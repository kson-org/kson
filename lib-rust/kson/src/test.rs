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
fn test_kson_format_classic() {
    let indent = IndentType::Spaces(IndentTypeSpaces::new(2));
    let result = Kson::format(
        "key: [1, 2, 3, 4]",
        &FormatOptions::new(&indent, &FormattingStyle::Classic),
    );
    insta::assert_snapshot!(result, @r#"
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

    let KsonValue::KsonObject(obj) = value else {
        panic!("expected object, found {:?}", value);
    };

    let properties = obj.properties();
    assert_eq!(properties.len(), 3);

    let mapped_properties: std::collections::HashMap<String, &KsonValue> = properties
        .iter()
        .map(|(key, value)| {
            let KsonValue::KsonString(s) = key else {
                panic!("expected string key");
            };
            (s.value().clone(), value)
        })
        .collect();

    // Check "key" property
    let key_value = mapped_properties.get("key").unwrap();
    let KsonValue::KsonString(string) = key_value else {
        panic!("expected string");
    };
    assert_eq!(string.value(), "value");
    assert_eq!(string.start().line(), 0);
    assert_eq!(string.start().column(), 5);
    assert_eq!(string.end().line(), 0);
    assert_eq!(string.end().column(), 10);

    // Check "list" property
    let list_value = mapped_properties.get("list").unwrap();
    let KsonValue::KsonArray(array) = list_value else {
        panic!("expected arrayay");
    };
    let elements = array.elements();
    assert_eq!(elements.len(), 3);
    assert_eq!(array.start().line(), 2);
    assert_eq!(array.start().column(), 2);
    assert_eq!(array.end().line(), 4);
    assert_eq!(array.end().column(), 7);

    // Check list elements
    let KsonValue::KsonInteger(integer) = &elements[0] else {
        panic!("expected integer");
    };
    assert_eq!(integer.value(), 1);
    assert_eq!(integer.start().line(), 2);
    assert_eq!(integer.start().column(), 4);
    assert_eq!(integer.end().line(), 2);
    assert_eq!(integer.end().column(), 5);

    let KsonValue::KsonDecimal(decimal1) = &elements[1] else {
        panic!("expected decimal");
    };
    assert_eq!(decimal1.value(), 2.1);
    assert_eq!(decimal1.start().line(), 3);
    assert_eq!(decimal1.start().column(), 4);
    assert_eq!(decimal1.end().line(), 3);
    assert_eq!(decimal1.end().column(), 7);

    let KsonValue::KsonDecimal(decimal2) = &elements[2] else {
        panic!("expected decimal");
    };
    assert_eq!(decimal2.value(), 3e5);
    assert_eq!(decimal2.start().line(), 4);
    assert_eq!(decimal2.start().column(), 4);
    assert_eq!(decimal2.end().line(), 4);
    assert_eq!(decimal2.end().column(), 7);

    // Check "embed" property
    let embed_value = mapped_properties.get("embed").unwrap();
    let KsonValue::KsonEmbed(embed) = embed_value else {
        panic!("expected embed");
    };
    assert_eq!(embed.tag(), Some("tag".to_string()));
    assert_eq!(embed.content(), "");
    assert_eq!(embed.start().line(), 5);
    assert_eq!(embed.start().column(), 6);
    assert_eq!(embed.end().line(), 6);
    assert_eq!(embed.end().column(), 2);
}

#[test]
fn test_get_property_by_name_success() {
    let input = r#"name: John
age: 30
city: 'New York'"#;
    let analysis = Kson::analyze(input);
    let value = analysis.kson_value().unwrap();

    let KsonValue::KsonObject(obj) = value else {
        panic!("expected object");
    };

    let name_value = obj.get_property_by_name("name");
    assert!(name_value.is_some());
    let KsonValue::KsonString(name_str) = name_value.unwrap() else {
        panic!("expected string");
    };
    assert_eq!(name_str.value(), "John");

    let age_value = obj.get_property_by_name("age");
    assert!(age_value.is_some());
    let KsonValue::KsonInteger(age_int) = age_value.unwrap() else {
        panic!("expected integer");
    };
    assert_eq!(age_int.value(), 30);

    let city_value = obj.get_property_by_name("city");
    assert!(city_value.is_some());
    let KsonValue::KsonString(city_str) = city_value.unwrap() else {
        panic!("expected string");
    };
    assert_eq!(city_str.value(), "New York");
}

#[test]
fn test_get_property_by_name_with_nested_object() {
    let input = r#"person:
  name: Alice
  age: 25"#;
    let analysis = Kson::analyze(input);
    let value = analysis.kson_value().unwrap();

    let KsonValue::KsonObject(obj) = value else {
        panic!("expected object");
    };

    let person_value = obj.get_property_by_name("person");
    assert!(person_value.is_some());
    let KsonValue::KsonObject(person_obj) = person_value.unwrap() else {
        panic!("expected object");
    };

    let nested_name = person_obj.get_property_by_name("name");
    assert!(nested_name.is_some());
    let KsonValue::KsonString(nested_name_str) = nested_name.unwrap() else {
        panic!("expected string");
    };
    assert_eq!(nested_name_str.value(), "Alice");
}

#[test]
fn test_get_property_by_name_nonexistent_property() {
    let input = r#"name: John
age: 30"#;
    let analysis = Kson::analyze(input);
    let value = analysis.kson_value().unwrap();

    let KsonValue::KsonObject(obj) = value else {
        panic!("expected object");
    };

    let nonexistent_value = obj.get_property_by_name("nonexistent");
    assert!(nonexistent_value.is_none());
}

#[test]
fn test_get_property_by_name_with_different_value_types() {
    let input = r#"string: text
number: 42
decimal: 3.14
boolean: true
null_value: null
array: [1, 2, 3]
object: {key: value}"#;

    let analysis = Kson::analyze(input);
    let value = analysis.kson_value().unwrap();

    let KsonValue::KsonObject(obj) = value else {
        panic!("expected object");
    };

    // Test string
    let string_value = obj.get_property_by_name("string");
    assert!(string_value.is_some());
    let KsonValue::KsonString(string_str) = string_value.unwrap() else {
        panic!("expected string");
    };
    assert_eq!(string_str.value(), "text");

    // Test integer
    let number_value = obj.get_property_by_name("number");
    assert!(number_value.is_some());
    let KsonValue::KsonInteger(number_int) = number_value.unwrap() else {
        panic!("expected integer");
    };
    assert_eq!(number_int.value(), 42);

    // Test decimal
    let decimal_value = obj.get_property_by_name("decimal");
    assert!(decimal_value.is_some());
    let KsonValue::KsonDecimal(decimal_dec) = decimal_value.unwrap() else {
        panic!("expected decimal");
    };
    assert_eq!(decimal_dec.value(), 3.14);

    // Test boolean
    let boolean_value = obj.get_property_by_name("boolean");
    assert!(boolean_value.is_some());
    assert!(matches!(boolean_value.unwrap(), KsonValue::KsonBoolean(_)));

    // Test null
    let null_value = obj.get_property_by_name("null_value");
    assert!(null_value.is_some());
    assert!(matches!(null_value.unwrap(), KsonValue::KsonNull(_)));

    // Test array
    let array_value = obj.get_property_by_name("array");
    assert!(array_value.is_some());
    let KsonValue::KsonArray(array_arr) = array_value.unwrap() else {
        panic!("expected array");
    };
    assert_eq!(array_arr.elements().len(), 3);

    // Test nested object
    let object_value = obj.get_property_by_name("object");
    assert!(object_value.is_some());
    assert!(matches!(object_value.unwrap(), KsonValue::KsonObject(_)));
}
