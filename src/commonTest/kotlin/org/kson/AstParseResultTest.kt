package org.kson

import org.kson.value.KsonValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests the contract of [AstParseResult.ksonValue]: when a parse yields `null`, and what a
 * caller can learn from [AstParseResult.messages] about why.
 */
class AstParseResultTest {

    /**
     * Sources chosen to break parsing in as many ways as we can think of, so the contract is
     * exercised against real failures rather than one representative of them
     */
    private val brokenSources = listOf(
        "",
        "   \n\n  ",
        "# only a comment",
        "{",
        "{ a: }",
        "key:",
        "-",
        ".",
        "<",
        "[",
        "key: \"unterminated",
        "%%%\nunclosed embed",
        "{ a: 1 } trailing",
        "{ a: { b: { c: { d: } } } }",
        "a: ".repeat(600) + "1",
        "- ".repeat(600) + "1"
    )

    /**
     * A parse that checked for errors withholds its value exactly when it found errors.  Callers
     * lean on both directions of this: no value means there is something in [AstParseResult.messages]
     * to report, and no errors means the value is there to use.
     */
    @Test
    fun testErrorCheckedParseWithholdsItsValueExactlyWhenItFoundErrors() {
        for (source in brokenSources) {
            val parseResult = KsonCore.parseToAst(source)
            assertEquals(
                parseResult.hasErrors(),
                parseResult.ksonValue == null,
                "a checked parse has no value exactly when it has errors: \"$source\""
            )
        }
    }

    /**
     * [AstParseResult.hasErrors] is also true for an error root the parser built without logging
     * anything, so this pins the part callers actually need: an absent value is always accompanied
     * by something they can show a user.
     */
    @Test
    fun testErrorCheckedParseExplainsEveryAbsentValue() {
        for (source in brokenSources) {
            val parseResult = KsonCore.parseToAst(source)
            if (parseResult.ksonValue == null) {
                assertTrue(
                    parseResult.messages.isNotEmpty(),
                    "a checked parse with no value must say why in `messages`: \"$source\""
                )
            }
        }
    }

    /**
     * Warnings mean we successfully parsed, so we definitely expect to get a value back
     */
    @Test
    fun testWarningsDoNotSuppressTheValue() {
        val duplicateKey = KsonCore.parseToAst("{ a: 1, a: 2 }")
        assertNotNull(duplicateKey.ksonValue, "a duplicate key is a warning, not a failure to parse")
        assertTrue(duplicateKey.messages.isNotEmpty(), "the duplicate key should still be reported")
    }

    /**
     * An error-tolerant parse may return an AST that cannot be represented as a [org.kson.value.KsonValue],
     * but nothing prevents a caller from asking for a [org.kson.value.KsonValue] anyway. They should get a
     * value or `null` if/when they try, never an exception.
     */
    @Test
    fun testErrorTolerantParseSurvivesEveryBrokenSource() {
        for (source in brokenSources) {
            val ignoreErrorsKsonValue = KsonCore.parseToAst(source, CoreCompileConfig(ignoreErrors = true)).ksonValue
            assertTrue(ignoreErrorsKsonValue is KsonValue || ignoreErrorsKsonValue == null)
        }
    }

    /**
     * An error-tolerant parse never looked for problems, so unlike a checked parse it can come back
     * with no value, but no messages explaining why.  Callers who need an explanation must parse
     * with error checking to get one.
     */
    @Test
    fun testErrorTolerantParseNeedNotExplainAnAbsentValue() {
        val parseResult = KsonCore.parseToAst("key:", CoreCompileConfig(ignoreErrors = true))
        assertNull(parseResult.ksonValue)
        assertEquals(emptyList(), parseResult.messages)
    }
}
