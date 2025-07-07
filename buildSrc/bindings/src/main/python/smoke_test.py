from lib import *

def test_smoke_1():
    # Parse
    compileConfig = CoreCompileConfig("true", False, 42)
    result = Kson.parseToAst("key: [1, 2, 3, 4]", compileConfig)
    assert not result.hasErrors()

    # Compile
    formatterConfig = KsonFormatterConfig(IndentType.Space(4))
    compileTarget = CompileTarget.Kson(False, formatterConfig, compileConfig)
    kson = result.get_ast().toSource(AstNode.Indent(), compileTarget)
    assert kson == "key:\n  - 1\n  - 2\n  - 3\n  - 4"
