import org.gradle.api.DefaultTask
import org.gradle.api.tasks.*
import org.kson.jsonsuite.JsonSuiteGitCheckout
import org.kson.jsonsuite.JsonTestSuiteGenerator
import org.kson.jsonsuite.SchemaSuiteGitCheckout
import java.io.File
import java.nio.file.Path

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
 * if/when the generated tests at [getGeneratedClassDirectory] are deleted)
 */
open class GenerateJsonTestSuiteTask : DefaultTask() {
    private val projectRoot: Path = project.projectDir.toPath()
    private val sourceRoot: Path = projectRoot.resolve("src/commonTest/kotlin/")
    private val classPackage = "org.kson.parser.json.generated"

    // output dir depends only on the source root + package, not on the (lazily cloned) test-suite checkout
    @OutputDirectory
    fun getGeneratedClassDirectory(): File {
        return sourceRoot.resolve(classPackage.replace('.', '/')).toFile()
    }

    @TaskAction
    fun generate() {
        // construct the checkouts here (not in init) so the clone only happens when this task actually runs
        val destinationDir = projectRoot.resolve("buildSrc").resolve("support/jsonsuite")
        val jsonSuiteGitCheckout = JsonSuiteGitCheckout(jsonTestSuiteSHA, destinationDir)
        val schemaSuiteGitCheckout = SchemaSuiteGitCheckout(schemaTestSuiteSHA, destinationDir)
        JsonTestSuiteGenerator(
            jsonSuiteGitCheckout,
            schemaSuiteGitCheckout,
            projectRoot,
            sourceRoot,
            classPackage
        ).generate()
    }

    @Internal
    override fun getDescription(): String? {
        return "Generates the JSON Test files in ${getGeneratedClassDirectory()}"
    }
}
