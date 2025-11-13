from kson import *


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


def test_kson_format():
    indent = IndentType.Spaces(2)
    formatting = FormattingStyle.PLAIN
    result = Kson.format(
        "key: [1, 2, 3, 4]",
        FormatOptions(indent, formatting),
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
        FormatOptions(indent, formatting),
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


def test_kson_to_json_success():
    result = Kson.to_json("key: [1, 2, 3, 4]")
    assert isinstance(result, Success)
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
    result = Kson.to_json(kson_with_embed)
    assert isinstance(result, Success)
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
        kson_with_embed, TranspileOptionsJson(retain_embed_tags=False)
    )
    assert isinstance(result, Success)
    assert (
        result.output()
        == """{
  "key": "This is embedded content\\nembed"
}"""
    )


def test_kson_to_json_failure():
    result = Kson.to_json("key: [1, 2, 3, 4")
    assert isinstance(result, Failure)
    output = messages_to_string(result.errors())
    assert output == "0,5 to 0,16 - Unclosed list\n"


def test_kson_to_yaml_success():
    result = Kson.to_yaml("key: [1, 2, 3, 4]")
    assert isinstance(result, Success)
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
    result = Kson.to_yaml(kson_with_embed)
    assert isinstance(result, Success)
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
        kson_with_embed, TranspileOptionsYaml(retain_embed_tags=False)
    )
    assert isinstance(result, Success)
    assert (
        result.output()
        == """key: |
    This is embedded content
    embed"""
    )


def test_kson_to_yaml_failure():
    result = Kson.to_yaml("key: [1, 2, 3, 4")
    assert isinstance(result, Failure)
    output = messages_to_string(result.errors())
    assert output == "0,5 to 0,16 - Unclosed list\n"


def test_kson_analysis():
    analysis = Kson.analyze("key: [1, 2, 3, 4]")
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
    analysis = Kson.analyze(input)
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
    analysis = Kson.analyze(input)
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
    analysis = Kson.analyze(input)
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
    analysis = Kson.analyze(input)
    value = analysis.kson_value()
    assert value is not None
    assert isinstance(value, KsonValue.KsonObject)

    # Empty object should have no property_keys
    assert len(value.property_keys()) == 0
    assert len(value.properties()) == 0
