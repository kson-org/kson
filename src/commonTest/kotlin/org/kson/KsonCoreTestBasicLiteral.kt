package org.kson

import kotlin.test.Test

class KsonCoreTestBasicLiteral : KsonCoreTest {
    @Test
    fun testBooleanLiteralSource() {
        assertParsesTo(
            """
                true
            """,
            "true",
            "true",
            "true"
        )

        assertParsesTo(
            """
                false
            """,
            "false",
            "false",
            "false"
        )
    }

    @Test
    fun testNullLiteralSource() {
        assertParsesTo(
            """
                null
            """,
            "null",
            "null",
            "null"
        )
    }
} 
