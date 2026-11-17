package org.kson.value.navigation

import org.kson.parser.messages.MessageType.JSON_POINTER_INCOMPLETE_ESCAPE
import org.kson.parser.messages.MessageType.JSON_POINTER_INVALID_ESCAPE
import org.kson.uri.UriReference
import org.kson.value.navigation.json_pointer.FragmentPointer
import org.kson.value.navigation.json_pointer.fragmentPointer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class FragmentPointerTest {

    private fun pointerOf(reference: String) = UriReference.parse(reference).fragmentPointer()

    private fun tokensOf(reference: String) = assertIs<FragmentPointer.Valid>(pointerOf(reference)).pointer.tokens

    @Test
    fun readsAPointerFragment() {
        assertEquals(listOf($$"$defs", "a"), tokensOf($$"#/$defs/a"))
        assertEquals(listOf($$"$defs", "a"), tokensOf($$"other.json#/$defs/a"))
        assertEquals(emptyList(), tokensOf("#"))
    }

    @Test
    fun percentDecodesAPointerFragmentBeforeParsingIt() {
        val tokens = listOf(
            $$"#/$defs/a%7E0b" to listOf($$"$defs", "a~b"),
            $$"#/$defs/100%25" to listOf($$"$defs", "100%"),
            $$"#/$defs/caf%C3%A9" to listOf($$"$defs", "café"),
            $$"#/$defs/a%2Fb" to listOf($$"$defs", "a", "b"),
        )
        assertEquals(tokens, tokens.map { (reference, _) -> reference to tokensOf(reference) })
    }

    @Test
    fun returnsWhatMakesAPointerMalformed() {
        val malformations = listOf(
            $$"#/$defs/a~2b" to FragmentPointer.Malformed(JSON_POINTER_INVALID_ESCAPE.create("2")),
            $$"#/$defs/a~" to FragmentPointer.Malformed(JSON_POINTER_INCOMPLETE_ESCAPE.create()),
            $$"#/$defs/a%7E2b" to FragmentPointer.Malformed(JSON_POINTER_INVALID_ESCAPE.create("2")),
        )
        assertEquals(malformations, malformations.map { (reference, _) -> reference to pointerOf(reference) })
    }

    @Test
    fun findsNoPointerInAPlainNameFragment() {
        assertNull(pointerOf("#foo"))
        assertNull(pointerOf("other.json#foo"))
    }

    @Test
    fun findsNoPointerWithoutAFragment() {
        assertNull(pointerOf("other.json"))
    }
}
