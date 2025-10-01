use super::*;

#[test]
fn test_kson_format() {
    let indent = IndentType::Spaces(IndentTypeSpaces::new(2));
    let result = Kson::format(
        "key: [1, 2, 3, 4]",
        &FormatOptions::new(&indent, &FormattingStyle::Plain),
    );
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
    let result = Kson::to_json("key: [1, 2, 3, 4]");
    match result {
        Err(_) => panic!("expected success, found failure"),
        Ok(success) => {
            insta::assert_snapshot!(success.output(), @r#"
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
    }
}

#[test]
fn test_kson_to_json_with_embed_tags() {
    let kson_with_embed = r#"key: $embed
This is embedded content
embed$$"#;

    // Test with default retain_embed_tags=true
    let result = Kson::to_json(kson_with_embed);
    match result {
        Err(_) => panic!("expected success, found failure"),
        Ok(success) => {
            insta::assert_snapshot!(success.output(), @r#"
            {
              "key": {
                "embedTag": "embed",
                "embedContent": "This is embedded content\nembed"
              }
            }
            "#);
        }
    }

    // Test with retain_embed_tags=false
    let result = Kson::to_json_with_options(kson_with_embed, false);
    match result {
        Err(_) => panic!("expected success, found failure"),
        Ok(success) => {
            insta::assert_snapshot!(success.output(), @r#"
            {
              "key": "This is embedded content\nembed"
            }
            "#);
        }
    }
}

#[test]
fn test_kson_to_json_failure() {
    let result = Kson::to_json("key: [1, 2, 3, 4");
    match result {
        Ok(_) => panic!("expected failure, found success"),
        Err(failure) => {
            let output = messages_to_string(&failure.errors());
            insta::assert_snapshot!(output, @"0,5 to 0,16 - Unclosed list\n");
        }
    }
}

#[test]
fn test_kson_to_yaml_success() {
    let result = Kson::to_yaml("key: [1, 2, 3, 4]");
    match result {
        Err(_) => panic!("expected success, found failure"),
        Ok(success) => {
            insta::assert_snapshot!(success.output(), @r"
            key:
              - 1
              - 2
              - 3
              - 4
            ");
        }
    }
}

#[test]
fn test_kson_to_yaml_with_embed_tags() {
    let kson_with_embed = r#"key: $embed
This is embedded content
embed$$"#;

    // Test with default retain_embed_tags=true
    let result = Kson::to_yaml(kson_with_embed);
    match result {
        Err(_) => panic!("expected success, found failure"),
        Ok(success) => {
            insta::assert_snapshot!(success.output(), @r#"
            key:
              embedTag: "embed"
              embedContent: |
                This is embedded content
                embed
            "#);
        }
    }

    // Test with retain_embed_tags=false
    let result = Kson::to_yaml_with_options(kson_with_embed, false);
    match result {
        Err(_) => panic!("expected success, found failure"),
        Ok(success) => {
            insta::assert_snapshot!(success.output(), @r"
            key: |
                This is embedded content
                embed
            ");
        }
    }
}

#[test]
fn test_kson_to_yaml_failure() {
    let result = Kson::to_yaml("key: [1, 2, 3, 4");
    match result {
        Ok(_) => panic!("expected failure, found success"),
        Err(failure) => {
            let output = messages_to_string(&failure.errors());
            insta::assert_snapshot!(output, @"0,5 to 0,16 - Unclosed list\n");
        }
    }
}

#[test]
fn test_kson_analysis() {
    let analysis = Kson::analyze("key: [1, 2, 3, 4]");
    assert!(analysis.errors().is_empty());

    // Transform tokens to strings, so we can snapshot them
    let mut output = String::new();
    for token in analysis.tokens() {
        let p1 = token.start();
        let p2 = token.end();
        let line = format!(
            "{},{} to {},{} - {}: {}\n",
            p1.line(),
            p1.column(),
            p2.line(),
            p2.column(),
            token.token_type().name(),
            token.text()
        );
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
    let result = Kson::parse_schema(r#"{ "type": "string" }"#);
    let Ok(success) = result else {
        panic!("expected success, found failure")
    };

    let validator = success.schema_validator();
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
    let p1 = msg.start();
    let p2 = msg.end();
    format!(
        "{},{} to {},{} - {}\n",
        p1.line(),
        p1.column(),
        p2.line(),
        p2.column(),
        msg.message()
    )
}

#[test]
fn test_kson_value() {
    let input = r#"key: value
list:
  - 1
  - 2.1
  - 3E5
embed:%tag
%%"#;
    let analysis = Kson::analyze(input);
    let kson_value = analysis.kson_value();
    assert!(kson_value.is_some());

    let value = kson_value.unwrap();

    // Check root object location
    assert_eq!(value.start().line(), 0);
    assert_eq!(value.start().column(), 0);
    assert_eq!(value.end().line(), 6);
    assert_eq!(value.end().column(), 2);

    let KsonValue::KsonObject { ref properties, .. } = value else {
        panic!("expected object, found {:?}", value);
    };

    assert_eq!(properties.len(), 3);

    let mapped_properties: std::collections::HashMap<String, &KsonValue> = properties
        .iter()
        .map(|(key, value)| {
            let KsonValue::KsonString { value: key_str, .. } = key else {
                panic!("expected string key");
            };
            (key_str.clone(), value)
        })
        .collect();

    // Check "key" property
    let key_value = mapped_properties.get("key").unwrap();
    let KsonValue::KsonString {
        value: key_str,
        start,
        end,
    } = key_value
    else {
        panic!("expected string");
    };
    assert_eq!(key_str, "value");
    assert_eq!(start.line(), 0);
    assert_eq!(start.column(), 5);
    assert_eq!(end.line(), 0);
    assert_eq!(end.column(), 10);

    // Check "list" property
    let list_value = mapped_properties.get("list").unwrap();
    let KsonValue::KsonArray {
        elements,
        start,
        end,
    } = list_value
    else {
        panic!("expected array");
    };
    assert_eq!(elements.len(), 3);
    assert_eq!(start.line(), 2);
    assert_eq!(start.column(), 2);
    assert_eq!(end.line(), 4);
    assert_eq!(end.column(), 7);

    // Check list elements
    let KsonValue::KsonInteger {
        value: first_val,
        start,
        end,
    } = &elements[0]
    else {
        panic!("expected integer");
    };
    assert_eq!(*first_val, 1);
    assert_eq!(start.line(), 2);
    assert_eq!(start.column(), 4);
    assert_eq!(end.line(), 2);
    assert_eq!(end.column(), 5);

    let KsonValue::KsonDecimal {
        value: second_val,
        start,
        end,
    } = &elements[1]
    else {
        panic!("expected decimal");
    };
    assert_eq!(*second_val, 2.1);
    assert_eq!(start.line(), 3);
    assert_eq!(start.column(), 4);
    assert_eq!(end.line(), 3);
    assert_eq!(end.column(), 7);

    let KsonValue::KsonDecimal {
        value: third_val,
        start,
        end,
    } = &elements[2]
    else {
        panic!("expected decimal");
    };
    assert_eq!(*third_val, 3e5);
    assert_eq!(start.line(), 4);
    assert_eq!(start.column(), 4);
    assert_eq!(end.line(), 4);
    assert_eq!(end.column(), 7);

    // Check "embed" property
    let embed_value = mapped_properties.get("embed").unwrap();
    let KsonValue::KsonEmbed {
        tag,
        content,
        start,
        end,
        ..
    } = embed_value
    else {
        panic!("expected embed");
    };
    assert_eq!(tag, &Some("tag".to_string()));
    assert_eq!(content, "");
    assert_eq!(start.line(), 5);
    assert_eq!(start.column(), 6);
    assert_eq!(end.line(), 6);
    assert_eq!(end.column(), 2);
}
