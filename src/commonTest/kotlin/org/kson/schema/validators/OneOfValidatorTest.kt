package org.kson.schema.validators

import org.kson.parser.messages.MessageType.*
import org.kson.schema.JsonSchemaTest
import org.kson.validation.SourceContext
import org.kson.validation.ValidationMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Generic `oneOf` behaviour; narrowing is mostly covered by its own suites, [OneOfDiscriminatedUnionTest],
 * [OneOfEliminationTest], [OneOfPresenceUnionTest], [UnionNarrowingThroughAllOfTest] and
 * [UnionNarrowingHidesNothingTest].
 */
class OneOfValidatorTest : JsonSchemaTest {
    /**
     * `description` is known by both branches, so presence can't narrow.  Its error is common to both,
     * so it is reported once, separately, and the listing shows the first branch lacks nothing else.
     */
    @Test
    fun testOneOfCommonValidationErrors() {
        val errors = assertKsonSchemaErrors(
            """
                description: 99
            """.trimIndent(),
            """
                oneOf:
                  - properties:
                      description:
                        type: string
                        .
                      thing:
                        type: number

                  - properties:
                      description:
                        type: string
                        .
                      .
                    required:
                      - required_prop
            """.trimIndent(),
            listOf(
                SCHEMA_VALUE_TYPE_MISMATCH,
                SCHEMA_ONE_OF_VALIDATION_FAILED,
                SCHEMA_SUB_SCHEMA_ERRORS
            )
        )

        assertEquals(
            "Value matches none of these sub-schemas:\n" +
                "- JSON Object Schema: no errors beyond those reported separately\n" +
                "- JSON Object Schema:\n" +
                "    - Missing required properties: required_prop (1:1)",
            errors[2].message.toString()
        )
    }

    /**
     * Presence matches on declared properties, not only required ones: `think` is optional in branch one.
     */
    @Test
    fun testOneOfPresenceNarrowsOnOptionalDeclaredProperty() {
        val errors = assertKsonSchemaErrors(
            """
                description: "describer"
                think: false
            """.trimIndent(),
            """
                oneOf:
                  - properties:
                      description:
                        type: string
                        .
                      think:
                        type: number

                  - properties:
                      description:
                        type: string
                        .
                      .
                    required:
                      - required_prop
            """.trimIndent(),
            listOf(
                SCHEMA_VALUE_TYPE_MISMATCH,
                SCHEMA_OTHER_SUB_SCHEMA_ERRORS
            )
        )

        assertFalse(errors[0].message.toString().contains("required_prop"))
    }

    /**
     * [ValidationMode.PARTIAL] mode withholds the multiple-match error: a half-typed document may
     * still match several branches.
     */
    @Test
    fun testOneOfMultipleMatches() {
        val document = """
            description: "hello"
        """.trimIndent()
        val schema = """
            oneOf:
              - properties:
                  description:
                    type: string

              - properties:
                  description:
                    type: string
        """.trimIndent()

        assertKsonSchemaErrors(document, schema, listOf(SCHEMA_ONE_OF_MULTIPLE_MATCHES))
        assertKsonEnforcesSchema(
            document,
            schema,
            shouldAcceptAsValid = true,
            sourceContext = SourceContext(mode = ValidationMode.PARTIAL)
        )
    }

    /**
     * Both branches lead and share the `x` error, which is reported separately; each fails on one more
     * property.  [ValidationMode.FULL] lists those, while [ValidationMode.PARTIAL] keeps to the `x` error.
     */
    @Test
    fun testOneOfPartialModeReportsOnlyTheSharedError() {
        val document = """
            x: s
            y: s
            z: s
        """.trimIndent()
        val schema = """
            oneOf:
              - title: XY
                properties:
                  x:
                    type: number
                    .
                  y:
                    type: number

              - title: XZ
                properties:
                  x:
                    type: number
                    .
                  z:
                    type: number
        """.trimIndent()

        val fullErrors = assertKsonSchemaErrors(
            document,
            schema,
            listOf(SCHEMA_VALUE_TYPE_MISMATCH, SCHEMA_ONE_OF_VALIDATION_FAILED, SCHEMA_SUB_SCHEMA_ERRORS)
        )
        assertEquals("Property 'x': Expected one of: number, but got: string", fullErrors[0].message.toString())
        assertEquals(
            "Value matches none of these sub-schemas:\n" +
                "- XY:\n" +
                "    - Property 'y': Expected one of: number, but got: string (2:4)\n" +
                "- XZ:\n" +
                "    - Property 'z': Expected one of: number, but got: string (3:4)",
            fullErrors[2].message.toString()
        )

        val partialErrors = assertKsonSchemaErrors(
            document,
            schema,
            listOf(SCHEMA_VALUE_TYPE_MISMATCH),
            sourceContext = SourceContext(mode = ValidationMode.PARTIAL)
        )
        assertEquals("Property 'x': Expected one of: number, but got: string", partialErrors[0].message.toString())
    }

    @Test
    fun testOneOfWithoutDiscriminatorReportsSubSchemaErrors() {
        assertKsonSchemaErrors(
            """
                value: "hello"
            """.trimIndent(),
            """
                oneOf:
                  - properties:
                      value:
                        type: number
                  - properties:
                      value:
                        type: boolean
            """.trimIndent(),
            listOf(
                SCHEMA_ONE_OF_VALIDATION_FAILED,
                SCHEMA_SUB_SCHEMA_ERRORS
            )
        )
    }

    /**
     * Branch `Q` fails its own `oneOf`, so `Q`'s errors include that union's report: the report's
     * location follows its first line, and its bullets are indented beneath it.
     */
    @Test
    fun testOneOfNestsABranchsOwnUnionReport() {
        val errors = assertKsonSchemaErrors(
            "z: 1",
            """
                oneOf:
                  - title: P
                    required:
                      - p
                      =

                  - title: Q
                    oneOf:
                      - title: Q1
                        properties:
                          z:
                            type: string

                      - title: Q2
                        properties:
                          z:
                            type: boolean
            """.trimIndent(),
            listOf(SCHEMA_ONE_OF_VALIDATION_FAILED, SCHEMA_SUB_SCHEMA_ERRORS)
        )

        assertEquals(
            "Value matches none of these sub-schemas:\n" +
                "- P:\n" +
                "    - Missing required properties: p (1:1)\n" +
                "- Q:\n" +
                "    - Value must match exactly one of the specified schemas (1:1)\n" +
                "    - Value matches none of these sub-schemas: (1:1)\n" +
                "      - Q1:\n" +
                "          - Property 'z': Expected one of: string, but got: integer (1:4)\n" +
                "      - Q2:\n" +
                "          - Property 'z': Expected one of: boolean, but got: integer (1:4)",
            errors[1].message.toString()
        )
    }
}
