import org.gradle.testfixtures.ProjectBuilder
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Note: most of the meat of  [GenerateJsonTestSuiteTask]'s implementation lives in
 * [org.kson.jsonsuite.JsonTestSuiteGenerator], and is tested more effectively in
 * [org.kson.jsonsuite.JsonTestSuiteGeneratorTest]
 */
class GenerateJsonTestSuiteTaskTest {
    /**
     * Basic sanity check test based on the equally [anemic example from the docs](https://docs.gradle.org/current/userguide/custom_tasks.html#sec:writing_tests_for_your_task_class)
     * See [org.kson.jsonsuite.JsonTestSuiteGeneratorTest] for more effective testing of the underlying code
     */
    @Test
    fun sanityCheckTask() {
        val project = ProjectBuilder.builder().build()
        project.tasks.register("generateJsonTestSuite", GenerateJsonTestSuiteTask::class.java)
        val jsonGenTask = project.getTasksByName("generateJsonTestSuite", false)
            .iterator().next()
        assertTrue(jsonGenTask is GenerateJsonTestSuiteTask)
    }
}