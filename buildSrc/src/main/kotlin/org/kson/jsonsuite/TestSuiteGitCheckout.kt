package org.kson.jsonsuite

import org.kson.CleanGitCheckout
import java.nio.file.Path

class JsonSuiteGitCheckout(jsonTestSuiteSHA: String, destinationDir: Path)
    : CleanGitCheckout("https://github.com/nst/JSONTestSuite.git", jsonTestSuiteSHA, destinationDir, "JSONTestSuite")
class SchemaSuiteGitCheckout(schemaTestSuiteSHA: String, destinationDir: Path)
    : CleanGitCheckout("https://github.com/json-schema-org/JSON-Schema-Test-Suite.git", schemaTestSuiteSHA, destinationDir, "JSON-Schema-Test-Suite")
