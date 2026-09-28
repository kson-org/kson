package org.kson.schema.validators

import org.kson.parser.messages.MessageType.SCHEMA_OBJECT_TOO_MANY_PROPERTIES
import org.kson.parser.messages.MessageType.SCHEMA_VALUE_TYPE_MISMATCH
import org.kson.schema.JsonSchemaTest
import org.kson.validation.SourceContext
import org.kson.validation.ValidationMode
import kotlin.test.Test

/**
 * Partial validation skips `required`, so an `if` requiring a property the value lacks holds under it,
 * while full validation fails it and takes the `else`.  Whatever full validation accepts, partial
 * validation must accept too.
 */
class IfValidatorTest : JsonSchemaTest {
    private val partial = SourceContext(mode = ValidationMode.PARTIAL)

    private val kindPicksA = """
        {
          "if": { "required": ["kind"] },
          "then": { "maxProperties": 0 },
          "else": { "properties": { "a": { "type": "integer" } } }
        }
    """

    @Test
    fun partialValidationAcceptsTheElseFullValidationTakes() {
        assertKsonEnforcesSchema("a: 1", kindPicksA, true)
        assertKsonEnforcesSchema("a: 1", kindPicksA, true, sourceContext = partial)
    }

    @Test
    fun partialValidationAcceptsTheMissingElseFullValidationTakes() {
        val schema = """{ "if": { "required": ["kind"] }, "then": { "maxProperties": 0 } }"""
        assertKsonEnforcesSchema("a: 1", schema, true)
        assertKsonEnforcesSchema("a: 1", schema, true, sourceContext = partial)
    }

    @Test
    fun partialValidationAppliesThenWhereFullValidationDoes() {
        // `kind` is there, so the condition holds under full validation too: the `then` rejects the value,
        // though the `else` would accept it
        assertKsonSchemaErrors("kind: 1\na: 1", kindPicksA, listOf(SCHEMA_OBJECT_TOO_MANY_PROPERTIES))
        assertKsonSchemaErrors("kind: 1\na: 1", kindPicksA, listOf(SCHEMA_OBJECT_TOO_MANY_PROPERTIES), sourceContext = partial)
    }

    @Test
    fun partialValidationAppliesThenWhereTheElseFails() {
        // Full validation takes the `else`, which rejects `a: x`, and partial validation the `then`
        assertKsonSchemaErrors("a: x", kindPicksA, listOf(SCHEMA_VALUE_TYPE_MISMATCH))
        assertKsonSchemaErrors("a: x", kindPicksA, listOf(SCHEMA_OBJECT_TOO_MANY_PROPERTIES), sourceContext = partial)
    }
}
