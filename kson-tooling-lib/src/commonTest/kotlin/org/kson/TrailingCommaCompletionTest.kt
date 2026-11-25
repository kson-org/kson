package org.kson

import org.kson.tooling.KsonTooling
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Completion where the next key goes after `key: value,`, the usual place to add a key to formatted JSON.  Whatever
 * the value and however deep its object, once the caret is past the comma, touching it or not, the object's remaining
 * keys are offered.
 */
class TrailingCommaCompletionTest {

    private enum class Syntax(val key: (String) -> String) {
        JSON({ "\"$it\"" }),
        KSON({ it })
    }

    private val valueKinds = listOf("string", "number", "boolean", "null", "object", "array")

    /**
     * The object at [depth] under `level1` … `level4`: a key for each kind of value and one for the next level, all
     * named after [depth], so a caret resolved to another object is offered another depth's keys.
     */
    private fun levelSchema(depth: Int): String {
        val keys = valueKinds.map { "\"$it$depth\": { \"type\": \"$it\" }" } +
            if (depth < MAX_DEPTH) listOf("\"level${depth + 1}\": ${levelSchema(depth + 1)}") else emptyList()
        return """{ "type": "object", "properties": { ${keys.joinToString(", ")} } }"""
    }

    private val schema = KsonTooling.parse(levelSchema(0))

    private fun keysAt(depth: Int): Set<String> =
        valueKinds.map { "$it$depth" }.toSet() + if (depth < MAX_DEPTH) setOf("level${depth + 1}") else emptySet()

    /** A document whose object at [depth] is [innermost], nested in braced objects */
    private fun document(syntax: Syntax, depth: Int, innermost: String): String =
        (depth downTo 1).fold(innermost) { inner, level -> "{\n${syntax.key("level$level")}: $inner\n}" }

    /** The object holding `key: value,`, with the caret where its next key goes */
    private val caretPlacements: List<Pair<String, (String) -> String>> = listOf(
        "on the next line, at its end" to { property -> "{\n  $property,\n  <caret>\n}" },
        "on the next line, before trailing spaces" to { property -> "{\n  $property,\n  <caret>   \n}" },
        "on the next line, directly before the brace" to { property -> "{\n  $property,\n<caret>}" },
        "on the same line, before a space and the brace" to { property -> "{ $property, <caret> }" },
        "on the same line, directly before the brace" to { property -> "{ $property, <caret>}" },
        "right after the comma, at the end of its line" to { property -> "{\n  $property,<caret>\n}" },
        "right after the comma, before trailing spaces" to { property -> "{\n  $property,<caret>   \n}" },
        "right after the comma, before a space and the brace" to { property -> "{ $property,<caret> }" },
        "right after the comma, directly before the brace" to { property -> "{ $property,<caret>}" }
    )

    private fun labelsAtCaret(documentWithCaret: String): Set<String> {
        val caretIndex = documentWithCaret.indexOf("<caret>")
        require(caretIndex >= 0) { "Document must contain a <caret> marker" }
        val beforeCaret = documentWithCaret.take(caretIndex)
        val line = beforeCaret.count { it == '\n' }
        val column = caretIndex - (beforeCaret.lastIndexOf('\n') + 1)
        val document = KsonTooling.parse(documentWithCaret.replace("<caret>", ""))
        return KsonTooling.getCompletionsAtLocation(document, schema, line, column).map { it.label }.toSet()
    }

    /**
     * After each of [values] of kind [kind], in the object at every depth and at every caret placement, the object's
     * keys other than the one just written are offered.  Lists every case that is not, rather than stopping at the first.
     */
    private fun assertRemainingKeysOffered(syntax: Syntax, kind: String, vararg values: String) {
        val wrong = values.flatMap { value ->
            (0..MAX_DEPTH).flatMap { depth ->
                val key = "$kind$depth"
                val expected = keysAt(depth) - key
                caretPlacements.mapNotNull { (placement, objectWith) ->
                    val documentWithCaret = document(syntax, depth, objectWith("${syntax.key(key)}: $value"))
                    val offered = labelsAtCaret(documentWithCaret)
                    if (offered == expected) null
                    else "$syntax $value at depth $depth, caret $placement: offered $offered, expected $expected"
                }
            }
        }
        assertEquals(emptyList(), wrong)
    }

    @Test
    fun afterAStringInJson() = assertRemainingKeysOffered(Syntax.JSON, "string", "\"primary\"", "\"\"")

    @Test
    fun afterANumberInJson() = assertRemainingKeysOffered(Syntax.JSON, "number", "3", "-0.5")

    @Test
    fun afterABooleanInJson() = assertRemainingKeysOffered(Syntax.JSON, "boolean", "true", "false")

    @Test
    fun afterNullInJson() = assertRemainingKeysOffered(Syntax.JSON, "null", "null")

    @Test
    fun afterAnObjectInJson() = assertRemainingKeysOffered(Syntax.JSON, "object", "{\"x\": 1}", "{}")

    /** The `}` ends an object whose last value is still empty, so the next key goes in the object around it */
    @Test
    fun afterAnObjectWithAnEmptyValueInJson() = assertRemainingKeysOffered(Syntax.JSON, "object", "{\"x\": }")

    @Test
    fun afterAnArrayInJson() = assertRemainingKeysOffered(Syntax.JSON, "array", "[1, 2]", "[]")

    @Test
    fun afterAStringInKson() = assertRemainingKeysOffered(Syntax.KSON, "string", "'primary'", "\"primary\"", "''", "primary")

    @Test
    fun afterANumberInKson() = assertRemainingKeysOffered(Syntax.KSON, "number", "3", "-0.5")

    @Test
    fun afterABooleanInKson() = assertRemainingKeysOffered(Syntax.KSON, "boolean", "true", "false")

    @Test
    fun afterNullInKson() = assertRemainingKeysOffered(Syntax.KSON, "null", "null")

    @Test
    fun afterAnObjectInKson() = assertRemainingKeysOffered(Syntax.KSON, "object", "{x: 1}", "{}")

    @Test
    fun afterAnObjectWithAnEmptyValueInKson() = assertRemainingKeysOffered(Syntax.KSON, "object", "{x: }")

    @Test
    fun afterAnArrayInKson() = assertRemainingKeysOffered(Syntax.KSON, "array", "[1, 2]", "[]")

    @Test
    fun betweenAStringAndTheNextKey() {
        val expected = keysAt(2) - "string2" - "number2"
        for (documentWithCaret in listOf(
            "{\"level1\": {\"level2\": {\n  \"string2\": \"primary\",\n  <caret>\n  \"number2\": 3\n}}}",
            "{\"level1\": {\"level2\": {\n  \"string2\": \"primary\",\n<caret>  \"number2\": 3\n}}}",
            "level1: {level2: {\n  string2: 'primary',\n  <caret>\n  number2: 3\n}}",
            "level1: {level2: {\n  string2: 'primary',\n<caret>  number2: 3\n}}"
        )) {
            assertEquals(expected, labelsAtCaret(documentWithCaret), documentWithCaret)
        }
    }

    /** The comma need not touch the value it follows */
    @Test
    fun whenWhitespaceOrACommentSeparatesTheValueFromItsComma() {
        for ((key, documentWithCaret) in listOf(
            "number2" to "{\"level1\": {\"level2\": {\n  \"number2\": 3 ,\n  <caret>\n}}}",
            "string2" to "{\"level1\": {\"level2\": {\n  \"string2\": \"primary\" ,\n  <caret>\n}}}",
            "string2" to "level1: {level2: {\n  string2: 'primary' # chosen\n  ,\n  <caret>\n}}"
        )) {
            assertEquals(keysAt(2) - key, labelsAtCaret(documentWithCaret), documentWithCaret)
        }
    }

    /** Before its comma an unquoted value may still be mid-typing, as `key: value|` is, so its values are offered */
    @Test
    fun aCaretRightAfterAnUnquotedValueStillCompletesItBeforeItsComma() {
        for (documentWithCaret in listOf(
            "{\"level1\": {\"boolean1\": tr<caret>,\n}}",
            "level1: {boolean1: true<caret>, }"
        )) {
            assertEquals(setOf("true", "false"), labelsAtCaret(documentWithCaret), documentWithCaret)
        }
    }

    /** A closer holds nothing to type, so right after an object or list, before its comma, the caret is past it */
    @Test
    fun aCaretBetweenACloserAndItsCommaCompletesTheObjectAroundIt() {
        for ((key, documentWithCaret) in listOf(
            "object1" to "{\"level1\": {\"object1\": {\"x\": 1}<caret>,\n}}",
            "object1" to "{\"level1\": {\"object1\": {\"x\": }<caret>,\n}}",
            "array1" to "{\"level1\": {\"array1\": [1, 2]<caret>,\n}}",
            "object1" to "level1: {object1: {x: 1}<caret>, }"
        )) {
            assertEquals(keysAt(1) - key, labelsAtCaret(documentWithCaret), documentWithCaret)
        }
    }

    /** Before its comma a committed string is finished, as `key: 'value'|` is, so nothing is offered */
    @Test
    fun aCaretBetweenACommittedStringAndItsCommaOffersNothing() {
        for (documentWithCaret in listOf(
            "{\"level1\": {\"string1\": \"primary\"<caret>,\n}}",
            "level1: {string1: 'primary'<caret>, }",
            "level1:\n  string1: 'primary'<caret>,\n  number1: 3"
        )) {
            assertEquals(emptySet(), labelsAtCaret(documentWithCaret), documentWithCaret)
        }
    }

    private companion object {
        const val MAX_DEPTH = 4
    }
}
