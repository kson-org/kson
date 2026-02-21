# kson-lang

Python bindings for [KSON](https://kson.org), a next-gen configuration language. Convert KSON to JSON/YAML, format and validate documents, and parse KSON into structured values.

Available on Linux, macOS, and Windows. Requires Python 3.10+.

## Installation

```bash
pip install kson-lang
```

## Quick start

```python
from kson import Kson, TranspileOptions, Result

result = Kson.to_json("key: [1, 2, 3, 4]", TranspileOptions.Json(retain_embed_tags=False))
assert isinstance(result, Result.Success)
print(result.output())
```

```json
{
  "key": [
    1,
    2,
    3,
    4
  ]
}
```

## Converting to JSON

Use `Kson.to_json()` to transpile KSON source into JSON. The result is either `Result.Success` or `Result.Failure`:

```python
from kson import Kson, TranspileOptions, Result

result = Kson.to_json(
    "name: Alice\nage: 30",
    TranspileOptions.Json(retain_embed_tags=False),
)

if isinstance(result, Result.Success):
    print(result.output())
elif isinstance(result, Result.Failure):
    for error in result.errors():
        print(f"Error: {error.message()}")
```

Set `retain_embed_tags=True` to preserve embed block metadata in the JSON output (see [Embed blocks](#embed-blocks)).

## Converting to YAML

Use `Kson.to_yaml()` to transpile KSON to YAML. Comments are preserved:

```python
from kson import Kson, TranspileOptions, Result

result = Kson.to_yaml(
    "# my config\nname: Alice\nage: 30",
    TranspileOptions.Yaml(retain_embed_tags=False),
)

if isinstance(result, Result.Success):
    print(result.output())
```

## Formatting

Use `Kson.format()` to reformat KSON source. Choose a formatting style and indentation:

```python
from kson import Kson, FormatOptions, IndentType, FormattingStyle

options = FormatOptions(
    indent_type=IndentType.Spaces(2),
    formatting_style=FormattingStyle.PLAIN,
    embed_block_rules=[],
)

formatted = Kson.format("key: [1, 2, 3, 4]", options)
print(formatted)
```

```
key:
  - 1
  - 2
  - 3
  - 4
```

### Formatting styles

| Style | Description |
|---|---|
| `FormattingStyle.PLAIN` | YAML-like format (default) |
| `FormattingStyle.CLASSIC` | Standard JSON format with braces and quotes |
| `FormattingStyle.DELIMITED` | JSON-like with explicit delimiters |
| `FormattingStyle.COMPACT` | Minified single-line output |

### Indentation

- `IndentType.Spaces(n)` — indent with `n` spaces
- `IndentType.Tabs()` — indent with tabs

## Parsing and analysis

Use `Kson.analyze()` to parse KSON into tokens and a structured value tree:

```python
from kson import Kson, KsonValue

analysis = Kson.analyze("name: Alice\nscores: [95, 87, 92]", None)

# Check for parse errors
for error in analysis.errors():
    print(f"{error.severity().name}: {error.message()}")

# Access the parsed value tree
value = analysis.kson_value()
if isinstance(value, KsonValue.KsonObject):
    props = value.properties()

    name = props["name"]
    if isinstance(name, KsonValue.KsonString):
        print(f"Name: {name.value()}")  # "Alice"

    scores = props["scores"]
    if isinstance(scores, KsonValue.KsonArray):
        for element in scores.elements():
            if isinstance(element, KsonValue.KsonNumber.Integer):
                print(f"Score: {element.value()}")
```

### Value types

All parsed values are subclasses of `KsonValue`:

| Type | Access |
|---|---|
| `KsonValue.KsonObject` | `.properties()` returns `dict[str, KsonValue]` |
| `KsonValue.KsonArray` | `.elements()` returns `list[KsonValue]` |
| `KsonValue.KsonString` | `.value()` returns `str` |
| `KsonValue.KsonNumber.Integer` | `.value()` returns `int` |
| `KsonValue.KsonNumber.Decimal` | `.value()` returns `float` |
| `KsonValue.KsonBoolean` | `.value()` returns `bool` |
| `KsonValue.KsonNull` | (no value) |
| `KsonValue.KsonEmbed` | `.tag()` returns `str` or `None`, `.content()` returns `str` |

Every value also has `.start()` and `.end()` returning a `Position` with `.line()` and `.column()` (both 0-based), useful for editor tooling and diagnostics.

### Token access

```python
analysis = Kson.analyze("key: value", None)
for token in analysis.tokens():
    print(f"{token.token_type().name}: {repr(token.text())}")
```

## Schema validation

Parse a JSON Schema definition and validate KSON documents against it:

```python
from kson import Kson, SchemaResult

schema_result = Kson.parse_schema("""
type: object
properties:
  name:
    type: string
  age:
    type: integer
required: [name, age]
""")

if isinstance(schema_result, SchemaResult.Success):
    validator = schema_result.schema_validator()

    errors = validator.validate("name: Alice\nage: 30", None)
    assert errors == []  # Valid

    errors = validator.validate("name: Alice\nage: not-a-number", None)
    for error in errors:
        print(f"Validation error: {error.message()}")
```

## Embed blocks

KSON supports [embed blocks](https://github.com/kson-org/kson) for embedding raw content like SQL, HTML, or other languages. When parsing:

```python
from kson import Kson, KsonValue

analysis = Kson.analyze("query: $sql\nSELECT * FROM users\n$$", None)
value = analysis.kson_value()

if isinstance(value, KsonValue.KsonObject):
    embed = value.properties()["query"]
    if isinstance(embed, KsonValue.KsonEmbed):
        print(f"Tag: {embed.tag()}")        # "sql"
        print(f"Content: {embed.content()}")  # "SELECT * FROM users"
```

When converting to JSON/YAML, use `retain_embed_tags=True` to keep embed metadata or `False` to flatten embed blocks to plain strings.

### Embed rules for formatting

Use embed rules to tell the formatter which string values should be rendered as embed blocks:

```python
from kson import Kson, FormatOptions, IndentType, FormattingStyle, EmbedRule, EmbedRuleResult

rule_result = EmbedRule.from_path_pattern("/scripts/*", "bash")
assert isinstance(rule_result, EmbedRuleResult.Success)

options = FormatOptions(
    indent_type=IndentType.Spaces(2),
    formatting_style=FormattingStyle.PLAIN,
    embed_block_rules=[rule_result.embed_rule()],
)

formatted = Kson.format(source, options)
```

## Error handling

All conversion methods (`to_json`, `to_yaml`) return a `Result`:

```python
result = Kson.to_json(source, TranspileOptions.Json(retain_embed_tags=False))

match result:
    case Result.Success():
        print(result.output())
    case Result.Failure():
        for error in result.errors():
            pos = error.start()
            print(f"  Line {pos.line()}, col {pos.column()}: {error.message()}")
```

`Kson.parse_schema()` returns a `SchemaResult` with the same pattern (`SchemaResult.Success` / `SchemaResult.Failure`).

## Build from source

```bash
git clone https://github.com/kson-org/kson.git
cd kson && ./gradlew :lib-python:build
pip install ./lib-python
```

## Links

- [KSON language documentation](https://github.com/kson-org/kson)
- [Issue tracker](https://github.com/kson-org/kson/issues)
