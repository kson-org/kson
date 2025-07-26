use super::*;

#[test]
fn test_kson_format() {
    let indent = IndentTypeSpaces::new(2).upcast();
    let result = Kson::format("key: [1, 2, 3, 4]", &FormatOptions::new(&indent, &FormattingStyle::PLAIN));
    insta::assert_snapshot!(result, @r"
    key:
      - 1
      - 2
      - 3
      - 4
    ");
}

#[test]
fn test_kson_to_json_success() {
    let result = Kson::toJson("key: [1, 2, 3, 4]");
    let Ok(success) = result.downcastToSuccess() else {
      panic!("expected success, found failure")
    };

    insta::assert_snapshot!(success.get_output(), @r#"
    {
      "key": [
        1,
        2,
        3,
        4
      ]
    }
    "#);
}

#[test]
fn test_kson_to_json_failure() {
    let result = Kson::toJson("key: [1, 2, 3, 4");
    let Ok(failure) = result.downcastToFailure() else {
      panic!("expected failure, found success")
    };

    let output = messages_to_string(&failure.get_errors());
    insta::assert_snapshot!(output, @"0,5 to 0,16 - Unclosed list\n");
}

#[test]
fn test_kson_analysis() {
    let analysis = Kson::analyze("key: [1, 2, 3, 4]");
    assert!(analysis.get_errors().is_empty());

    // Transform tokens to strings, so we can snapshot them
    let mut output = String::new();
    for token in analysis.get_tokens() {
      let p1 = token.get_start();
      let p2 = token.get_end();
      let line = format!("{},{} to {},{} - {}: {}\n", p1.get_line(), p1.get_column(), p2.get_line(), p2.get_column(), token.get_tokenType().name(), token.get_text());
      output.push_str(&line);
    }

    insta::assert_snapshot!(output, @r"
    0,0 to 0,3 - UNQUOTED_STRING: key
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
    ");
}

#[test]
fn test_kson_validate_schema() {
    let result = Kson::parseSchema(r#"{ "type": "string" }"#);
    let Ok(success) = result.downcastToSuccess() else {
      panic!("expected success, found failure")
    };

    let validator = success.get_schemaValidator();
    let errors = validator.validate(r#""a good old JSON string""#);
    assert!(errors.is_empty());

    let errors = validator.validate(r#"42"#);
    assert!(!errors.is_empty());

    let output = messages_to_string(&errors);
    insta::assert_snapshot!(output, @"0,0 to 0,2 - Expected one of: string, but got: integer");
}

/// Transform messages to strings, so we can snapshot them
fn messages_to_string(msgs: &[Message]) -> String {
  let mut output = String::new();
  for msg in msgs {
    let line = format!("{}\n", message_to_string(&msg));
    output.push_str(&line);
  }
  output
}

fn message_to_string(msg: &Message) -> String {
  let p1 = msg.get_start();
  let p2 = msg.get_end();
  format!("{},{} to {},{} - {}\n", p1.get_line(), p1.get_column(), p2.get_line(), p2.get_column(), msg.get_message())
}
