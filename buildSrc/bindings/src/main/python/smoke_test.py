from lib import *

def messages_to_string(msgs):
    output = ""
    for msg in msgs:
        p1 = msg.get_start()
        p2 = msg.get_end()
        line = f"{p1.get_line()},{p1.get_column()} to {p2.get_line()},{p2.get_column()} - {msg.get_message()}"
        output += line.strip()
        output += "\n"
    return output

def test_kson_format():
    indent = IndentType.Spaces(2)
    formatting = FormattingStyle.PLAIN.get()
    result = Kson.format(
        "key: [1, 2, 3, 4]",
        FormatOptions(indent, formatting),
    )

    assert result == """key:
  - 1
  - 2
  - 3
  - 4"""


def test_kson_to_json_success():
    result = Kson.toJson("key: [1, 2, 3, 4]")
    assert isinstance(result, Result.Success)
    assert result.get_output() == """{
  "key": [
    1,
    2,
    3,
    4
  ]
}"""


def test_kson_to_json_failure():
    result = Kson.toJson("key: [1, 2, 3, 4")
    assert isinstance(result, Result.Failure)
    output = messages_to_string(result.get_errors())
    assert output == "0,5 to 0,16 - Unclosed list\n"

def test_kson_analysis():
    analysis = Kson.analyze("key: [1, 2, 3, 4]")
    assert analysis.get_errors() == []

    # Transform tokens to strings, so we can snapshot them
    output = ""
    for token in analysis.get_tokens():
        p1 = token.get_start()
        p2 = token.get_end()
        line = f"{p1.get_line()},{p1.get_column()} to {p2.get_line()},{p2.get_column()} - {token.get_text()}"
        output += line.strip()
        output += "\n"

    assert output == """0,0 to 0,3 - key
0,3 to 0,4 - :
0,5 to 0,6 - [
0,6 to 0,7 - 1
0,7 to 0,8 - ,
0,9 to 0,10 - 2
0,10 to 0,11 - ,
0,12 to 0,13 - 3
0,13 to 0,14 - ,
0,15 to 0,16 - 4
0,16 to 0,17 - ]
0,17 to 0,17 -
"""
