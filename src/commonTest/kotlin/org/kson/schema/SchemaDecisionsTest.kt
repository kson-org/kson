package org.kson.schema

import org.kson.KsonCore
import org.kson.schema.SchemaDecisions.accepts
import org.kson.schema.SchemaDecisions.breaks
import org.kson.value.KsonObject
import org.kson.value.KsonValue
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SchemaDecisionsTest {
    private fun kson(source: String): KsonValue =
        assertNotNull(KsonCore.parseToAst(source).ksonValue, "should parse: $source")

    private fun compiled(schema: String): JsonSchema {
        val node = kson(schema)
        return assertNotNull(SchemaDecisions.compile(node, "", SchemaIdLookup(node)), "should compile: $schema")
    }

    private fun locationOf(property: String, document: KsonValue) =
        (document as KsonObject).propertyLookup.getValue(property).location

    @Test
    fun compileFollowsARef() {
        val schema = compiled(
            $$"""
                '$ref': '#/$defs/count'
                '$defs':
                  count:
                    type: number
            """.trimIndent()
        )

        assertFalse(breaks(schema, kson("3")))
        assertTrue(breaks(schema, kson("three")))
    }

    @Test
    fun compileReadsRefsUnderTheBaseUriItIsGiven() {
        val root = kson(
            $$"""
                '$id': 'https://example.org/root'
                '$ref': '#/$defs/count'
                '$defs':
                  count: {}
            """.trimIndent()
        )
        val idLookup = SchemaIdLookup(root)

        assertNotNull(SchemaDecisions.compile(root, "https://example.org/root", idLookup))
        assertNull(SchemaDecisions.compile(root, "https://example.org/elsewhere", idLookup))
    }

    @Test
    fun compileAnswersNullForWhatIsNoSchema() {
        val notASchema = kson("3")
        assertNull(SchemaDecisions.compile(notASchema, "", SchemaIdLookup(notASchema)))

        val danglingRef = kson($$"'$ref': '#/$defs/missing'")
        assertNull(SchemaDecisions.compile(danglingRef, "", SchemaIdLookup(danglingRef)))
    }

    @Test
    fun compileThrowsOnAMalformedRefPointer() {
        val malformedPointer = kson($$"'$ref': '#/$defs/a~2b'")

        assertFailsWith<IllegalArgumentException> {
            SchemaDecisions.compile(malformedPointer, "", SchemaIdLookup(malformedPointer))
        }
    }

    @Test
    fun compileLeavesAMalformedKeywordUnconstraining() {
        val schema = compiled(
            """
                type: 5
                const: circle
            """.trimIndent()
        )

        assertFalse(breaks(schema, kson("circle")))
        assertTrue(breaks(schema, kson("square")))
    }

    @Test
    fun compileAcceptsBooleanSchemas() {
        assertFalse(breaks(compiled("true"), kson("anything")))
        assertTrue(breaks(compiled("false"), kson("anything")))
    }

    @Test
    fun aValueThatFitsDoesNotBreak() {
        val schema = compiled(
            """
                properties:
                  kind:
                    const: circle
                    .
                  .
                required:
                  - kind
            """.trimIndent()
        )

        assertFalse(breaks(schema, kson("kind: circle")))
    }

    @Test
    fun whatIsOnlyMissingDoesNotBreak() {
        val schema = compiled(
            """
                properties:
                  kind:
                    const: circle
                    .
                  .
                required:
                  - kind
            """.trimIndent()
        )

        assertFalse(breaks(schema, kson("radius: 3")))
    }

    @Test
    fun aPresentValueTheSchemaForbidsBreaks() {
        assertTrue(breaks(compiled("type: object"), kson("[1, 2]")))
        assertTrue(breaks(compiled("properties: { kind: { const: circle } }"), kson("kind: square")))
        assertTrue(breaks(compiled("additionalProperties: false"), kson("radius: 3")))
    }

    @Test
    fun aProblemInsideTheForgivenSpanDoesNotBreak() {
        val schema = compiled(
            """
                properties:
                  kind:
                    const: circle
            """.trimIndent()
        )
        val document = kson("kind: cir")

        assertTrue(breaks(schema, document))
        assertFalse(breaks(schema, document, forgiving = locationOf("kind", document)))
    }

    @Test
    fun aProblemOutsideTheForgivenSpanStillBreaks() {
        val schema = compiled(
            """
                properties:
                  kind:
                    const: circle
                    .
                  radius:
                    type: number
            """.trimIndent()
        )
        val document = kson(
            """
                kind: cir
                radius: big
            """.trimIndent()
        )

        assertTrue(breaks(schema, document, forgiving = locationOf("kind", document)))
    }

    @Test
    fun aValueThatFitsIsAccepted() {
        val schema = compiled(
            """
                properties:
                  kind:
                    const: circle
                    .
                  .
                required:
                  - kind
            """.trimIndent()
        )

        assertTrue(accepts(schema, kson("kind: circle")))
    }

    @Test
    fun aPresentValueTheSchemaForbidsIsNotAccepted() {
        assertFalse(accepts(compiled("type: object"), kson("[1, 2]")))
        assertFalse(accepts(compiled("properties: { kind: { const: circle } }"), kson("kind: square")))
        assertFalse(accepts(compiled("additionalProperties: false"), kson("radius: 3")))
    }

    @Test
    fun whatIsOnlyUnfinishedDoesNotBreakButIsNotAccepted() {
        val unfinished = listOf(
            "required: [kind]" to "radius: 3",
            "minProperties: 2" to "radius: 3",
            "minItems: 2" to "[1]",
            "minLength: 2" to "a",
            "contains: { const: 1 }" to "[2]",
            "dependencies: { radius: [kind] }" to "radius: 3",
            "not: { type: string }" to "a",
            "oneOf: [true, true]" to "a"
        )

        for ((source, document) in unfinished) {
            val schema = compiled(source)
            val value = kson(document)

            assertFalse(breaks(schema, value), "breaks: $source")
            assertFalse(accepts(schema, value), "accepts: $source")
        }
    }
}
