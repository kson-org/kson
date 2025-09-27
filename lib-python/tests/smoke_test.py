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
    result = Kson.to_json(kson_with_embed, retain_embed_tags=False)
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
    result = Kson.to_yaml(kson_with_embed, retain_embed_tags=False)
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
    assert isinstance(value, KsonValueObject)

    properties = value.properties()
    assert len(properties) == 3

    mapped_properties = {key.value(): val for key, val in properties.items()}

    # Check root object location (should span entire document)
    assert value.start().line() == 0
    assert value.start().column() == 0
    assert value.end().line() == 6
    assert value.end().column() == 2

    # Check "key" property
    key_value = mapped_properties["key"]
    assert isinstance(key_value, KsonValueString)
    assert key_value.value() == "value"
    assert key_value.start().line() == 0
    assert key_value.start().column() == 5
    assert key_value.end().line() == 0
    assert key_value.end().column() == 10

    # Check "list" property
    list_value = mapped_properties["list"]
    assert isinstance(list_value, KsonValueArray)
    elements = list_value.elements()
    assert len(elements) == 3
    assert list_value.start().line() == 2
    assert list_value.start().column() == 2
    assert list_value.end().line() == 4
    assert list_value.end().column() == 7

    # Check list elements
    first_element = elements[0]
    assert isinstance(first_element, KsonValueInteger)
    assert first_element.value() == 1
    assert first_element.start().line() == 2
    assert first_element.start().column() == 4
    assert first_element.end().line() == 2
    assert first_element.end().column() == 5

    second_element = elements[1]
    assert isinstance(second_element, KsonValueDecimal)
    assert second_element.value() == 2.1
    assert second_element.start().line() == 3
    assert second_element.start().column() == 4
    assert second_element.end().line() == 3
    assert second_element.end().column() == 7

    third_element = elements[2]
    assert isinstance(third_element, KsonValueDecimal)
    assert third_element.value() == 3e5
    assert third_element.start().line() == 4
    assert third_element.start().column() == 4
    assert third_element.end().line() == 4
    assert third_element.end().column() == 7

    # Check "embed" property
    embed_value = mapped_properties["embed"]
    assert isinstance(embed_value, KsonValueEmbed)
    assert embed_value.tag() == "tag"
    assert embed_value.content() == ""
    assert embed_value.start().line() == 5
    assert embed_value.start().column() == 6
    assert embed_value.end().line() == 6
    assert embed_value.end().column() == 2
