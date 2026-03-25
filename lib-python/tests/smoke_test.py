import pytest
import typing
from kson import *


NoneAny = typing.cast(Any, None)


def messages_to_string(msgs):
    output = ""
    for msg in msgs:
        p1 = msg.start()
        p2 = msg.end()
        line = (
            f"{p1.line()},{p1.column()} to {p2.line()},{p2.column()} - {msg.message()}"
        )
        output += line.strip()
        output += "\n"
    return output


def test_none_argument():
    with pytest.raises(ValueError):
        IndentType.Spaces(None)


def test_kson_format():
    indent = IndentType.Spaces(2)
    formatting = FormattingStyle.PLAIN
    result = Kson.format(
        "key: [1, 2, 3, 4]",
        FormatOptions(indent, formatting, []),
    )

    assert (
        result
        == """key:
  - 1
  - 2
  - 3
  - 4"""
    )


def test_kson_formatting_classic():
    indent = IndentType.Spaces(2)
    formatting = FormattingStyle.CLASSIC
    result = Kson.format(
        "key: [1, 2, 3, 4]",
        FormatOptions(indent, formatting, []),
    )

    assert (
        result
        == """{
  "key": [
    1,
    2,
    3,
    4
  ]
}"""
    )


def test_formatting_style_delimited():
    indent = IndentType.Spaces(2)
    result = Kson.format(
        "key: [1, 2]",
        FormatOptions(indent, FormattingStyle.DELIMITED, []),
    )
    assert (
        result
        == """{
  key: <
    - 1
    - 2
  >
}"""
    )


def test_formatting_style_compact():
    indent = IndentType.Spaces(2)
    result = Kson.format(
        "key: [1, 2]",
        FormatOptions(indent, FormattingStyle.COMPACT, []),
    )
    assert result == "key:[1 2]"


def test_kson_to_json_success():
    result = Kson.to_json(
        "key: [1, 2, 3, 4]", TranspileOptions.Json(retain_embed_tags=True)
    )
    assert isinstance(result, Result.Success)
    assert (
        result.output()
        == """{
  "key": [
    1,
    2,
    3,
    4
  ]
}"""
    )


def test_kson_to_json_with_embed_tags():
    kson_with_embed = """key: $embed
This is embedded content
embed$$"""

    # Test with default retain_embed_tags=True
    result = Kson.to_json(
        kson_with_embed, TranspileOptions.Json(retain_embed_tags=True)
    )
    assert isinstance(result, Result.Success)
    assert (
        result.output()
        == """{
  "key": {
    "embedTag": "embed",
    "embedContent": "This is embedded content\\nembed"
  }
}"""
    )
    # The JSON output should contain the embed tag information

    # Test with retain_embed_tags=False
    result = Kson.to_json(
        kson_with_embed, TranspileOptions.Json(retain_embed_tags=False)
    )
    assert isinstance(result, Result.Success)
    assert (
        result.output()
        == """{
  "key": "This is embedded content\\nembed"
}"""
    )


def test_kson_to_json_failure():
    result = Kson.to_json(
        "key: [1, 2, 3, 4", TranspileOptions.Json(retain_embed_tags=True)
    )
    assert isinstance(result, Result.Failure)
    output = messages_to_string(result.errors())
    assert output == "0,5 to 0,16 - Unclosed list\n"


def test_kson_to_yaml_success():
    result = Kson.to_yaml(
        "key: [1, 2, 3, 4]", TranspileOptions.Yaml(retain_embed_tags=True)
    )
    assert isinstance(result, Result.Success)
    assert (
        result.output()
        == """key:
  - 1
  - 2
  - 3
  - 4"""
    )


def test_kson_to_yaml_with_embed_tags():
    kson_with_embed = """key: $embed
This is embedded content
embed$$"""

    # Test with default retain_embed_tags=True
    result = Kson.to_yaml(
        kson_with_embed, TranspileOptions.Yaml(retain_embed_tags=True)
    )
    assert isinstance(result, Result.Success)
    assert (
        result.output()
        == """key:
  embedTag: "embed"
  embedContent: |
    This is embedded content
    embed"""
    )

    # Test with retain_embed_tags=False
    result = Kson.to_yaml(
        kson_with_embed, TranspileOptions.Yaml(retain_embed_tags=False)
    )
    assert isinstance(result, Result.Success)
    assert (
        result.output()
        == """key: |
    This is embedded content
    embed"""
    )


def test_kson_to_yaml_failure():
    result = Kson.to_yaml(
        "key: [1, 2, 3, 4", TranspileOptions.Yaml(retain_embed_tags=True)
    )
    assert isinstance(result, Result.Failure)
    output = messages_to_string(result.errors())
    assert output == "0,5 to 0,16 - Unclosed list\n"


def test_kson_analysis():
    analysis = Kson.analyze("key: [1, 2, 3, 4]", None)
    assert analysis.errors() == []

    # Transform tokens to strings, so we can snapshot them
    output = ""
    for token in analysis.tokens():
        p1 = token.start()
        p2 = token.end()
        line = f"{p1.line()},{p1.column()} to {p2.line()},{p2.column()} - {token.token_type().name}: {token.text()}"
        output += line.strip()
        output += "\n"

    assert (
        output
        == """0,0 to 0,3 - UNQUOTED_STRING: key
0,3 to 0,4 - COLON: :
0,5 to 0,6 - SQUARE_BRACKET_L: [
0,6 to 0,7 - NUMBER: 1
0,7 to 0,8 - COMMA: ,
0,9 to 0,10 - NUMBER: 2
0,10 to 0,11 - COMMA: ,
0,12 to 0,13 - NUMBER: 3
0,13 to 0,14 - COMMA: ,
0,15 to 0,16 - NUMBER: 4
0,16 to 0,17 - SQUARE_BRACKET_R: ]
0,17 to 0,17 - EOF:
"""
    )


def test_kson_value():
    input = """key: value
list:
  - 1
  - 2.1
  - 3E5
embed:%tag
%%"""
    analysis = Kson.analyze(input, None)
    value = analysis.kson_value()
    assert value is not None
    assert isinstance(value, KsonValue.KsonObject)

    properties = value.properties()
    assert len(properties) == 3

    # Check root object location (should span entire document)
    assert value.start().line() == 0
    assert value.start().column() == 0
    assert value.end().line() == 6
    assert value.end().column() == 2

    # Check "key" property
    key_value = properties["key"]
    assert isinstance(key_value, KsonValue.KsonString)
    assert key_value.value() == "value"
    assert key_value.start().line() == 0
    assert key_value.start().column() == 5
    assert key_value.end().line() == 0
    assert key_value.end().column() == 10

    # Check "list" property
    list_value = properties["list"]
    assert isinstance(list_value, KsonValue.KsonArray)
    elements = list_value.elements()
    assert len(elements) == 3
    assert list_value.start().line() == 2
    assert list_value.start().column() == 2
    assert list_value.end().line() == 4
    assert list_value.end().column() == 7

    # Check list elements
    first_element = elements[0]
    assert isinstance(first_element, KsonValue.KsonNumber.Integer)
    assert first_element.value() == 1
    assert first_element.start().line() == 2
    assert first_element.start().column() == 4
    assert first_element.end().line() == 2
    assert first_element.end().column() == 5

    second_element = elements[1]
    assert isinstance(second_element, KsonValue.KsonNumber.Decimal)
    assert second_element.value() == 2.1
    assert second_element.start().line() == 3
    assert second_element.start().column() == 4
    assert second_element.end().line() == 3
    assert second_element.end().column() == 7

    third_element = elements[2]
    assert isinstance(third_element, KsonValue.KsonNumber.Decimal)
    assert third_element.value() == 3e5
    assert third_element.start().line() == 4
    assert third_element.start().column() == 4
    assert third_element.end().line() == 4
    assert third_element.end().column() == 7

    # Check "embed" property
    embed_value = properties["embed"]
    assert isinstance(embed_value, KsonValue.KsonEmbed)
    assert embed_value.tag() == "tag"
    assert embed_value.content() == ""
    assert embed_value.start().line() == 5
    assert embed_value.start().column() == 6
    assert embed_value.end().line() == 6
    assert embed_value.end().column() == 2


def test_property_keys_basic_access():
    input = """name: John
age: 30
city: 'New York'"""
    analysis = Kson.analyze(input, None)
    value = analysis.kson_value()
    assert value is not None
    assert isinstance(value, KsonValue.KsonObject)

    # Verify all keys are present in property_keys
    property_keys = value.property_keys()
    assert len(property_keys) == 3
    assert "name" in property_keys
    assert "age" in property_keys
    assert "city" in property_keys

    # Verify property_keys contains KsonString values
    name_key = property_keys["name"]
    assert isinstance(name_key, KsonValue.KsonString)
    assert name_key.value() == "name"

    age_key = property_keys["age"]
    assert isinstance(age_key, KsonValue.KsonString)
    assert age_key.value() == "age"


def test_property_keys_with_position_information():
    input = """name: John
age: 30"""
    analysis = Kson.analyze(input, None)
    value = analysis.kson_value()
    assert value is not None
    assert isinstance(value, KsonValue.KsonObject)

    # Verify position information for keys
    property_keys = value.property_keys()
    name_key = property_keys["name"]
    assert name_key.start().line() == 0
    assert name_key.start().column() == 0
    assert name_key.end().line() == 0
    assert name_key.end().column() == 4

    age_key = property_keys["age"]
    assert age_key.start().line() == 1
    assert age_key.start().column() == 0
    assert age_key.end().line() == 1
    assert age_key.end().column() == 3


def test_property_keys_empty_object():
    input = "{}"
    analysis = Kson.analyze(input, None)
    value = analysis.kson_value()
    assert value is not None
    assert isinstance(value, KsonValue.KsonObject)

    # Empty object should have no property_keys
    assert len(value.property_keys()) == 0
    assert len(value.properties()) == 0


def test_kson_value_boolean_and_null():
    input = """flag: true
other: false
nothing: null"""
    analysis = Kson.analyze(input, None)
    value = analysis.kson_value()
    assert isinstance(value, KsonValue.KsonObject)

    props = value.properties()

    flag_value = props["flag"]
    assert isinstance(flag_value, KsonValue.KsonBoolean)
    assert flag_value.value() is True
    assert flag_value.type() == KsonValueType.BOOLEAN

    other_value = props["other"]
    assert isinstance(other_value, KsonValue.KsonBoolean)
    assert other_value.value() is False

    null_value = props["nothing"]
    assert isinstance(null_value, KsonValue.KsonNull)
    assert null_value.type() == KsonValueType.NULL


def test_kson_value_type_discriminator():
    input = """str: hello
num: 42
dec: 3.14
list: [1]
obj: {a: b}"""
    analysis = Kson.analyze(input, None)
    value = analysis.kson_value()
    assert isinstance(value, KsonValue.KsonObject)
    props = value.properties()

    assert props["str"].type() == KsonValueType.STRING
    assert props["num"].type() == KsonValueType.INTEGER
    assert props["dec"].type() == KsonValueType.DECIMAL
    assert props["list"].type() == KsonValueType.ARRAY
    assert props["obj"].type() == KsonValueType.OBJECT
    assert value.type() == KsonValueType.OBJECT


def test_kson_value_embed_type():
    input = """key: $embed
content here
embed$$"""
    analysis = Kson.analyze(input, None)
    value = analysis.kson_value()
    assert isinstance(value, KsonValue.KsonObject)
    props = value.properties()
    embed = props["key"]
    assert isinstance(embed, KsonValue.KsonEmbed)
    assert embed.type() == KsonValueType.EMBED


def test_message_severity():
    result = Kson.to_json(
        "key: [1, 2, 3, 4", TranspileOptions.Json(retain_embed_tags=True)
    )
    assert isinstance(result, Result.Failure)
    errors = result.errors()
    assert len(errors) > 0
    assert errors[0].severity() == MessageSeverity.ERROR


def test_format_options_getters():
    indent = IndentType.Spaces(2)
    formatting = FormattingStyle.PLAIN
    opts = FormatOptions(indent, formatting, [])

    retrieved_indent = opts.indent_type()
    assert isinstance(retrieved_indent, IndentType.Spaces)

    retrieved_formatting = opts.formatting_style()
    assert retrieved_formatting == FormattingStyle.PLAIN

    retrieved_rules = opts.embed_block_rules()
    assert retrieved_rules == []


def test_format_options_none_checks():
    with pytest.raises(ValueError):
        FormatOptions(NoneAny, FormattingStyle.PLAIN, [])
    with pytest.raises(ValueError):
        FormatOptions(IndentType.Spaces(2), NoneAny, [])
    with pytest.raises(ValueError):
        FormatOptions(IndentType.Spaces(2), FormattingStyle.PLAIN, NoneAny)


def test_indent_type_tabs():
    tabs = IndentType.Tabs()
    result = Kson.format(
        "key: [1, 2]",
        FormatOptions(tabs, FormattingStyle.PLAIN, []),
    )
    assert "\t" in result


def test_indent_type_spaces_size():
    spaces = IndentType.Spaces(4)
    assert spaces.size() == 4


def test_transpile_options_retain_embed_tags_getter():
    json_opts = TranspileOptions.Json(retain_embed_tags=True)
    assert json_opts.retain_embed_tags() is True

    yaml_opts = TranspileOptions.Yaml(retain_embed_tags=False)
    assert yaml_opts.retain_embed_tags() is False


def test_parse_schema_success():
    schema_kson = """type: object
properties:
  name:
    type: string"""
    result = Kson.parse_schema(schema_kson)
    assert isinstance(result, SchemaResult.Success)

    validator = result.schema_validator()
    assert validator is not None

    # Validate a valid document
    errors = validator.validate("name: hello", None)
    assert errors == []


def test_parse_schema_validation_errors():
    schema_kson = """type: object
properties:
  name:
    type: string
    .
  .
required: [name]"""
    result = Kson.parse_schema(schema_kson)
    assert isinstance(result, SchemaResult.Success)

    validator = result.schema_validator()
    # Validate with missing required field
    errors = validator.validate("{}", None)
    assert len(errors) > 0


def test_parse_schema_failure():
    result = Kson.parse_schema("type: [invalid")
    assert isinstance(result, SchemaResult.Failure)
    errors = result.errors()
    assert len(errors) > 0


def test_embed_rule_from_path_pattern_success():
    result = EmbedRule.from_path_pattern("/scripts/*", "bash", 0)
    assert isinstance(result, EmbedRuleResult.Success)

    rule = result.embed_rule()
    assert rule.path_pattern() == "/scripts/*"
    assert rule.tag() == "bash"
    assert rule.min_length() == 0


def test_embed_rule_from_path_pattern_no_tag():
    result = EmbedRule.from_path_pattern("/config/description", None, 40)
    assert isinstance(result, EmbedRuleResult.Success)

    rule = result.embed_rule()
    assert rule.tag() is None
    assert rule.min_length() == 40


def test_embed_rule_from_path_pattern_failure():
    result = EmbedRule.from_path_pattern("invalid pattern [", None, 0)
    assert isinstance(result, EmbedRuleResult.Failure)
    assert len(result.message()) > 0


def test_format_with_embed_rules():
    rule_result = EmbedRule.from_path_pattern("/key", "txt", 0)
    assert isinstance(rule_result, EmbedRuleResult.Success)
    rule = rule_result.embed_rule()

    indent = IndentType.Spaces(2)
    result = Kson.format(
        'key: "hello world"',
        FormatOptions(indent, FormattingStyle.PLAIN, [rule]),
    )
    assert result is not None


def test_kson_format_none_checks():
    indent = IndentType.Spaces(2)
    formatting = FormattingStyle.PLAIN
    opts = FormatOptions(indent, formatting, [])
    with pytest.raises(ValueError):
        Kson.format(NoneAny, opts)
    with pytest.raises(ValueError):
        Kson.format("key: value", NoneAny)


def test_kson_to_json_none_checks():
    with pytest.raises(ValueError):
        Kson.to_json(NoneAny, TranspileOptions.Json(retain_embed_tags=True))
    with pytest.raises(ValueError):
        Kson.to_json("key: value", NoneAny)


def test_kson_to_yaml_none_checks():
    with pytest.raises(ValueError):
        Kson.to_yaml(NoneAny, TranspileOptions.Yaml(retain_embed_tags=True))
    with pytest.raises(ValueError):
        Kson.to_yaml("key: value", NoneAny)


def test_kson_analyze_none_check():
    with pytest.raises(ValueError):
        Kson.analyze(NoneAny, NoneAny)


def test_kson_parse_schema_none_check():
    with pytest.raises(ValueError):
        Kson.parse_schema(NoneAny)


def test_indent_type_parent():
    with pytest.raises(TypeError):
        value = IndentType()


def test_indent_type_downcast_via_format_options():
    tabs = IndentType.Tabs()
    opts = FormatOptions(tabs, FormattingStyle.PLAIN, [])
    retrieved = opts.indent_type()
    assert isinstance(retrieved, IndentType.Tabs)


def test_indent_type_tabs_eq_and_hash():
    tabs1 = IndentType.Tabs()
    tabs2 = IndentType.Tabs()
    assert tabs1 == tabs2
    assert hash(tabs1) == hash(tabs2)


def test_formatting_style_from_kotlin_enum():
    opts = FormatOptions(IndentType.Spaces(2), FormattingStyle.CLASSIC, [])
    assert opts.formatting_style() == FormattingStyle.CLASSIC

    opts2 = FormatOptions(IndentType.Spaces(2), FormattingStyle.DELIMITED, [])
    assert opts2.formatting_style() == FormattingStyle.DELIMITED

    opts3 = FormatOptions(IndentType.Spaces(2), FormattingStyle.COMPACT, [])
    assert opts3.formatting_style() == FormattingStyle.COMPACT


def test_number_internal_start_end():
    input = "val: 42"
    analysis = Kson.analyze(input, None)
    value = analysis.kson_value()
    assert isinstance(value, KsonValue.KsonObject)
    props = value.properties()
    num = props["val"]
    assert isinstance(num, KsonValue.KsonNumber.Integer)
    istart = num.internal_start()
    iend = num.internal_end()
    assert istart.line() == 0
    assert iend.line() == 0


def test_result_success_direct_construction():
    success = Result.Success("hello world")
    assert success.output() == "hello world"


def test_result_failure_direct_construction():
    # Get a real Message object from a parse failure
    result = Kson.to_json("key: [1, 2", TranspileOptions.Json(retain_embed_tags=True))
    assert isinstance(result, Result.Failure)
    errors = result.errors()

    # Reconstruct a Failure from those errors
    failure = Result.Failure(errors)
    assert len(failure.errors()) > 0


def test_schema_result_success_direct_construction():
    # Get a real SchemaValidator from a successful parse
    result = Kson.parse_schema("type: object")
    assert isinstance(result, SchemaResult.Success)
    validator = result.schema_validator()

    # Reconstruct a Success from that validator
    success = SchemaResult.Success(validator)
    assert success.schema_validator() is not None


def test_schema_result_failure_direct_construction():
    # Get real Message objects from a parse failure
    result = Kson.to_json("key: [1, 2", TranspileOptions.Json(retain_embed_tags=True))
    assert isinstance(result, Result.Failure)
    errors = result.errors()

    # Construct a SchemaResult.Failure from those errors
    failure = SchemaResult.Failure(errors)
    assert len(failure.errors()) > 0


def test_embed_rule_result_success_direct_construction():
    # Get a real EmbedRule
    rule_result = EmbedRule.from_path_pattern("/key", "txt", 0)
    assert isinstance(rule_result, EmbedRuleResult.Success)
    rule = rule_result.embed_rule()

    # Reconstruct a Success from that rule
    success = EmbedRuleResult.Success(rule)
    assert success.embed_rule().path_pattern() == "/key"


def test_embed_rule_result_failure_direct_construction():
    failure = EmbedRuleResult.Failure("something went wrong")
    assert failure.message() == "something went wrong"
