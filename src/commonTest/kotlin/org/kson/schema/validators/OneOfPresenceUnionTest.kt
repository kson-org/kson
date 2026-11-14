package org.kson.schema.validators

import org.kson.parser.Coordinates
import org.kson.parser.Location
import org.kson.parser.messages.MessageType.*
import org.kson.schema.JsonSchemaTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Presence narrowing: among the branches no narrowing pin rules out, those that know (declare or
 * require) a property the document carries lead the report.  Pin elimination and selection take priority.
 */
class OneOfPresenceUnionTest : JsonSchemaTest {
    private val presenceUnion = """
        oneOf:
          - properties:
              selector_a:
                type: string
                .
              needs_a:
                type: string
                .
              .
            required:
              - selector_a
              - needs_a
              =

          - properties:
              selector_b:
                type: string
                .
              needs_b:
                type: string
                .
              .
            required:
              - selector_b
              - needs_b
    """.trimIndent()

    private val loneOptionalPinUnion = """
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
              clientSecret:
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
              - clientSecret
            additionalProperties: false
    """.trimIndent()

    /**
     * The `node` `$ref` is resolved one hop and its `child` property — a `$ref` back to `node` — is
     * never followed, so the self-reference can't recurse.
     */
    @Test
    fun testOneOfRefBranchSelfReferentialPropertyPresenceNarrowsToNodeBranch() {
        val errors = assertKsonSchemaErrors(
            """
                kind: "node"
            """.trimIndent(),
            $$"""
                oneOf:
                  - '$ref': '#/$defs/node'
                  - properties:
                      value:
                        type: boolean
                        .
                      .
                    required:
                      - value
                    .
                '$defs':
                  node:
                    properties:
                      kind:
                        type: string
                        .
                      child:
                        '$ref': '#/$defs/node'
                        .
                      .
                    required:
                      - child
            """.trimIndent(),
            listOf(
                SCHEMA_REQUIRED_PROPERTY_MISSING,
                SCHEMA_OTHER_SUB_SCHEMA_ERRORS
            )
        )

        assertContains(errors[0].message.toString(), "child")
        assertFalse(errors[0].message.toString().contains("value"))
        assertEquals(
            "Other sub-schemas tried:\n" +
                "- JSON Object Schema:\n" +
                "    - Missing required properties: value (1:1)",
            errors[1].message.toString()
        )
    }

    /**
     * A single leading branch reports its error directly, not grouped under the no-match message.
     */
    @Test
    fun testOneOfPresenceSelectsSingleMatchingBranch() {
        val errors = assertKsonSchemaErrors(
            """
                selector_a: "present"
            """.trimIndent(),
            presenceUnion,
            listOf(
                SCHEMA_REQUIRED_PROPERTY_MISSING,
                SCHEMA_OTHER_SUB_SCHEMA_ERRORS
            )
        )

        assertContains(errors[0].message.toString(), "needs_a")
        assertFalse(errors[0].message.toString().contains("needs_b"))
        assertEquals(
            "Other sub-schemas tried:\n" +
                "- JSON Object Schema:\n" +
                "    - Missing required properties: selector_b, needs_b (1:1)",
            errors[1].message.toString()
        )
    }

    /**
     * A property two branches know and the third doesn't narrows to both.
     */
    @Test
    fun testOneOfPresenceReportsAllMatchingBranches() {
        val errors = assertKsonSchemaErrors(
            """
                shared: "x"
            """.trimIndent(),
            """
                oneOf:
                  - title: BranchA
                    properties:
                      shared:
                        type: string
                        .
                      only_a:
                        type: string
                        .
                      .
                    required:
                      - shared
                      - only_a
                      =

                  - title: BranchB
                    properties:
                      shared:
                        type: string
                        .
                      only_b:
                        type: string
                        .
                      .
                    required:
                      - shared
                      - only_b
                      =

                  - title: BranchC
                    properties:
                      gamma:
                        type: string
                        .
                      .
                    required:
                      - gamma
            """.trimIndent(),
            listOf(
                SCHEMA_ONE_OF_VALIDATION_FAILED,
                SCHEMA_SUB_SCHEMA_ERRORS,
                SCHEMA_OTHER_SUB_SCHEMA_ERRORS
            )
        )

        val report = errors[1].message.toString()
        assertContains(report, "BranchA")
        assertContains(report, "BranchB")
        assertFalse(report.contains("BranchC"))
        assertEquals(
            "Other sub-schemas tried:\n" +
                "- BranchC:\n" +
                "    - Missing required properties: gamma (1:1)",
            errors[2].message.toString()
        )
    }

    @Test
    fun testOneOfPresenceNoMatchReportsAllBranches() {
        val errors = assertKsonSchemaErrors(
            """
                unrelated: "x"
            """.trimIndent(),
            presenceUnion,
            listOf(
                SCHEMA_ONE_OF_VALIDATION_FAILED,
                SCHEMA_SUB_SCHEMA_ERRORS
            )
        )

        val report = errors[1].message.toString()
        assertContains(report, "selector_a")
        assertContains(report, "selector_b")
    }

    @Test
    fun testOneOfLoneOptionalPinRejectionDoesNotEliminatePresenceMatchedBranch() {
        val errors = assertKsonSchemaErrorAtLocation(
            """
                { "clientId": "my-app", "clientSecret": "s3cret", "grantType": "password" }
            """.trimIndent(),
            loneOptionalPinUnion,
            listOf(
                SCHEMA_ENUM_VALUE_NOT_ALLOWED,
                SCHEMA_OTHER_SUB_SCHEMA_ERRORS
            ),
            listOf(
                // the `grantType` value `password`
                Location(Coordinates(0, 64), Coordinates(0, 72), 64, 72),
                Location(Coordinates(0, 0), Coordinates(0, 5), 0, 5)
            )
        )

        assertEquals("Value must be one of: \"client_credentials\", \"authorization_code\"", errors[0].message.toString())
    }

    @Test
    fun testOneOfLoneOptionalPinMatchDoesNotSelectBranch() {
        val errors = assertKsonSchemaErrors(
            """
                apiKey: abc123
                grantType: client_credentials
            """.trimIndent(),
            loneOptionalPinUnion,
            listOf(
                SCHEMA_ONE_OF_VALIDATION_FAILED,
                SCHEMA_SUB_SCHEMA_ERRORS
            )
        )

        assertEquals(
            "Value matches none of these sub-schemas:\n" +
                "- ApiKeyAuth:\n" +
                "    - Additional property 'grantType' is not allowed in ApiKeyAuth (2:1)\n" +
                "- OAuthAuth:\n" +
                "    - Additional property 'apiKey' is not allowed in OAuthAuth (1:1)\n" +
                "    - Missing required properties: clientId, clientSecret (1:1)",
            errors[1].message.toString()
        )
    }

    /**
     * Presence would pick branch B via `beta`, but only branch A's pin admits `kind: "A"`, so the pin wins.
     */
    @Test
    fun testOneOfPinnedValueTakesPrecedenceOverPresence() {
        val errors = assertKsonSchemaErrors(
            """
                kind: "A"
                beta: "present"
            """.trimIndent(),
            """
                oneOf:
                  - properties:
                      kind:
                        const: A
                        .
                      alpha:
                        type: string
                        .
                      .
                    required:
                      - kind
                      - alpha
                      =

                  - properties:
                      kind:
                        type: string
                        .
                      beta:
                        type: string
                        .
                      gamma:
                        type: string
                        .
                      .
                    required:
                      - beta
                      - gamma
            """.trimIndent(),
            listOf(
                SCHEMA_REQUIRED_PROPERTY_MISSING,
                SCHEMA_OTHER_SUB_SCHEMA_ERRORS
            )
        )

        assertEquals("Missing required properties: alpha", errors[0].message.toString())
    }

    /**
     * Known properties are read through each branch's `$ref`.
     */
    @Test
    fun testOneOfPresenceNarrowsThroughRefBranch() {
        val errors = assertKsonSchemaErrors(
            """
                needs_a: "present"
            """.trimIndent(),
            $$"""
                oneOf:
                  - '$ref': '#/$defs/A'
                  - '$ref': '#/$defs/B'
                    .
                '$defs':
                  A:
                    properties:
                      needs_a:
                        type: string
                        .
                      detail_a:
                        type: string
                        .
                      .
                    required:
                      - needs_a
                      - detail_a
                    .
                  B:
                    properties:
                      needs_b:
                        type: string
                        .
                      detail_b:
                        type: string
                        .
                      .
                    required:
                      - needs_b
                      - detail_b
            """.trimIndent(),
            listOf(
                SCHEMA_REQUIRED_PROPERTY_MISSING,
                SCHEMA_OTHER_SUB_SCHEMA_ERRORS
            )
        )

        assertContains(errors[0].message.toString(), "detail_a")
        assertFalse(errors[0].message.toString().contains("detail_b"))
        assertEquals(
            "Other sub-schemas tried:\n" +
                "- B:\n" +
                "    - Missing required properties: needs_b, detail_b (1:1)",
            errors[1].message.toString()
        )
    }

    /**
     * `region` is required by Lambda but only declared by Kubernetes; matching on `required` alone would
     * wrongly narrow to Lambda.
     */
    @Test
    fun testOneOfPresenceMatchesBranchesDeclaringPropertyEvenWhenOptional() {
        val errors = assertKsonSchemaErrors(
            """
                region: "us-east"
            """.trimIndent(),
            """
                oneOf:
                  - title: Kubernetes
                    properties:
                      kind:
                        const: kubernetes
                        .
                      cluster:
                        type: string
                        .
                      region:
                        type: string
                        .
                      .
                    required:
                      - kind
                      - cluster
                      =

                  - title: Lambda
                    properties:
                      kind:
                        const: lambda
                        .
                      region:
                        type: string
                        .
                      .
                    required:
                      - kind
                      - region
                      =

                  - title: Static
                    properties:
                      kind:
                        const: static
                        .
                      bucket:
                        type: string
                        .
                      .
                    required:
                      - kind
                      - bucket
            """.trimIndent(),
            listOf(
                SCHEMA_ONE_OF_VALIDATION_FAILED,
                SCHEMA_SUB_SCHEMA_ERRORS,
                SCHEMA_OTHER_SUB_SCHEMA_ERRORS
            )
        )

        val report = errors[1].message.toString()
        assertContains(report, "Kubernetes")
        assertContains(report, "Lambda")
        assertFalse(report.contains("Static"))
        assertEquals(
            "Other sub-schemas tried:\n" +
                "- Static:\n" +
                "    - Missing required properties: kind, bucket (1:1)",
            errors[2].message.toString()
        )
    }
}
