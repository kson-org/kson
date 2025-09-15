package org.kson.jsonsuite

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.File
import java.nio.file.Path

/**
 * [JsonTestSuiteGenerator] generates native Kotlin tests based on the tests defined in
 * [JSONTestSuite](https://github.com/nst/JSONTestSuite) and [JSON-Schema-Test-Suite](https://github.com/json-schema-org/JSON-Schema-Test-Suite)
 *
 * By generating the tests into pure Kotlin test methods, we not only have the ergonomics of having
 * an actual test method per test from these suites, we also get the benefit of being able to run them across all
 * platforms without wrangling cross-platform file reads
 *
 * See [JsonTestSuiteEditList] for info on the adjustments we make to the JSONTestSuite to suit Kson's needs as a
 * superset of JSON
 *
 * @param jsonSuiteGitCheckout an instance of [JsonSuiteGitCheckout]
 * @param schemaSuiteGitCheckout an instance of [SchemaSuiteGitCheckout]
 * @param sourceRootDir The directory to consider the src root - [classPackage] will be used to determine which
 *   sub-folder relative to [sourceRootDir] to place generated tests into
 * @param classPackage The package to place the generated tests into
 */
class JsonTestSuiteGenerator(
    private val jsonSuiteGitCheckout: JsonSuiteGitCheckout,
    private val schemaSuiteGitCheckout: SchemaSuiteGitCheckout,
    private val projectRoot: Path,
    private val sourceRootDir: Path,
    private val classPackage: String
) {
    val jsonTestSourceFilesDir: Path = jsonSuiteGitCheckout.checkoutDir.toPath().resolve("test_parsing")
    val testClassPackageDir = sourceRootDir.resolve(classPackage.replace('.', '/'))
    val generatedJsonSuiteTestPath: Path =
        testClassPackageDir.resolve("JsonSuiteTest.kt")
    val draft7TestClassName = "SchemaDraft7SuiteTest"
    val draft2020_12ClassName = "SchemaDraft2020_12SuiteTest"

    fun generate() {
        testClassPackageDir.toFile().mkdirs()

        val jsonTestDataList = JsonTestDataLoader(jsonTestSourceFilesDir, projectRoot).loadTestData()
        generatedJsonSuiteTestPath.toFile()
            .writeText(generateJsonSuiteTestClass(this.javaClass.name, classPackage, jsonTestDataList))

        val schemaTestSourceFilesDir: Path = schemaSuiteGitCheckout.checkoutDir.toPath().resolve("tests")

        val schemaDraft7TestDataList = SchemaTestDataLoader(schemaTestSourceFilesDir, "draft7", projectRoot).loadTestData()
        schemaDraft7TestDataList.forEach { schemaTestFileData ->
            val testClassName = draft7TestClassName + "_" + schemaTestFileData.testFileName.replace("-", "_")
            val testFileName = testClassPackageDir.resolve("$testClassName.kt")
            testFileName.toFile().writeText(
                generateSchemaSuiteTestClass(
                    this.javaClass.name,
                    classPackage,
                    testClassName,
                    schemaTestFileData))
        }

        val schemaDraft2020_12TestDataList = SchemaTestDataLoader(schemaTestSourceFilesDir, "draft2020-12", projectRoot).loadTestData()
        schemaDraft2020_12TestDataList.forEach { schemaTestFileData ->
            val testClassName = draft2020_12ClassName + "_" + schemaTestFileData.testFileName.replace("-", "_")
            val testFileName = testClassPackageDir.resolve("$testClassName.kt")
            testFileName.toFile().writeText(
                generateSchemaSuiteTestClass(
                    this.javaClass.name,
                    classPackage,
                    testClassName,
                    schemaTestFileData))
        }
    }
}

/**
 * The properties used to generate the expected results enum in [generateJsonSuiteTestClass]
 */
private class ResultEnumData {
    companion object {
        const val className = "JsonParseResult"
        const val acceptEntry = "ACCEPT"
        const val acceptEntryForKson = "ACCEPT_FOR_KSON"
        const val rejectEntry = "REJECT"
        const val unspecifiedEntry = "UNSPECIFIED"
    }
}

private class JsonTestData(
    val rawTestName: String,
    val testSource: String,
    val filePathFromProjectRoot: String,
    val testEditType: JsonTestEditType
) {
    val isSkipped = testEditType == JsonTestEditType.SKIP_NEEDS_INVESTIGATION

    /**
     * Create a legal MPP Kotlin test name out of the raw test name by replacing problematic characters
     */
    val testName = rawTestName.replace("-", "DASH").replace(".", "DOT").replace("+", "PLUS").replace("#", "HASH")

    fun parsingRequirement(): String {
        return when (testEditType) {
            JsonTestEditType.ACCEPT_N_FOR_SUPERSET -> {
                if (!rawTestName.startsWith("n_")) {
                    throw RuntimeException("Invalid use of ${JsonTestEditType.ACCEPT_N_FOR_SUPERSET::class.simpleName}: this edit only applies to overriding `n_`-type rejection tests")
                }
                return ResultEnumData.acceptEntryForKson
            }
            JsonTestEditType.SKIP_NEEDS_INVESTIGATION, JsonTestEditType.NONE -> {
                when {
                    rawTestName.startsWith("i_") -> ResultEnumData.unspecifiedEntry
                    rawTestName.startsWith("y_") -> ResultEnumData.acceptEntry
                    rawTestName.startsWith("n_") -> ResultEnumData.rejectEntry
                    else -> throw RuntimeException("Unexpected test prefix---should only have i/y/n tests")
                }
            }
        }
    }
}

/**
 * The [JSON-Schema-Test-Suite](https://github.com/json-schema-org/JSON-Schema-Test-Suite) tests are organized
 * into groups where [schema] provides a Json Schema, and the list of [SchemaTestSpec] specify Json data
 * to apply that schema to and whether that data should be considered valid or not
 */
@Serializable
private data class SchemaTestGroup(val description: String,
                                   val comment: String? = null,
                                   val schema: JsonElement,
                                   val tests: List<SchemaTestSpec>,
                                   val specification: JsonElement? = null)
@Serializable
private data class SchemaTestSpec(val description: String, val comment: String? = null, val data: JsonElement, val valid: Boolean)

/**
 * The test data we parse out of the test files provided by [JSON-Schema-Test-Suite](https://github.com/json-schema-org/JSON-Schema-Test-Suite)
 */
private class SchemaTestData(
    val testFileName: String,
    val filePathFromProjectRoot: String,
    val schemaTestGroups: List<SchemaTestGroup>
)

private fun generateJsonSuiteTestClass(
    generatorClassName: String,
    testClassPackage: String,
    tests: List<JsonTestData>
): String {
    return """package $testClassPackage

import org.kson.KsonCore
import org.kson.parser.LoggedMessage
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * DO NOT MANUALLY EDIT.  This class is GENERATED by `./gradlew generateJsonTestSuite` task 
 * which calls [$generatorClassName]---see that class for more info.
 */
class JsonSuiteTest {

${
        tests.joinToString("\n\n") {
            val theComment = """
        |    /**
        |     * Test generated by [$generatorClassName] based on ${it.rawTestName} in JSONTestSuite (see: ${it.filePathFromProjectRoot})
        |""".trimMargin() +
                    if (it.isSkipped) {
                        "     *\n" +
                                "     * To uncomment and include this test in the running suite, remove it from\n" +
                                "     * [${JsonTestSuiteEditList::class.qualifiedName}] and regenerate this file\n"
                    } else {
                        ""
                    } +
                    """
        |     */
        |""".trimMargin()

            val theTest = """
        |    @Test
        |""".trimMargin() +
                    "    fun ${it.testName}() {\n" +
                    "        assertParseResult(\n" + "            " +
                    "${ResultEnumData.className}.${it.parsingRequirement()},\n" +
                    "            \"\"\"" + it.testSource + "\"\"\"\n" +
                    "        )\n" +
                    "    }"

            theComment + if (it.isSkipped) {
                // comment out our skipped tests
                theTest.split('\n').joinToString("\n//", "//")
            } else {
                theTest
            }
        }
    }
}

private enum class ${ResultEnumData.className} {
    /**
     * Parser must accept the given source as valid JSON
     */
    ${ResultEnumData.acceptEntry},
    
    /**
     * Parser must accept this invalid JSON because it is valid KSON (part of KSON being a superset of JSON)
     */
     ${ResultEnumData.acceptEntryForKson},

    /**
     * Parser must reject the given source as invalid JSON
     */
    ${ResultEnumData.rejectEntry},

    /**
     * The JSON spec does not define a correct response to the given source.
     * i.e. A spec-compliant JSON parser is free accept or reject the given source
     */
    ${ResultEnumData.unspecifiedEntry}
}

private fun assertParseResult(
    expectedParseResult: JsonParseResult,
    source: String
) {
    val parseResult = KsonCore.parseToAst(source)

    when (expectedParseResult) {
        ${ResultEnumData.className}.${ResultEnumData.acceptEntry}, ${ResultEnumData.className}.${ResultEnumData.acceptEntryForKson} -> assertFalse(
            parseResult.hasErrors(),
            "Should have accepted `" + source + "`, but rejected as invalid.  Errors produced:\n\n" + LoggedMessage.print(parseResult.messages)
        )
        ${ResultEnumData.className}.${ResultEnumData.rejectEntry} -> assertTrue(
            parseResult.hasErrors(),
            "Should have rejected `" + source + "`, but accepted as valid Kson.  Do we a new entry in ${JsonTestSuiteEditList::class.simpleName}?"
        )
        ${ResultEnumData.className}.${ResultEnumData.unspecifiedEntry} -> {
            // no-op: doesn't matter if we accept or reject as long as we didn't blow up
        }
    }
}

"""
}

private fun generateSchemaSuiteTestClass(
    generatorClassName: String,
    testClassPackage: String,
    testClassName: String,
    tests: SchemaTestData
): String {
    var testNum = 1
    return """package $testClassPackage

import org.kson.schema.JsonSchemaTest
import kotlin.test.Test

/**
 * DO NOT MANUALLY EDIT.  This class is GENERATED by `./gradlew generateJsonTestSuite` task 
 * which calls [$generatorClassName]---see that class for more info.
 *
 * TODO expand the testing here as we implement Json Schema support by 
 *   removing exclusions from [org.kson.jsonsuite.schemaTestSuiteExclusions]
 */
@Suppress("UNREACHABLE_CODE", "ClassName") // unreachable code is okay here until we complete the above TODO
class $testClassName : JsonSchemaTest {

${    run {
        val theTests = ArrayList<String>()
        for (schema in tests.schemaTestGroups) {
            val schemaComment = if (schema.comment != null) "// " + schema.comment else ""
            for (test in schema.tests) {
                // construct a unique ID for this test
                val schemaTestId = "${tests.testFileName}::${schema.description}::${test.description}"
                val testCode = """
                    |    /**
                    |     * Test generated by [$generatorClassName] based on `${tests.filePathFromProjectRoot}`:
                    |     *     "${schema.description} -> ${test.description}"
                    |     *
                    |     * Test ID: "$schemaTestId"
                    |     */
                    |    @Test
                    |    fun jsonSchemaSuiteTest_${testNum++}() {${
                        if (schemaTestSuiteExclusions().contains(schemaTestId)) {
                            """
                    |
                    |       /**
                    |        * TODO implement the schema functionality under test here and remove the exclusion entry
                    |        * "$schemaTestId" from 
                    |        * [org.kson.jsonsuite.schemaTestSuiteExclusions]
                    |        */
                    |        return""".trimMargin()
                        }
                    else {
                            ""
                        }}
                    |        $schemaComment
                    |        assertKsonEnforcesSchema(
                    |            ${"\"\"\""}
                    |                ${formatForTest(test.data, "                ")}
                    |            ${"\"\"\""},
                    |            ${"\"\"\""}
                    |                ${formatForTest(schema.schema, "                ")}
                    |            ${"\"\"\""},
                    |            ${test.valid},
                    |            ${"\"\"\""}    schemaTestId: "${schemaTestId.replace("$", "\${'$'}")}"    ${"\"\"\""})
                    |    }
                    """.trimMargin()
                theTests.add(testCode)
            }
        }
        theTests.joinToString("\n\n")
    }}
}
"""
}

/**
 * This class manages loading and transforming the [JSONTestSuite](https://github.com/nst/JSONTestSuite)
 * tests to facilitate writing them as native, platform-independent, Kotlin tests in [JsonTestSuiteGenerator]
 *
 * Property [jsonTestSuiteEditList] contains the list of tests we currently skip
 *
 * @param testDefinitionFilesDir the [Path] on disk to the [JSONTestSuite](https://github.com/nst/JSONTestSuite) test files
 * @param projectRoot the [Path] on disk of the project containing [testDefinitionFilesDir] - used to write out
 *                      machine-independent file paths relative to the project root
 */
private class JsonTestDataLoader(private val testDefinitionFilesDir: Path, private val projectRoot: Path) {
    private val testFiles = (testDefinitionFilesDir.toFile().listFiles()
        ?: throw RuntimeException("Should have ensured these files existed before calling this loader"))

    init {
        val testDefinitionFileNames = testFiles.map { it.name }.toSet()

        // ensure all the test names in jsonTestSuiteSkipList are valid
        for (testFileName in JsonTestSuiteEditList.all()) {
            if (!testDefinitionFileNames.contains(testFileName)) {
                throw RuntimeException("Invalid JSONTestSuite test file name \"$testFileName\".\n" +
                        "File not found amongst test files in ${testFiles.first().parentFile}:\n" +
                        testFiles.joinToString(",\n") { it.name })
            }
        }
    }

    fun loadTestData(): List<JsonTestData> {
        return testFiles.map {
            JsonTestData(
                it.nameWithoutExtension,
                // explicitly note UTF-8 here since the JSON spec specifies that as the proper json encoding
                it.readText(Charsets.UTF_8),
                it.absolutePath.replace(File.separatorChar, '/').replace("${projectRoot.toString().replace(File.separatorChar, '/')}/", ""),
                JsonTestSuiteEditList.get(it.name)
            )
        }.sortedBy { it.rawTestName }
    }
}

/**
 * This class manages loading and transforming the [JSON-Schema-Test-Suite](https://github.com/json-schema-org/JSON-Schema-Test-Suite)
 * tests to facilitate writing them as native, platform-independent, Kotlin tests in [JsonTestSuiteGenerator]
 *
 * NOTE: we currently only support Draft7 version of Json Schema. TODO expand support to other version.
 *
 * @param testDefinitionFilesDir the [Path] on disk to the [JSON-Schema-Test-Suite](https://github.com/json-schema-org/JSON-Schema-Test-Suite)
 *        test files
 *
 * @param projectRoot the [Path] on disk of the project containing [testDefinitionFilesDir] - used to write out
 *                      machine-independent file paths relative to the project root
 */
private class SchemaTestDataLoader(private val testDefinitionFilesDir: Path, draftTestFolderName: String, private val projectRoot: Path) {
    private val draftSevenTestSourceFiles = (testDefinitionFilesDir.resolve(draftTestFolderName).toFile().listFiles()
        ?: throw RuntimeException("Should have ensured these files existed before calling this loader"))

    fun loadTestData(): List<SchemaTestData> {
        return draftSevenTestSourceFiles
            .filter { !it.isDirectory }
            .map {
                val contents = it.readText(Charsets.UTF_8)
                val schemaTestGroups: List<SchemaTestGroup> = prettyPrintingJson.decodeFromString(contents)
                SchemaTestData(
                    it.nameWithoutExtension,
                    it.absolutePath.replace(File.separatorChar, '/').replace("${projectRoot.toString().replace(File.separatorChar, '/')}/", ""),
                    schemaTestGroups,
                )
            }.sortedBy { it.filePathFromProjectRoot }
    }
}

@OptIn(ExperimentalSerializationApi::class)
private val prettyPrintingJson = Json {
    prettyPrint = true
    // 4 spaces for indentation
    prettyPrintIndent = "    "
}
private fun formatForTest(jsonElement: JsonElement, indent: String): String {
    return formatForTest(prettyPrintingJson.encodeToString(JsonElement.serializer(), jsonElement))
        .split("\n")
        .joinToString(separator = "\n$indent")
}

private fun formatForTest(string: String): String {
    return string.replace("$", "\${'$'}")
}
