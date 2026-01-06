package org.kson.value

import org.kson.KsonCore
import org.kson.KsonCoreTestError
import org.kson.parser.Location
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SubParseableTest: KsonCoreTestError {

    /**
     * Given a chunk of KSON source ([ksonSourceWithSRCTag]) which has a String that begins with "SRC", assert that
     * [org.kson.parser.Location] [embeddedSourceLocation] in [ksonSourceWithSRCTag] is translated to [expectedOriginalSourceLocation]
     * using the [SubParseable] interface of [KsonString]
     */
    fun assertSubLocationMessageLogged(ksonSourceWithSRCTag: String,
                                       embeddedSourceLocation: Location,
                                       expectedOriginalSourceLocation: Location
    ) {
        val parseResult = KsonCore.parseToAst(ksonSourceWithSRCTag)
        val ksonValue = parseResult.ksonValue
        assertNotNull(ksonValue)
        val embeddedSrcString = findEmbeddedSRC(ksonValue)
        assertNotNull(
            embeddedSrcString,
            "No embedded src found. Does ksonSource have a string that starts with SRC??"
        )
        assertEquals(
            expectedOriginalSourceLocation,
            embeddedSrcString.subOffsetLocation(embeddedSourceLocation.startOffset, embeddedSourceLocation.endOffset),
            "should properly map back to original source location using offsets"
        )

        assertEquals(
            expectedOriginalSourceLocation,
            embeddedSrcString.subCoordinatesLocation(
                embeddedSourceLocation.start.line,
                embeddedSourceLocation.start.column,
                embeddedSourceLocation.end.line,
                embeddedSourceLocation.end.column
            ),
            "should properly map back to original source location using line/column data"
        )
    }

    /**
     * Return the first found [KsonString] in the given [KsonValue] that starts with "SRC". This is a bit informal,
     * but it makes for an easy way for these tests to tag the string they would like to describe a sub-location of
     */
    private fun findEmbeddedSRC(ksonValue: KsonValue): KsonString? {
        when (ksonValue) {
            is EmbedBlock -> {
                return ksonValue.embedTag?.let { findEmbeddedSRC(it) } ?:
                    ksonValue.metadataTag?.let { findEmbeddedSRC(it) } ?:
                    findEmbeddedSRC(ksonValue.embedContent)
            }
            is KsonString -> {
                if (!ksonValue.value.startsWith("SRC")) {
                    return null
                }
                return ksonValue
            }
            is KsonObject -> {
                ksonValue.propertyMap.values.forEach {
                    val ksonString = findEmbeddedSRC(it.propName) ?:
                        findEmbeddedSRC(it.propValue)
                    if (ksonString != null) { return ksonString }
                }
                return null
            }
            is KsonList -> {
                ksonValue.elements.forEach {
                    val ksonString = findEmbeddedSRC(it)
                    if (ksonString != null) {
                        return ksonString
                    }
                }
                return null
            }
            is KsonNumber, is KsonBoolean, is KsonNull -> return null
        }
    }

    @Test
    fun testSubLocationInSimpleStrings() {
        val ksonUnquotedString = """
            key: SRCa_simple_string
        """.trimIndent()

        assertSubLocationMessageLogged(
            ksonUnquotedString,
            // location of "simple" in SRC-denoted KSON string
            Location.Companion.create(0, 5, 0, 11, 5, 11),
            // location of "simple" in original source
            Location.Companion.create(0, 10, 0, 16, 10, 16))

        val ksonQuotedPlainString = """
            key: 'SRCa plain quoted string'
        """.trimIndent()

        assertSubLocationMessageLogged(
            ksonQuotedPlainString,
            // location of " quoted " in SRC-denoted KSON string
            Location.Companion.create(0, 10, 0, 18, 10, 18),
            Location.Companion.create(0, 16, 0, 24, 16, 24))

        val ksonQuotedStringWithNewlines = """
            key: 'SRCa quoted string
            with newlines'
        """.trimIndent()

        assertSubLocationMessageLogged(
            ksonQuotedStringWithNewlines,
            // location of "ted string\nwith new" in SRC-denoted KSON string
            Location.Companion.create(0, 8, 1, 8, 8, 27),
            Location.Companion.create(0, 14, 1, 8, 14, 33))
    }

    @Test
    fun testSubLocationInStringWithEscapedQuotes() {
        val ksonStringWithEscapes = """
            key: 'SRCthis string has \'escaped\' quotes inside it'
        """.trimIndent()

        assertSubLocationMessageLogged(
            ksonStringWithEscapes,
            // location of "quotes" in the SRC-denoted KSON string
            Location.create(0, 29, 0, 35, 29, 35),
            Location.Companion.create(0, 37, 0, 43, 37, 43))
    }

    @Test
    fun testSubLocationInStringWithEscaping() {
        val ksonStringWithEscapes = """
            key: 'SRCthis string\t\n\t has escapes'
        """.trimIndent()

        assertSubLocationMessageLogged(
            ksonStringWithEscapes,
            // location of "g\t\n\t h" in the SRC-denoted KSON string
            Location.Companion.create(0, 13, 1, 3, 13, 19),
            Location.Companion.create(0, 19, 0, 28, 19, 28))
    }

    @Test
    fun testSubLocationInPlainEmbed() {
        val ksonPlainEmbed = """
            %
            this is a very simple embed, with no escapes or
            indent stripping.  It is equivalent to a string
            with newlines
            %%
        """.trimIndent()

        // TODO test this case
    }

    @Test
    fun testSubLocationInEmbedWithIndent() {
        val ksonIndentedEmbed = """
            key: %
                SRCthis is an indented simple embed, with no escapes
                for testing accuracy of sub-location generation
                %%
        """.trimIndent()

        // TODO test this case
    }

    @Test
    fun testSubLocationInEmbedWithEscapes() {
        val ksonIndentedEmbed = """
            %
            SRCthis is an indent-free embed block with escaped %\%
            embed delimiters %\\\\% for testing accuracy of
            sub-location generation
            %%
        """.trimIndent()

        // TODO test this case

        val ksonIndentedEmbedWithEscapes = """
            key: %
                this is an indented embed block with escaped %\%
                embed delimiters %\\\\% for testing accuracy of
                sub-location generation
                %%
        """.trimIndent()

        // TODO test this case
    }
}
