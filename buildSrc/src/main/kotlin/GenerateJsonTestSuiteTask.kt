import org.gradle.api.DefaultTask
import org.gradle.api.tasks.*
import org.kson.jsonsuite.JsonSuiteGitCheckout
import org.kson.jsonsuite.JsonTestSuiteGenerator
import org.kson.jsonsuite.SchemaSuiteGitCheckout
import java.io.File

/**
 * The Git SHAs in [JSONTestSuite](https://github.com/nst/JSONTestSuite) and [JSON-Schema-Test-Suite](https://github.com/json-schema-org/JSON-Schema-Test-Suite)
 * that we currently test against.
 *
 * These can be updated if/when we want to pull in newer tests from those projects.
 */
const val jsonTestSuiteSHA = "984defc2deaa653cb73cd29f4144a720ec9efe7c"
const val schemaTestSuiteSHA = "9fc880bfb6d8ccd093bc82431f17d13681ffae8e"

/**
 * This task exposes [JsonTestSuiteGenerator] to our Gradle build, ensuring the task's
 * [inputs and outputs](https://docs.gradle.org/current/userguide/more_about_tasks.html#sec:task_inputs_outputs)
 * are properly defined so that we support incremental builds (and so that, for instance, the task re-runs
 * if/when the test at [getGeneratedTestPath] is deleted)
 */
open class GenerateJsonTestSuiteTask : DefaultTask() {
    private val jsonTestSuiteGenerator: JsonTestSuiteGenerator

    init {
        val projectRoot = project.projectDir.toPath()
        val destinationDir = projectRoot.resolve("buildSrc").resolve("support/jsonsuite")

        val sourceRoot = projectRoot.resolve("src/commonTest/kotlin/")

        val jsonSuiteGitCheckout = JsonSuiteGitCheckout(jsonTestSuiteSHA, destinationDir)
        val schemaSuiteGitCheckout = SchemaSuiteGitCheckout(schemaTestSuiteSHA, destinationDir)

        jsonTestSuiteGenerator = JsonTestSuiteGenerator(
            jsonSuiteGitCheckout,
            schemaSuiteGitCheckout,
            projectRoot,
            sourceRoot,
            "org.kson.parser.json.generated"
        )

        // ensure we're out of date when/if the repo of test source files is deleted
        outputs.upToDateWhen {
            jsonSuiteGitCheckout.checkoutDir.exists()
                    && schemaSuiteGitCheckout.checkoutDir.exists()
        }
    }

    @OutputDirectory
    fun getGeneratedClassDirectory(): File {
        return jsonTestSuiteGenerator.testClassPackageDir.toFile()
    }

    @TaskAction
    fun generate() {
        jsonTestSuiteGenerator.generate()
    }

    @Internal
    override fun getDescription(): String? {
        return "Generates the JSON Test files in ${jsonTestSuiteGenerator.testClassPackageDir}"
    }
}
