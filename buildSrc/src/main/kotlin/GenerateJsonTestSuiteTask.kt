import org.gradle.api.DefaultTask
import org.gradle.api.tasks.*
import org.kson.jsonsuite.JsonTestSuiteGenerator
import java.io.File
import java.nio.file.Paths

/**
 * This task exposes [JsonTestSuiteGenerator] to our Gradle build, ensuring the task's
 * [inputs and outputs](https://docs.gradle.org/current/userguide/more_about_tasks.html#sec:task_inputs_outputs)
 * are properly defined so that we support incremental builds (and so that, for instance,
 * the test at [getGeneratedTestPath] is deleted)
 */
open class GenerateJsonTestSuiteTask : DefaultTask() {
    private val jsonTestSuiteGenerator =
        JsonTestSuiteGenerator(project.projectDir.toPath(), Paths.get("src/commonTest/kotlin/"), "org.kson.parser.json.generated")

    init {
        outputs.upToDateWhen {
            // ensure we're out of date when/if the repo of test source files is deleted
            jsonTestSuiteGenerator.testSuiteRootDir.toFile().exists()
        }
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