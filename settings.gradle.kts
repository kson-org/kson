rootProject.name = "kson"
include("tooling:jetbrains")
// todo lib-kotlin: temporarily disable the subprojects relying on the JSExports we're refactoring
//include("tooling:language-server-protocol")
//include("tooling:vs-code")
include("lib-kotlin")
