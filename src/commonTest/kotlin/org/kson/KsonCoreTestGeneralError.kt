package org.kson

import org.kson.KsonCoreTest.CompileSettings
import org.kson.parser.Location
import org.kson.parser.messages.MessageType.*
import org.kson.schema.JsonBooleanSchema
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Tests for general/mixed Kson values that don't fit neatly into the other [KsonCoreTestError] tests
 */
class KsonCoreTestGeneralError: KsonCoreTestError {
    @Test
    fun testBlankKsonSource() {
        assertParserRejectsSource("", listOf(BLANK_SOURCE))
        assertParserRejectsSource("  ", listOf(BLANK_SOURCE))
        assertParserRejectsSource("\t", listOf(BLANK_SOURCE))
    }

    /**
     * Regression test for an issue where we would throw an exception trying
     * to validate invalid Kson against a schema
     */
    @Test
    fun testSchemaValidationForIllegalKson() {
        KsonCore.parseToAst("""
            - 
        """.trimIndent(),
            CoreCompileConfig(schemaJson = JsonBooleanSchema(true))
        )
    }

    @Test
    fun testIllegalCharacterError() {
        assertParserRejectsSource("key: \\value", listOf(ILLEGAL_CHARACTERS))
    }

    @Test
    fun testIllegalMinusSignError() {
        assertParserRejectsSource(
            """
                -nope
            """.trimIndent(),
            listOf(ILLEGAL_MINUS_SIGN)
        )
    }

    @Test
    fun testInvalidTrailingKson() {
        assertParserRejectsSource("[1] illegal_key: illegal_value", listOf(EOF_NOT_REACHED))
        assertParserRejectsSourceWithLocation(
            "{ key: value } 4.5",
            listOf(EOF_NOT_REACHED),
            listOf(Location.create(0, 15, 0, 18, 15, 18))
        )
        assertParserRejectsSource("key: value illegal extra identifiers", listOf(EOF_NOT_REACHED))
    }

    @Test
    fun testOnlyUnexpectedContent() {
        assertParserRejectsSource(",K", listOf(ONLY_UNEXPECTED_CONTENT))
    }

    @Test
    fun testEmptyCommas() {
        assertParserRejectsSource("[,]", listOf(EMPTY_COMMAS))
        assertParserRejectsSource("{,}", listOf(EMPTY_COMMAS))

        assertParserRejectsSource("[,,]", listOf(EMPTY_COMMAS, EMPTY_COMMAS))
        assertParserRejectsSource("{,,}", listOf(EMPTY_COMMAS, EMPTY_COMMAS))

        assertParserRejectsSource("[1,,3]", listOf(EMPTY_COMMAS))
        assertParserRejectsSource("{one: 1 ,, three: 3}", listOf(EMPTY_COMMAS))

        assertParserRejectsSource("[1,2,3,,,,,,]", listOf(EMPTY_COMMAS))
        assertParserRejectsSource("{one: 1, two: 2, three: 3,,,,,,}", listOf(EMPTY_COMMAS))

        assertParserRejectsSource("[,,,, x ,, y ,,,,,,, z ,,,,]", listOf(EMPTY_COMMAS, EMPTY_COMMAS, EMPTY_COMMAS, EMPTY_COMMAS))
    }

    @Test
    fun testIllegalMixedListAndObjectNesting() {
        // test nesting a mix of objects, bracket lists, and dashed lists
        assertParserRejectsSource("""
            [
              { 
                 a: - { 
                        b: [ { c: - [] } ]
                      }
              }
            ]
        """.trimIndent(), listOf(MAX_NESTING_LEVEL_EXCEEDED), 7)
    }

    @Test
    fun testParseTrailingContentRegression() {
        val source = """
            "outer_key": 42
              }
            """.trimIndent()

        val ksonResult = KsonCore.parseToKson(source, CompileSettings().ksonSettings)
        assertNull(ksonResult.kson)

        val jsonResult = KsonCore.parseToJson(source, CompileSettings().jsonSettings)
        assertNull(jsonResult.json)

        val yamlResult = KsonCore.parseToYaml(source, CompileSettings().yamlSettings)
        assertNull(yamlResult.yaml)
    }

    @Test
    fun testKsonValueReturnsNullForErrorTolerantParseWithDeepErrors() {
        // With ignoreErrors=true, the parser produces an AST with error nodes
        // deeper inside (not at the root). ksonValue should return null rather
        // than throw when toKsonValue() encounters these error nodes.
        val result = KsonCore.parseToAst(
            """{"key": , "other": 42}""",
            CoreCompileConfig(ignoreErrors = true)
        )
        // The root is valid (KsonRootImpl) and hasErrors() returns false because
        // error-walking is skipped — this is the exact gap ksonValue must handle.
        assertFalse(result.hasErrors(), "hasErrors() should be false with ignoreErrors=true")
        assertNull(
            result.ksonValue,
            "ksonValue should return null when the AST contains deep error nodes"
        )
    }

    @Test
    fun testGapFreeAndStrictParsingProduceIdenticalLocations() {
        // The KsonBuilder.done() walk-back ensures gap-free (error-tolerant)
        // and strict parsing produce identical AST node locations, even when
        // trailing whitespace would otherwise extend the gap-free range.
        val source = """
            {
              "name": "Alice",
              "address": {
                "city": "Portland"
              }
            }
        """.trimIndent()

        val strict = KsonCore.parseToAst(source)
        val gapFree = KsonCore.parseToAst(source, CoreCompileConfig(ignoreErrors = true))

        val strictValue = assertNotNull(strict.ksonValue, "strict parse should produce a value")
        val gapFreeValue = assertNotNull(gapFree.ksonValue, "gap-free parse should produce a value")

        // Root object locations must match
        assertEquals(
            strictValue.location, gapFreeValue.location,
            "Root object locations should be identical"
        )

        // Nested object locations must match (this is where trailing whitespace
        // previously caused gap-free end positions to extend to the next line)
        val strictAddress = (strictValue as org.kson.value.KsonObject).propertyMap["address"]!!.propValue
        val gapFreeAddress = (gapFreeValue as org.kson.value.KsonObject).propertyMap["address"]!!.propValue
        assertEquals(
            strictAddress.location, gapFreeAddress.location,
            "Nested 'address' object locations should be identical"
        )
    }

    @Test
    fun testGapFreeAndStrictLocationsMatchWithTrailingComments() {
        // Trailing comments produce WHITESPACE + COMMENT lookahead tokens in gap-free mode.
        // The walk-back in KsonBuilder.done() must skip past these (in addition to plain
        // trailing WHITESPACE) so that gap-free end positions still match strict positions.
        val source = """
            {
              "name": "Alice", # inline comment
              "address": {
                "city": "Portland" # another comment
              } # closing comment
            }
        """.trimIndent()

        val strict = KsonCore.parseToAst(source)
        val gapFree = KsonCore.parseToAst(source, CoreCompileConfig(ignoreErrors = true))

        val strictValue = assertNotNull(strict.ksonValue, "strict parse should produce a value")
        val gapFreeValue = assertNotNull(gapFree.ksonValue, "gap-free parse should produce a value")

        assertEquals(
            strictValue.location, gapFreeValue.location,
            "Root object locations should be identical with trailing comments"
        )

        val strictAddress = (strictValue as org.kson.value.KsonObject).propertyMap["address"]!!.propValue
        val gapFreeAddress = (gapFreeValue as org.kson.value.KsonObject).propertyMap["address"]!!.propValue
        assertEquals(
            strictAddress.location, gapFreeAddress.location,
            "Nested 'address' object locations should be identical with trailing comments"
        )
    }
}
