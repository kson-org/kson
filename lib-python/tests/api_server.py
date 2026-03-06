"""HTTP server exposing the KSON Python API, conforming to kson-api-schema.json."""

import json
import sys
import traceback
from http.server import HTTPServer, BaseHTTPRequestHandler
from kson import (
    Kson, FormatOptions, IndentType, FormattingStyle, EmbedRule,
    TranspileOptions, Result, SchemaResult, KsonValue, KsonValueType,
    MessageSeverity,
)


def _serialize_position(pos):
    return {"line": pos.line(), "column": pos.column()}


def _serialize_message(msg):
    return {
        "message": msg.message(),
        "severity": msg.severity().name,
        "start": _serialize_position(msg.start()),
        "end": _serialize_position(msg.end()),
    }


def _serialize_token(token):
    return {
        "tokenType": token.token_type().name,
        "text": token.text(),
        "start": _serialize_position(token.start()),
        "end": _serialize_position(token.end()),
    }


def _serialize_kson_value(value):
    if value is None:
        return None

    vtype = value.type()

    if vtype == KsonValueType.OBJECT:
        props = value.properties()
        prop_keys = value.property_keys()
        return {
            "type": "OBJECT",
            "properties": {k: _serialize_kson_value(v) for k, v in props.items()},
            "propertyKeys": {k: _serialize_kson_value(v) for k, v in prop_keys.items()},
            "start": _serialize_position(value.start()),
            "end": _serialize_position(value.end()),
        }
    elif vtype == KsonValueType.ARRAY:
        return {
            "type": "ARRAY",
            "elements": [_serialize_kson_value(e) for e in value.elements()],
            "start": _serialize_position(value.start()),
            "end": _serialize_position(value.end()),
        }
    elif vtype == KsonValueType.STRING:
        return {
            "type": "STRING",
            "value": value.value(),
            "start": _serialize_position(value.start()),
            "end": _serialize_position(value.end()),
        }
    elif vtype == KsonValueType.INTEGER:
        return {
            "type": "INTEGER",
            "value": value.value(),
            "start": _serialize_position(value.start()),
            "end": _serialize_position(value.end()),
        }
    elif vtype == KsonValueType.DECIMAL:
        return {
            "type": "DECIMAL",
            "value": value.value(),
            "start": _serialize_position(value.start()),
            "end": _serialize_position(value.end()),
        }
    elif vtype == KsonValueType.BOOLEAN:
        return {
            "type": "BOOLEAN",
            "value": bool(value.value()),
            "start": _serialize_position(value.start()),
            "end": _serialize_position(value.end()),
        }
    elif vtype == KsonValueType.NULL:
        return {
            "type": "NULL",
            "start": _serialize_position(value.start()),
            "end": _serialize_position(value.end()),
        }
    elif vtype == KsonValueType.EMBED:
        return {
            "type": "EMBED",
            "tag": value.tag(),
            "content": value.content(),
            "start": _serialize_position(value.start()),
            "end": _serialize_position(value.end()),
        }


def _parse_indent_type(obj):
    if obj is None:
        return IndentType.Spaces(2)
    if obj["type"] == "tabs":
        return IndentType.Tabs()
    return IndentType.Spaces(obj.get("size", 2))


def _parse_formatting_style(name):
    if name is None:
        return FormattingStyle.PLAIN
    return FormattingStyle[name]


def _parse_embed_rule(obj):
    return EmbedRule(obj["pathPattern"], obj.get("tag"))


def _parse_format_options(obj):
    if obj is None:
        return FormatOptions(IndentType.Spaces(2), FormattingStyle.PLAIN, [])
    indent = _parse_indent_type(obj.get("indentType"))
    style = _parse_formatting_style(obj.get("formattingStyle"))
    rules = [_parse_embed_rule(r) for r in obj.get("embedBlockRules", [])]
    return FormatOptions(indent, style, rules)


def handle_format(req):
    options = _parse_format_options(req.get("formatOptions"))
    output = Kson.format(req["kson"], options)
    return {"command": "format", "success": True, "output": output}


def handle_to_json(req):
    retain = req.get("retainEmbedTags", True)
    result = Kson.to_json(req["kson"], TranspileOptions.Json(retain_embed_tags=retain))
    if isinstance(result, Result.Success):
        return {"command": "toJson", "success": True, "output": result.output()}
    else:
        return {
            "command": "toJson",
            "success": False,
            "errors": [_serialize_message(m) for m in result.errors()],
        }


def handle_to_yaml(req):
    retain = req.get("retainEmbedTags", True)
    result = Kson.to_yaml(req["kson"], TranspileOptions.Yaml(retain_embed_tags=retain))
    if isinstance(result, Result.Success):
        return {"command": "toYaml", "success": True, "output": result.output()}
    else:
        return {
            "command": "toYaml",
            "success": False,
            "errors": [_serialize_message(m) for m in result.errors()],
        }


def handle_analyze(req):
    filepath = req.get("filepath")
    analysis = Kson.analyze(req["kson"], filepath)
    return {
        "command": "analyze",
        "errors": [_serialize_message(m) for m in analysis.errors()],
        "tokens": [_serialize_token(t) for t in analysis.tokens()],
        "ksonValue": _serialize_kson_value(analysis.kson_value()),
    }


def handle_parse_schema(req):
    result = Kson.parse_schema(req["schemaKson"])
    if isinstance(result, SchemaResult.Success):
        return {"command": "parseSchema", "success": True}
    else:
        return {
            "command": "parseSchema",
            "success": False,
            "errors": [_serialize_message(m) for m in result.errors()],
        }


def handle_validate(req):
    schema_result = Kson.parse_schema(req["schemaKson"])
    if isinstance(schema_result, SchemaResult.Failure):
        return {
            "command": "validate",
            "success": False,
            "errors": [_serialize_message(m) for m in schema_result.errors()],
        }

    validator = schema_result.schema_validator()
    errors = validator.validate(req["kson"], req.get("filepath"))
    return {
        "command": "validate",
        "success": len(errors) == 0,
        "errors": [_serialize_message(m) for m in errors],
    }


HANDLERS = {
    "format": handle_format,
    "toJson": handle_to_json,
    "toYaml": handle_to_yaml,
    "analyze": handle_analyze,
    "parseSchema": handle_parse_schema,
    "validate": handle_validate,
}


class KsonHandler(BaseHTTPRequestHandler):
    def do_POST(self):
        length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(length)

        try:
            req = json.loads(body)
        except json.JSONDecodeError as e:
            self._send_error(400, f"Invalid JSON: {e}")
            return

        command = req.get("command")
        handler = HANDLERS.get(command)
        if handler is None:
            self._send_error(400, f"Unknown command: {command}")
            return

        try:
            response = handler(req)
        except Exception as e:
            stack_trace = traceback.format_exc()
            self._send_error(500, stack_trace)
            return

        self._send_json(200, response)

    def _send_json(self, status, obj):
        data = json.dumps(obj).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def _send_error(self, status, message):
        self._send_json(status, {"internal_error": message})

    def log_message(self, format, *args):
        # Suppress default stderr logging
        pass


def main():
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8080
    server = HTTPServer(("127.0.0.1", port), KsonHandler)
    print(f"Listening on http://127.0.0.1:{port}", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    except BaseException as e:
        print(f"Exception! {e}")
    print("Shutting down...")
    server.server_close()


if __name__ == "__main__":
    main()
