package org.kson.schema.validators

import org.kson.parser.messages.MessageType.*
import org.kson.schema.JsonSchemaTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Narrowing only orders a [org.kson.validation.ValidationMode.FULL] union failure report: whatever
 * leads, every branch's errors stay visible.  Each test is a shape whose lead alone would leave a
 * branch's errors out.
 */
class UnionNarrowingHidesNothingTest : JsonSchemaTest {
    @Test
    fun testLoneRequiredPinSelectionListsTheOtherBranch() {
        val errors = assertKsonSchemaErrors(
            "mode: fast",
            """
                oneOf:
                  - title: Pinned
                    properties:
                      mode:
                        const: fast
                        .
                      .
                    required:
                      - mode
                      - needs_pinned
                      =

                  - title: Other
                    properties:
                      mode:
                        type: string
                        .
                      .
                    required:
                      - needs_other
            """.trimIndent(),
            listOf(SCHEMA_REQUIRED_PROPERTY_MISSING, SCHEMA_OTHER_SUB_SCHEMA_ERRORS)
        )

        assertEquals("Missing required properties: needs_pinned", errors[0].message.toString())
        assertEquals(
            "Other sub-schemas tried:\n" +
                "- Other:\n" +
                "    - Missing required properties: needs_other (1:1)",
            errors[1].message.toString()
        )
    }

    /**
     * Neither lone optional pin narrows, so presence leads with `OAuthAuth`.
     */
    @Test
    fun testPresenceSelectionListsTheOtherBranch() {
        val errors = assertKsonSchemaErrors(
            """
                clientId: app
                grantType: password
            """.trimIndent(),
            """
                oneOf:
                  - title: ApiKeyAuth
                    properties:
                      apiKey:
                        type: string
                        .
                      location:
                        enum:
                          - header
                          - query
                        .
                      .
                    required:
                      - apiKey
                    additionalProperties: false

                  - title: OAuthAuth
                    properties:
                      clientId:
                        type: string
                        .
                      grantType:
                        enum:
                          - client_credentials
                          - authorization_code
                        .
                      .
                    required:
                      - clientId
                    additionalProperties: false
            """.trimIndent(),
            listOf(SCHEMA_ENUM_VALUE_NOT_ALLOWED, SCHEMA_OTHER_SUB_SCHEMA_ERRORS)
        )

        assertEquals("Value must be one of: \"client_credentials\", \"authorization_code\"", errors[0].message.toString())
        assertEquals(
            "Other sub-schemas tried:\n" +
                "- ApiKeyAuth:\n" +
                "    - Additional property 'clientId' is not allowed in ApiKeyAuth (1:1)\n" +
                "    - Additional property 'grantType' is not allowed in ApiKeyAuth (2:1)\n" +
                "    - Missing required properties: apiKey (1:1)",
            errors[1].message.toString()
        )
    }

    @Test
    fun testEnumErrorLeadListsEveryBranch() {
        val errors = assertKsonSchemaErrors(
            "kind: zzz",
            """
                oneOf:
                  - title: A
                    properties:
                      kind:
                        enum:
                          - a
                          - common
                        .
                      .
                    required:
                      - x
                      =

                  - title: B
                    properties:
                      kind:
                        enum:
                          - b
                          - common
                        .
                      .
                    required:
                      - y
            """.trimIndent(),
            listOf(SCHEMA_ENUM_VALUE_NOT_ALLOWED, SCHEMA_SUB_SCHEMA_ERRORS)
        )

        assertEquals("Value must be one of: \"a\", \"common\", \"b\"", errors[0].message.toString())
        assertEquals(
            "Value matches none of these sub-schemas:\n" +
                "- A:\n" +
                "    - Value must be one of: \"a\", \"common\" (1:7)\n" +
                "    - Missing required properties: x (1:1)\n" +
                "- B:\n" +
                "    - Value must be one of: \"b\", \"common\" (1:7)\n" +
                "    - Missing required properties: y (1:1)",
            errors[1].message.toString()
        )
    }

    /**
     * `kind: A` matches branch A's discriminator, but `version: 2` rejects both branches, so its enum
     * error leads.
     */
    @Test
    fun testSharedPinRejectionListsTheBranchTheOtherPinMatches() {
        val errors = assertKsonSchemaErrors(
            """
                version: 2
                kind: A
                params: {}
            """.trimIndent(),
            """
                oneOf:
                  - title: A
                    properties:
                      version:
                        const: 1
                        .
                      kind:
                        const: A
                        .
                      params:
                        type: object
                        required:
                          - alpha
                        .
                      .
                    required:
                      - version
                      - kind
                      =

                  - title: B
                    properties:
                      version:
                        const: 1
                        .
                      kind:
                        const: B
                        .
                      params:
                        type: object
                        required:
                          - beta
                        .
                      .
                    required:
                      - version
                      - kind
            """.trimIndent(),
            listOf(SCHEMA_ENUM_VALUE_NOT_ALLOWED, SCHEMA_SUB_SCHEMA_ERRORS)
        )

        assertEquals("Value must be one of: 1", errors[0].message.toString())
        assertEquals(
            "Value matches none of these sub-schemas:\n" +
                "- A:\n" +
                "    - Value must be exactly equal to 1 (1:10)\n" +
                "    - Missing required properties: alpha (3:9)\n" +
                "- B:\n" +
                "    - Value must be exactly equal to 1 (1:10)\n" +
                "    - Value must be exactly equal to \"B\" (2:7)\n" +
                "    - Missing required properties: beta (3:9)",
            errors[1].message.toString()
        )
    }

    /**
     * `mode: zzz` contradicts branch A on a pin it requires and `type: a` contradicts branch B, so the
     * unpinned branch W leads.
     */
    @Test
    fun testUnpinnedLeadListsTheContradictedBranches() {
        val errors = assertKsonSchemaErrors(
            """
                type: a
                mode: zzz
            """.trimIndent(),
            """
                oneOf:
                  - title: A
                    properties:
                      type:
                        const: a
                        .
                      mode:
                        const: m
                        .
                      x:
                        type: number
                        .
                      .
                    required:
                      - type
                      - mode
                      - x
                      =

                  - title: B
                    properties:
                      type:
                        const: b
                        .
                      .
                    required:
                      - type
                      =

                  - title: W
                    properties:
                      z:
                        type: number
                        .
                      .
                    required:
                      - z
            """.trimIndent(),
            listOf(SCHEMA_REQUIRED_PROPERTY_MISSING, SCHEMA_OTHER_SUB_SCHEMA_ERRORS)
        )

        assertEquals("Missing required properties: z", errors[0].message.toString())
        assertEquals(
            "Other sub-schemas tried:\n" +
                "- A:\n" +
                "    - Value must be exactly equal to \"m\" (2:7)\n" +
                "    - Missing required properties: x (1:1)\n" +
                "- B:\n" +
                "    - Value must be exactly equal to \"b\" (1:7)",
            errors[1].message.toString()
        )
    }

    /**
     * Both branches lack `id`, which is reported once, separately; what else each lacks is still listed.
     */
    @Test
    fun testSharedErrorLeavesEachBranchsOwnErrorsListed() {
        for ((keyword, noMatchType) in listOf(
            "oneOf" to SCHEMA_ONE_OF_VALIDATION_FAILED,
            "anyOf" to SCHEMA_ANY_OF_VALIDATION_FAILED
        )) {
            val errors = assertKsonSchemaErrors(
                """
                    type: a
                    mode: zzz
                """.trimIndent(),
                """
                    $keyword:
                      - title: A
                        properties:
                          type:
                            const: a
                            .
                          mode:
                            const: m
                            .
                          .
                        required:
                          - type
                          - mode
                          - id
                          =

                      - title: B
                        properties:
                          type:
                            const: b
                            .
                          .
                        required:
                          - type
                          - id
                """.trimIndent(),
                listOf(SCHEMA_REQUIRED_PROPERTY_MISSING, noMatchType, SCHEMA_SUB_SCHEMA_ERRORS)
            )

            assertEquals("Missing required properties: id", errors[0].message.toString())
            assertEquals(
                "Value matches none of these sub-schemas:\n" +
                    "- A:\n" +
                    "    - Value must be exactly equal to \"m\" (2:7)\n" +
                    "- B:\n" +
                    "    - Value must be exactly equal to \"b\" (1:7)",
                errors[2].message.toString()
            )
        }
    }

    /**
     * `kind: a` selects branch A, whose errors lead, though branch B fails only on the `x` error they
     * share.  B still lists it, as the error reported separately doesn't say which branches it fails.
     */
    @Test
    fun testOtherBranchFailingOnlyOnReportedErrorsIsListed() {
        val errors = assertKsonSchemaErrors(
            """
                kind: a
                x: s
            """.trimIndent(),
            """
                oneOf:
                  - title: A
                    properties:
                      kind:
                        const: a
                        .
                      x:
                        type: number
                        .
                      .
                    required:
                      - kind
                      - a1
                      =

                  - title: B
                    properties:
                      x:
                        type: number
            """.trimIndent(),
            listOf(SCHEMA_VALUE_TYPE_MISMATCH, SCHEMA_REQUIRED_PROPERTY_MISSING, SCHEMA_OTHER_SUB_SCHEMA_ERRORS)
        )

        assertEquals(
            "Other sub-schemas tried:\n" +
                "- B:\n" +
                "    - Property 'x': Expected one of: number, but got: string (2:4)",
            errors[2].message.toString()
        )
    }

    /**
     * `kind: a` selects branch A over branch B, and `x` fails under both: B lists its `x` error next to
     * its `kind` one, though the `x` error is also reported separately.
     */
    @Test
    fun testOtherBranchListsTheErrorItSharesWithTheLead() {
        val errors = assertKsonSchemaErrors(
            """
                kind: a
                x: s
            """.trimIndent(),
            """
                oneOf:
                  - title: A
                    properties:
                      kind:
                        const: a
                        .
                      x:
                        type: number

                  - title: B
                    properties:
                      kind:
                        const: b
                        .
                      x:
                        type: number
            """.trimIndent(),
            listOf(SCHEMA_VALUE_TYPE_MISMATCH, SCHEMA_OTHER_SUB_SCHEMA_ERRORS)
        )

        assertEquals("Property 'x': Expected one of: number, but got: string", errors[0].message.toString())
        assertEquals(
            "Other sub-schemas tried:\n" +
                "- B:\n" +
                "    - Value must be exactly equal to \"b\" (1:7)\n" +
                "    - Property 'x': Expected one of: number, but got: string (2:4)",
            errors[1].message.toString()
        )
    }
}
