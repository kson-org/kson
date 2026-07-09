package org.kson.jsonsuite

import org.kson.gitcheckout.CleanGitCheckout
import java.nio.file.Path

fun JsonSuiteGitCheckout(jsonTestSuiteSHA: String, destinationDir: Path): CleanGitCheckout =
    CleanGitCheckout(
        "https://github.com/nst/JSONTestSuite.git", jsonTestSuiteSHA, destinationDir, "JSONTestSuite",
        acceptableUntrackedFiles = setOf(".DS_Store", "Thumbs.db"), dirtyMessage = dirtyMessage
    )

fun SchemaSuiteGitCheckout(schemaTestSuiteSHA: String, destinationDir: Path): CleanGitCheckout =
    CleanGitCheckout(
        "https://github.com/json-schema-org/JSON-Schema-Test-Suite.git", schemaTestSuiteSHA, destinationDir,
        "JSON-Schema-Test-Suite",
        acceptableUntrackedFiles = setOf(".DS_Store", "Thumbs.db"), dirtyMessage = dirtyMessage
    )

/**
 * The rationale for why these [CleanGitCheckout]s must be clean
 */
private const val dirtyMessage = "This needs to be clean since we generate files from this repo.\n"
