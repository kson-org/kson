import shutil

# Note: when packaging the python library, the kson DLL will be in a place the OS can find. For
# testing, we need to put it in the same dir as the main python script.
shutil.copy("libkson/kson.dll", "kson.dll")

from lib import *

# Parse
compileConfig = CoreCompileConfig("true", False, 42)
result = Kson.parseToAst("key: [1, 2, 3, 4]", compileConfig)

if result.hasErrors():
    print(result.toString())
else:
    # Compile
    formatterConfig = KsonFormatterConfig(IndentType.Space(4))
    compileTarget = CompileTarget.Kson(False, formatterConfig, compileConfig)
    json = result.get_ast().toSource(AstNode.Indent(), compileTarget)
    print(json)
