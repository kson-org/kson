package org.kson.jsonsuite

import org.kson.CleanGitCheckout
import java.nio.file.Path

class JsonSuiteGitCheckout(jsonTestSuiteSHA: String, destinationDir: Path)
    : CleanGitCheckout("https://github.com/nst/JSONTestSuite.git", jsonTestSuiteSHA, destinationDir, "JSONTestSuite", dirtyMessage)
class SchemaSuiteGitCheckout(schemaTestSuiteSHA: String, destinationDir: Path)
    : CleanGitCheckout("https://github.com/json-schema-org/JSON-Schema-Test-Suite.git", schemaTestSuiteSHA, destinationDir, "JSON-Schema-Test-Suite", dirtyMessage)

/**
 * The rationale for why these [CleanGitCheckout]s must be clean
 */
private const val dirtyMessage = "This needs to be clean since we generate files from this repo.\n"
