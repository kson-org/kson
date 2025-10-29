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
    assert isinstance(value, KsonValue.KsonObject)

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
    assert isinstance(key_value, KsonValue.KsonString)
    assert key_value.value() == "value"
    assert key_value.start().line() == 0
    assert key_value.start().column() == 5
    assert key_value.end().line() == 0
    assert key_value.end().column() == 10

    # Check "list" property
    list_value = mapped_properties["list"]
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
    embed_value = mapped_properties["embed"]
    assert isinstance(embed_value, KsonValue.KsonEmbed)
    assert embed_value.tag() == "tag"
    assert embed_value.content() == ""
    assert embed_value.start().line() == 5
    assert embed_value.start().column() == 6
    assert embed_value.end().line() == 6
    assert embed_value.end().column() == 2


def test_get_property_by_name_success():
    input = """name: John
age: 30
city: 'New York'"""
    analysis = Kson.analyze(input)
    value = analysis.kson_value()
    assert value is not None
    assert isinstance(value, KsonValue.KsonObject)

    name_value = value.get_property_by_name("name")
    assert name_value is not None
    assert isinstance(name_value, KsonValue.KsonString)
    assert name_value.value() == "John"

    age_value = value.get_property_by_name("age")
    assert age_value is not None
    assert isinstance(age_value, KsonValue.KsonNumber.Integer)
    assert age_value.value() == 30

    city_value = value.get_property_by_name("city")
    assert city_value is not None
    assert isinstance(city_value, KsonValue.KsonString)
    assert city_value.value() == "New York"


def test_get_property_by_name_with_nested_object():
    input = """person:
  name: Alice
  age: 25"""
    analysis = Kson.analyze(input)
    value = analysis.kson_value()
    assert value is not None
    assert isinstance(value, KsonValue.KsonObject)

    person_value = value.get_property_by_name("person")
    assert person_value is not None
    assert isinstance(person_value, KsonValue.KsonObject)

    nested_name = person_value.get_property_by_name("name")
    assert nested_name is not None
    assert isinstance(nested_name, KsonValue.KsonString)
    assert nested_name.value() == "Alice"


def test_get_property_by_name_nonexistent_property():
    input = """name: John
age: 30"""
    analysis = Kson.analyze(input)
    value = analysis.kson_value()
    assert value is not None
    assert isinstance(value, KsonValue.KsonObject)

    nonexistent_value = value.get_property_by_name("nonexistent")
    assert nonexistent_value is None


def test_get_property_by_name_with_different_value_types():
    input = """string: text
number: 42
decimal: 3.14
boolean: true
null_value: null
array: [1, 2, 3]
object: {key: value}"""

    analysis = Kson.analyze(input)
    value = analysis.kson_value()
    assert value is not None
    assert isinstance(value, KsonValue.KsonObject)

    # Test string
    string_value = value.get_property_by_name("string")
    assert string_value is not None
    assert isinstance(string_value, KsonValue.KsonString)
    assert string_value.value() == "text"

    # Test integer
    number_value = value.get_property_by_name("number")
    assert number_value is not None
    assert isinstance(number_value, KsonValue.KsonNumber.Integer)
    assert number_value.value() == 42

    # Test decimal
    decimal_value = value.get_property_by_name("decimal")
    assert decimal_value is not None
    assert isinstance(decimal_value, KsonValue.KsonNumber.Decimal)
    assert decimal_value.value() == 3.14

    # Test boolean
    boolean_value = value.get_property_by_name("boolean")
    assert boolean_value is not None
    assert isinstance(boolean_value, KsonValue.KsonBoolean)

    # Test null
    null_value = value.get_property_by_name("null_value")
    assert null_value is not None
    assert isinstance(null_value, KsonValue.KsonNull)

    # Test array
    array_value = value.get_property_by_name("array")
    assert array_value is not None
    assert isinstance(array_value, KsonValue.KsonArray)
    assert len(array_value.elements()) == 3

    # Test nested object
    object_value = value.get_property_by_name("object")
    assert object_value is not None
    assert isinstance(object_value, KsonValue.KsonObject)
