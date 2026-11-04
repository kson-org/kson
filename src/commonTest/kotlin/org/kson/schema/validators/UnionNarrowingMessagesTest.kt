package org.kson.schema.validators

import org.kson.CoreCompileConfig
import org.kson.KsonCore
import org.kson.parser.Coordinates
import org.kson.parser.Location
import org.kson.schema.JsonSchemaTest
import org.kson.validation.SourceContext
import org.kson.validation.ValidationMode
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The exact messages a failed union leads with.  In [ValidationMode.FULL] the other branches are listed after
 * them, which these tests don't pin down.  [org.kson.parser.MessageSink] de-duplicates by message type and text,
 * and so does the search for errors common to every branch, so what reads the same must also be the same type.
 */
class UnionNarrowingMessagesTest : JsonSchemaTest {
    private fun reported(schema: String, document: String, mode: ValidationMode): List<Pair<String, Location>> {
        val config = CoreCompileConfig(schemaJson = assertValidSchema(schema), sourceContext = SourceContext(mode = mode))
        return KsonCore.parseToAst(document, config).messages.map { it.message.toString() to it.location }
    }

    private fun <T> assertLeadsWith(expected: List<T>, reported: List<T>, mode: ValidationMode, message: String) {
        val leading = if (mode == ValidationMode.FULL) reported.take(expected.size) else reported
        assertEquals(expected, leading, message)
    }

    private fun assertReportedInFullAndPartial(schema: String, document: String, vararg expected: Pair<String, Location>) {
        for (mode in listOf(ValidationMode.FULL, ValidationMode.PARTIAL)) {
            assertLeadsWith(expected.toList(), reported(schema, document, mode), mode, "in $mode")
        }
    }

    @Test
    fun testEnumBesideAUnionRejectingTheSameValueReportsOnce() {
        assertReportedInFullAndPartial(
            """
                properties:
                  kind:
                    enum:
                      - A
                      - B
                    .
                  .
                oneOf:
                  - properties:
                      kind:
                        const: A
                  - properties:
                      kind:
                        const: B
            """.trimIndent(),
            "kind: Z",
            "Value must be one of: \"A\", \"B\"" to Location(Coordinates(0, 6), Coordinates(0, 7), 6, 7)
        )
    }

    @Test
    fun testOuterUnionKeepsTheEnumErrorItsBranchesShare() {
        assertReportedInFullAndPartial(
            """
                oneOf:
                  - properties:
                      spec:
                        properties:
                          kind:
                            enum:
                              - A
                              - B
                              =
                  - properties:
                      spec:
                        oneOf:
                          - properties:
                              kind:
                                const: A
                          - properties:
                              kind:
                                const: B
            """.trimIndent(),
            "spec: {kind: Z}",
            "Value must be one of: \"A\", \"B\"" to Location(Coordinates(0, 13), Coordinates(0, 14), 13, 14)
        )
    }

    /** Validation stops at `Broken`'s `type`, so only reading its pins would reach the malformed pointer. */
    @Test
    fun testNonObjectDocumentReportsEveryBranchWithoutReadingPins() {
        val wholeDocument = Location(Coordinates(0, 0), Coordinates(0, 1), 0, 1)

        assertReportedInFullAndPartial(
            $$"""
                oneOf:
                  - title: Broken
                    type: object
                    allOf:
                      - '$ref': '#/$defs/Bad'
                      =

                  - title: Text
                    type: string
                    .
                '$defs':
                  Bad:
                    '$ref': '#/$defs/a~2b'
            """.trimIndent(),
            "3",
            "Value must match exactly one of the specified schemas" to wholeDocument,
            """
                Value matches none of these sub-schemas:
                - Broken:
                    - Expected one of: object, but got: integer (1:1)
                - Text:
                    - Expected one of: string, but got: integer (1:1)
            """.trimIndent() to wholeDocument
        )
    }
}
