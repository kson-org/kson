import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertTrue
import org.kson.jsonsuite.JsonTestSuiteGenerator
import org.kson.jsonsuite.JsonTestSuiteGeneratorTest

/**
 * Note: most of the meat of [GenerateJsonTestSuiteTask]'s implementation lives in
 * [JsonTestSuiteGenerator], so the bulk of the tests for this task like in
 * [JsonTestSuiteGeneratorTest]
 */
class GenerateJsonTestSuiteTaskTest {
    /**
     * Basic sanity check test based on the [equally basic example from the docs](https://docs.gradle.org/current/userguide/custom_tasks.html#sec:writing_tests_for_your_task_class)
     */
    @Test
    fun sanityCheckTask() {
        val project = ProjectBuilder.builder().build()
        project.tasks.register("generateJsonTestSuite", GenerateJsonTestSuiteTask::class.java)
        val jsonGenTask = project.getTasksByName("generateJsonTestSuite", false)
            .iterator().next()
        assertTrue(jsonGenTask is GenerateJsonTestSuiteTask)

        assertTrue(
            jsonGenTask.getGeneratedClassDirectory().startsWith(project.projectDir),
            "Should set the output test file path relative to the project directory"
        )
    }
}
