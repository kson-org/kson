use axum::{Router, routing::post, Json};
use serde::Deserialize;
use serde_json::Value;

use kson::{
    EmbedRule, FormatOptions, FormattingStyle, IndentType, Kson, KsonValue,
    indent_type, kson_value, transpile_options,
};

// --- Request types ---

#[derive(Deserialize)]
#[serde(tag = "command")]
enum Request {
    #[serde(rename = "format")]
    Format {
        kson: String,
        #[serde(rename = "formatOptions")]
        format_options: Option<FormatOptionsDto>,
    },
    #[serde(rename = "toJson")]
    ToJson {
        kson: String,
        #[serde(rename = "retainEmbedTags", default = "default_true")]
        retain_embed_tags: bool,
    },
    #[serde(rename = "toYaml")]
    ToYaml {
        kson: String,
        #[serde(rename = "retainEmbedTags", default = "default_true")]
        retain_embed_tags: bool,
    },
    #[serde(rename = "analyze")]
    Analyze {
        kson: String,
        filepath: Option<String>,
    },
    #[serde(rename = "parseSchema")]
    ParseSchema {
        #[serde(rename = "schemaKson")]
        schema_kson: String,
    },
    #[serde(rename = "validate")]
    Validate {
        #[serde(rename = "schemaKson")]
        schema_kson: String,
        kson: String,
        filepath: Option<String>,
    },
}

fn default_true() -> bool {
    true
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct FormatOptionsDto {
    indent_type: Option<IndentTypeDto>,
    formatting_style: Option<String>,
    embed_block_rules: Option<Vec<EmbedRuleDto>>,
}

#[derive(Deserialize)]
struct IndentTypeDto {
    r#type: String,
    size: Option<i32>,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct EmbedRuleDto {
    path_pattern: String,
    tag: Option<String>,
}

// --- Response serialization helpers ---

fn serialize_position(pos: kson::Position) -> Value {
    serde_json::json!({
        "line": pos.line(),
        "column": pos.column(),
    })
}

fn serialize_message(msg: kson::Message) -> Value {
    let severity = match msg.severity() {
        kson::MessageSeverity::Error => "ERROR",
        kson::MessageSeverity::Warning => "WARNING",
    };
    serde_json::json!({
        "message": msg.message(),
        "severity": severity,
        "start": serialize_position(msg.start()),
        "end": serialize_position(msg.end()),
    })
}

fn serialize_token(token: kson::Token) -> Value {
    let token_type = match token.token_type() {
        kson::TokenType::CurlyBraceL => "CURLY_BRACE_L",
        kson::TokenType::CurlyBraceR => "CURLY_BRACE_R",
        kson::TokenType::SquareBracketL => "SQUARE_BRACKET_L",
        kson::TokenType::SquareBracketR => "SQUARE_BRACKET_R",
        kson::TokenType::AngleBracketL => "ANGLE_BRACKET_L",
        kson::TokenType::AngleBracketR => "ANGLE_BRACKET_R",
        kson::TokenType::Colon => "COLON",
        kson::TokenType::Dot => "DOT",
        kson::TokenType::EndDash => "END_DASH",
        kson::TokenType::Comma => "COMMA",
        kson::TokenType::Comment => "COMMENT",
        kson::TokenType::EmbedOpenDelim => "EMBED_OPEN_DELIM",
        kson::TokenType::EmbedCloseDelim => "EMBED_CLOSE_DELIM",
        kson::TokenType::EmbedTag => "EMBED_TAG",
        kson::TokenType::EmbedPreambleNewline => "EMBED_PREAMBLE_NEWLINE",
        kson::TokenType::EmbedContent => "EMBED_CONTENT",
        kson::TokenType::False => "FALSE",
        kson::TokenType::UnquotedString => "UNQUOTED_STRING",
        kson::TokenType::IllegalChar => "ILLEGAL_CHAR",
        kson::TokenType::ListDash => "LIST_DASH",
        kson::TokenType::Null => "NULL",
        kson::TokenType::Number => "NUMBER",
        kson::TokenType::StringOpenQuote => "STRING_OPEN_QUOTE",
        kson::TokenType::StringCloseQuote => "STRING_CLOSE_QUOTE",
        kson::TokenType::StringContent => "STRING_CONTENT",
        kson::TokenType::True => "TRUE",
        kson::TokenType::Whitespace => "WHITESPACE",
        kson::TokenType::Eof => "EOF",
    };
    serde_json::json!({
        "tokenType": token_type,
        "text": token.text(),
        "start": serialize_position(token.start()),
        "end": serialize_position(token.end()),
    })
}

fn serialize_kson_value(value: &KsonValue) -> Value {
    match value {
        KsonValue::KsonObject(obj) => {
            let props = obj.properties();
            let property_keys = obj.property_keys();
            let mut serialized_props: serde_json::Map<String, Value> = serde_json::Map::new();
            for (key, val) in &props {
                serialized_props.insert(key.clone(), serialize_kson_value(val));
            }
            let mut serialized_keys: serde_json::Map<String, Value> = serde_json::Map::new();
            for (key, kson_str) in &property_keys {
                serialized_keys.insert(key.clone(), serialize_kson_string(kson_str));
            }
            serde_json::json!({
                "type": "OBJECT",
                "properties": serialized_props,
                "propertyKeys": serialized_keys,
                "start": serialize_position(obj.start()),
                "end": serialize_position(obj.end()),
            })
        }
        KsonValue::KsonArray(arr) => {
            let elements: Vec<Value> = arr.elements().iter().map(serialize_kson_value).collect();
            serde_json::json!({
                "type": "ARRAY",
                "elements": elements,
                "start": serialize_position(arr.start()),
                "end": serialize_position(arr.end()),
            })
        }
        KsonValue::KsonString(s) => serialize_kson_string(s),
        KsonValue::KsonNumber(num) => match num {
            kson_value::KsonNumber::Integer(i) => {
                serde_json::json!({
                    "type": "INTEGER",
                    "value": i.value(),
                    "start": serialize_position(i.start()),
                    "end": serialize_position(i.end()),
                })
            }
            kson_value::KsonNumber::Decimal(d) => {
                serde_json::json!({
                    "type": "DECIMAL",
                    "value": d.value(),
                    "start": serialize_position(d.start()),
                    "end": serialize_position(d.end()),
                })
            }
        },
        KsonValue::KsonBoolean(b) => {
            serde_json::json!({
                "type": "BOOLEAN",
                "value": b.value(),
                "start": serialize_position(b.start()),
                "end": serialize_position(b.end()),
            })
        }
        KsonValue::KsonNull(n) => {
            serde_json::json!({
                "type": "NULL",
                "start": serialize_position(n.start()),
                "end": serialize_position(n.end()),
            })
        }
        KsonValue::KsonEmbed(e) => {
            serde_json::json!({
                "type": "EMBED",
                "tag": e.tag(),
                "content": e.content(),
                "start": serialize_position(e.start()),
                "end": serialize_position(e.end()),
            })
        }
    }
}

fn serialize_kson_string(s: &kson_value::KsonString) -> Value {
    serde_json::json!({
        "type": "STRING",
        "value": s.value(),
        "start": serialize_position(s.start()),
        "end": serialize_position(s.end()),
    })
}

// --- Request handling ---

fn convert_indent_type(dto: &IndentTypeDto) -> IndentType {
    match dto.r#type.as_str() {
        "tabs" => IndentType::Tabs(indent_type::Tabs::new()),
        _ => IndentType::Spaces(indent_type::Spaces::new(dto.size.unwrap_or(2))),
    }
}

fn convert_formatting_style(s: &str) -> FormattingStyle {
    match s {
        "DELIMITED" => FormattingStyle::Delimited,
        "COMPACT" => FormattingStyle::Compact,
        "CLASSIC" => FormattingStyle::Classic,
        _ => FormattingStyle::Plain,
    }
}

fn convert_format_options(dto: Option<&FormatOptionsDto>) -> FormatOptions {
    let indent_type = dto
        .and_then(|d| d.indent_type.as_ref())
        .map(convert_indent_type)
        .unwrap_or_else(|| IndentType::Spaces(indent_type::Spaces::new(2)));

    let formatting_style = dto
        .and_then(|d| d.formatting_style.as_deref())
        .map(convert_formatting_style)
        .unwrap_or(FormattingStyle::Plain);

    let embed_rules: Vec<EmbedRule> = dto
        .and_then(|d| d.embed_block_rules.as_ref())
        .map(|rules| {
            rules
                .iter()
                .map(|r| EmbedRule::new(&r.path_pattern, r.tag.as_deref()))
                .collect()
        })
        .unwrap_or_default();

    FormatOptions::new(indent_type, formatting_style, &embed_rules)
}

fn handle_request(request: Request) -> Value {
    match request {
        Request::Format {
            kson,
            format_options,
        } => {
            let opts = convert_format_options(format_options.as_ref());
            let output = Kson::format(&kson, opts);
            serde_json::json!({
                "command": "format",
                "success": true,
                "output": output,
            })
        }
        Request::ToJson {
            kson,
            retain_embed_tags,
        } => {
            let opts = transpile_options::Json::new(retain_embed_tags);
            match Kson::to_json(&kson, opts) {
                Ok(success) => serde_json::json!({
                    "command": "toJson",
                    "success": true,
                    "output": success.output(),
                }),
                Err(failure) => {
                    let errors: Vec<Value> =
                        failure.errors().into_iter().map(serialize_message).collect();
                    serde_json::json!({
                        "command": "toJson",
                        "success": false,
                        "errors": errors,
                    })
                }
            }
        }
        Request::ToYaml {
            kson,
            retain_embed_tags,
        } => {
            let opts = transpile_options::Yaml::new(retain_embed_tags);
            match Kson::to_yaml(&kson, opts) {
                Ok(success) => serde_json::json!({
                    "command": "toYaml",
                    "success": true,
                    "output": success.output(),
                }),
                Err(failure) => {
                    let errors: Vec<Value> =
                        failure.errors().into_iter().map(serialize_message).collect();
                    serde_json::json!({
                        "command": "toYaml",
                        "success": false,
                        "errors": errors,
                    })
                }
            }
        }
        Request::Analyze { kson, filepath } => {
            let analysis = Kson::analyze(&kson, filepath.as_deref());
            let errors: Vec<Value> = analysis
                .errors()
                .into_iter()
                .map(serialize_message)
                .collect();
            let tokens: Vec<Value> = analysis
                .tokens()
                .into_iter()
                .map(serialize_token)
                .collect();
            let kson_value = analysis
                .kson_value()
                .as_ref()
                .map(serialize_kson_value);
            serde_json::json!({
                "command": "analyze",
                "errors": errors,
                "tokens": tokens,
                "ksonValue": kson_value,
            })
        }
        Request::ParseSchema { schema_kson } => match Kson::parse_schema(&schema_kson) {
            Ok(_) => serde_json::json!({
                "command": "parseSchema",
                "success": true,
            }),
            Err(failure) => {
                let errors: Vec<Value> =
                    failure.errors().into_iter().map(serialize_message).collect();
                serde_json::json!({
                    "command": "parseSchema",
                    "success": false,
                    "errors": errors,
                })
            }
        },
        Request::Validate {
            schema_kson,
            kson,
            filepath,
        } => match Kson::parse_schema(&schema_kson) {
            Ok(success) => {
                let validator = success.schema_validator();
                let errors: Vec<Value> = validator
                    .validate(&kson, filepath.as_deref())
                    .into_iter()
                    .map(serialize_message)
                    .collect();
                let is_success = errors.is_empty();
                serde_json::json!({
                    "command": "validate",
                    "success": is_success,
                    "errors": errors,
                })
            }
            Err(failure) => {
                let errors: Vec<Value> =
                    failure.errors().into_iter().map(serialize_message).collect();
                serde_json::json!({
                    "command": "validate",
                    "success": false,
                    "errors": errors,
                })
            }
        }
    }
}

async fn handler(Json(request): Json<Request>) -> Json<Value> {
    Json(handle_request(request))
}

#[tokio::main]
async fn main() {
    let port: u16 = std::env::args()
        .nth(1)
        .and_then(|s| s.parse().ok())
        .unwrap_or(3000);

    let app = Router::new().route("/", post(handler));
    let addr = std::net::SocketAddr::from(([127, 0, 0, 1], port));
    println!("Listening on {addr}");
    let listener = tokio::net::TcpListener::bind(addr).await.unwrap();
    axum::serve(listener, app).await.unwrap();
}
