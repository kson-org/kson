package org.kson

import kotlin.test.Test

class KsonTestString : KsonTest() {
    @Test
    fun testStringLiteralSource() {
        assertParsesTo(
            """
                "This is a string"
            """,
            "\"This is a string\"",
            "\"This is a string\"",
            "\"This is a string\""
        )
    }

    @Test
    fun testEmptyString() {
        assertParsesTo("''", "\"\"", "\"\"", "\"\"")
    }
}
