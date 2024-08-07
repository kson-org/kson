package org.kson.jsonsuite

import kotlin.test.*
import org.kson.GitOps

class JsonTestSuiteGeneratorTest {
    @Test
    fun testPlaceholder() {
        /**
         * Placeholder test to hold this rationale for the lack of test coverage around [JsonTestSuiteGenerator]:
         *
         * [JsonTestSuiteGenerator] is difficult to test because it needs to directly manipulate files in this project.
         * Much of the non-trivial work it needs has been factored into [GitOps], which is tested by [GitOpsTest].
         * TODO: if/when the need arises, further factor [JsonTestSuiteGenerator] to accommodate testing
         *
         * Also note that [GenerateJsonTestSuiteTaskTest.sanityCheckTask] exercises the bits in [JsonTestSuiteGenerator]
         * using the scaffolding provided by Gradle, so we are not entirely uncovered here
         */
        assert(true)
    }
}