package org.kson.parser.behavior.embedblock

import kotlin.test.Test
import kotlin.test.assertEquals

class EmbedDelimTest {
    /**
     * Regression test for the case where a trailing slash would be incorrectly escaped
     */
    @Test
    fun testEscapeEmbedContentAtBoundary() {
        assertEquals("""
                %\%\n
            """.trimIndent(),
            EmbedDelim.Percent.escapeEmbedContent("""
                %%\n
            """.trimIndent()))
    }

    @Test
    fun testEscapeSubsequentEmbedDelims() {
        assertEquals("""
                %\%%\%
            """.trimIndent(),
            EmbedDelim.Percent.escapeEmbedContent("""
                %%%%
            """.trimIndent()),
            "should detect the first and second embed-ends without double-counting")

        assertEquals("""
                %\\%\%\\%
            """.trimIndent(),
            EmbedDelim.Percent.escapeEmbedContent("""
                %\%\%\%
            """.trimIndent()),
            "should detect the first and second embed-ends without double-counting")
    }

    @Test
    fun testEscapeEmbedContentForMultipleDelims() {
        /**
         * Test a noisy and subtle case: this should only add an escaping slash
         * between the first two %s, and then the second two %s.  All other slashes
         * are NOT %-delim escapes
         */
        assertEquals(
            """
                \%\\\\%\%\\%\\n
            """.trimIndent(),
            EmbedDelim.Percent.escapeEmbedContent("""
                \%\\\%\%\%\\n
            """.trimIndent()))
    }

    @Test
    fun testUnescapeDelimWithTrailingSlash() {
        assertEquals(
            """
                %%\n
            """.trimIndent(),
            EmbedDelim.Percent.unescapeEmbedContent("""
                %\%\n
            """.trimIndent()))
    }
}
