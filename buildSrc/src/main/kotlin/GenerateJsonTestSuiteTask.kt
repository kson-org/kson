import org.gradle.api.DefaultTask
import org.gradle.api.tasks.*
import org.kson.jsonsuite.JsonTestSuiteGenerator
import java.io.File
import java.nio.file.Paths

/**
 * The Git SHA in the [JSONTestSuite](https://github.com/nst/JSONTestSuite) we currently test against.
 * This can be updated if/when we want to pull in newer tests from that project.
 */
private const val testSuiteSHA = "d64aefb55228d9584d3e5b2433f720ea8fd00c82"

/**
 * This task exposes [JsonTestSuiteGenerator] to our Gradle build, ensuring the task's
 * [inputs and outputs](https://docs.gradle.org/current/userguide/more_about_tasks.html#sec:task_inputs_outputs)
 * are properly defined so that we support incremental builds (and so that, for instance, the task re-runs
 * if/when the test at [getGeneratedTestPath] is deleted)
 */
open class GenerateJsonTestSuiteTask : DefaultTask() {
    private val jsonTestSuiteGenerator =
        JsonTestSuiteGenerator(
            testSuiteSHA,
            project.projectDir.toPath(),
            Paths.get("src/commonTest/kotlin/"),
            "org.kson.parser.json.generated"
        )

    init {
        // ensure we're out of date when/if the repo of test source files is deleted
        outputs.upToDateWhen {
            jsonTestSuiteGenerator.testSuiteRootDir.toFile().exists()
        }
    }

    /**
     * Register [JsonTestSuiteGenerator.jsonTestSuiteSHA] as an input to this script so that it is marked
     * "out of date" whenever the script changes and re-runs
     */
    @Input
    fun getTestSuiteSHA(): String {
        return jsonTestSuiteGenerator.jsonTestSuiteSHA
    }

    @OutputFiles
    fun getGeneratedTestPath(): List<File> {
        return listOf(jsonTestSuiteGenerator.generatedTestPath.toFile())
    }

    @TaskAction
    fun generate() {
        jsonTestSuiteGenerator.generate()
    }

    @Internal
    override fun getDescription(): String? {
        return "Generates src/commonTest/kotlin/org/kson/parser/json/generated/JsonSuiteTest.kt"
    }
}