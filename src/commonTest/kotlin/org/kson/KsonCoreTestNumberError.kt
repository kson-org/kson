package org.kson

import org.kson.parser.messages.MessageType.*
import kotlin.test.Test

class KsonCoreTestNumberError : KsonCoreTestError {
    @Test
    fun testDanglingExponentError() {
        assertParserRejectsSource(
            """
                420E
            """,
            listOf(DANGLING_EXP_INDICATOR)
        )

        assertParserRejectsSource(
            """
                420E-
            """,
            listOf(DANGLING_EXP_INDICATOR)
        )
    }
}
