use super::*;

#[test]
fn test_kson_parse_and_format() {
    // Parse
    let compileConfig = CoreCompileConfig::new("true", false, 42);
    let result = Kson::parseToAst("key: [1, 2, 3, 4]", &compileConfig);

    assert!(!result.hasErrors());

    // Compile
    let formatterConfig = KsonFormatterConfig::new(&IndentTypeSpace::new(4).upcast());
    let compileTarget = CompileTargetKson::new(false, &formatterConfig, &compileConfig);
    let kson = result.get_ast().upcast().toSource(&AstNodeIndent::new(), &compileTarget.upcast());
    insta::assert_snapshot!(kson, @r"
    key:
      - 1
      - 2
      - 3
      - 4
    ");
}
