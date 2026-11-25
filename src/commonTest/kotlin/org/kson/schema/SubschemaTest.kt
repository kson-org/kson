package org.kson.schema

import org.kson.KsonCore
import org.kson.value.KsonList
import org.kson.value.KsonObject
import org.kson.value.KsonValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class SubschemaTest {
    private fun kson(source: String): KsonValue =
        assertNotNull(KsonCore.parseToAst(source).ksonValue, "should parse: $source")

    @Test
    fun subschemasAreEnteredAndTheirRefsFollowed() {
        val root = kson(
            $$"""
                '$id': 'https://example.org/root/'
                properties:
                  near:
                    '$ref': 'far/'
                    .
                  .
                definitions:
                  far:
                    '$id': 'far/'
                    title: Far
            """.trimIndent()
        ) as KsonObject
        val far = (root.propertyLookup.getValue("definitions") as KsonObject).propertyLookup.getValue("far")

        val subschemas = IndexedSchemaDocuments(SchemaDocument(root)).resolvedRootSchema.subschemas

        assertEquals(listOf(Applies.NotApplied, Applies.Property("near")), subschemas.map { it.applies })
        for (subschema in subschemas) {
            assertSame(far, subschema.resolved.value, subschema.keyword)
            assertEquals("https://example.org/root/", subschema.resolved.baseUri.toString(), subschema.keyword)
        }
    }

    @Test
    fun subschemasOfAChoiceNameWhatMakesIt() {
        val root = kson(
            """
                {
                  "oneOf": [{ "type": "string" }, { "type": "number" }],
                  "if": { "type": "string" },
                  "then": { "minLength": 1 },
                  "else": { "minimum": 0 }
                }
            """.trimIndent()
        ) as KsonObject
        val oneOf = (root.propertyLookup.getValue("oneOf") as KsonList).elements

        val subschemas = IndexedSchemaDocuments(SchemaDocument(root)).resolvedRootSchema.subschemas

        assertEquals(
            listOf(Applies.Alternative(0), Applies.Alternative(1), Applies.Condition, Applies.Then, Applies.Else),
            subschemas.map { it.applies }
        )
        val (alternatives, conditional) = subschemas.partition { it.applies is Applies.Alternative }
        val amongAlternatives = assertIs<SchemaChoice.Among>(alternatives.first().choice)
        assertEquals(oneOf.size, amongAlternatives.alternatives.size)
        oneOf.zip(amongAlternatives.alternatives).forEach { (element, alternative) ->
            assertSame(element, alternative.value)
        }
        alternatives.forEach { assertSame(amongAlternatives, it.choice) }
        val byCondition = assertIs<SchemaChoice.By>(conditional.first().choice)
        assertSame(root.propertyLookup.getValue("if"), byCondition.condition.value)
        conditional.forEach { assertSame(byCondition, it.choice) }
    }

    @Test
    fun subschemasOfNotContainsAndPropertyNamesNameWhereEachApply() {
        val root = kson(
            """
                {
                  "contains": { "type": "number" },
                  "not": { "type": "string" },
                  "propertyNames": { "maxLength": 3 }
                }
            """.trimIndent()
        )

        val subschemas = IndexedSchemaDocuments(SchemaDocument(root)).resolvedRootSchema.subschemas

        assertEquals(
            listOf("contains" to Applies.SomeItem, "not" to Applies.Negated, "propertyNames" to Applies.PropertyNames),
            subschemas.map { it.keyword to it.applies }
        )
    }
}
