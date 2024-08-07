import org.gradle.api.DefaultTask
import org.gradle.api.tasks.*
import org.kson.jsonsuite.JsonTestSuiteGenerator
import java.io.File
import java.nio.file.Paths

/**
 * The Git SHAs in [JSONTestSuite](https://github.com/nst/JSONTestSuite) and [JSON-Schema-Test-Suite](https://github.com/json-schema-org/JSON-Schema-Test-Suite)
 * that we currently test against.
 *
 * These can be updated if/when we want to pull in newer tests from those projects.
 */
private const val jsonTestSuiteSHA = "984defc2deaa653cb73cd29f4144a720ec9efe7c"
private const val schemaTestSuiteSHA = "9fc880bfb6d8ccd093bc82431f17d13681ffae8e"

/**
 * The Git SHA in the [JSONTestSuite](https://github.com/nst/JSONTestSuite) we currently test against.
 * This can be updated if/when we want to pull in newer tests from that project.
 */

/**
 * This task exposes [JsonTestSuiteGenerator] to our Gradle build, ensuring the task's
 * [inputs and outputs](https://docs.gradle.org/current/userguide/more_about_tasks.html#sec:task_inputs_outputs)
 * are properly defined so that we support incremental builds (and so that, for instance, the task re-runs
 * if/when the test at [getGeneratedTestPath] is deleted)
 */
open class GenerateJsonTestSuiteTask : DefaultTask() {
    private val jsonTestSuiteGenerator =
        JsonTestSuiteGenerator(
            jsonTestSuiteSHA,
            schemaTestSuiteSHA,
            project.projectDir.toPath(),
            Paths.get("src/commonTest/kotlin/"),
            "org.kson.parser.json.generated"
        )

    init {
        // ensure we're out of date when/if the repo of test source files is deleted
        outputs.upToDateWhen {
            jsonTestSuiteGenerator.jsonTestSuiteRootDir.toFile().exists()
                    && jsonTestSuiteGenerator.schemaTestSuiteRootDir.toFile().exists()
        }
    }

    /**
     * Register [JsonTestSuiteGenerator.jsonTestSuiteSHA] as an input to this script so that it is marked
     * "out of date" whenever the script changes and re-runs
     */
    @Input
    fun getTestSuiteSHA(): List<String> {
        return listOf(jsonTestSuiteGenerator.jsonTestSuiteSHA, jsonTestSuiteGenerator.schemaTestSuiteSHA)
    }

    @OutputFiles
    fun getGeneratedTestPath(): List<File> {
        return listOf(jsonTestSuiteGenerator.generatedJsonSuiteTestPath.toFile(),
            jsonTestSuiteGenerator.generatedSchemaSuiteTestPath.toFile())
    }

    @TaskAction
    fun generate() {
        jsonTestSuiteGenerator.generate()
    }

    @Internal
    override fun getDescription(): String? {
        return "Generates ${jsonTestSuiteGenerator.generatedJsonSuiteTestPath} and " +
                "${jsonTestSuiteGenerator.generatedSchemaSuiteTestPath}"
    }
}