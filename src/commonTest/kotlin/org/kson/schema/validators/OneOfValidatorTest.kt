package org.kson.schema.validators

import org.kson.parser.Coordinates
import org.kson.parser.Location
import org.kson.parser.messages.MessageType.*
import org.kson.schema.JsonSchemaTest
import kotlin.test.Test
import kotlin.test.assertContains

class OneOfValidatorTest : JsonSchemaTest {
    /**
     * A discriminated union shaped like real-world schemas: `kind` repeats consts across branches
     * (so it can't discriminate), `kind_job` carries the distinct consts (the real discriminator),
     * each branch's `params` requires a different property, and a trailing wildcard branch pins
     * neither `kind` nor `kind_job` to a const.
     */
    private val duplicateConstUnionWithWildcard = """
        {
          "oneOf": [
            {
              "properties": {
                "kind": { "const": "A" },
                "kind_job": { "const": "J1" },
                "params": { "type": "object", "required": ["p1"] }
              },
              "required": ["kind", "kind_job", "params"]
            },
            {
              "properties": {
                "kind": { "const": "A" },
                "kind_job": { "const": "J2" },
                "params": { "type": "object", "required": ["p2"] }
              },
              "required": ["kind", "kind_job", "params"]
            },
            {
              "properties": {
                "kind": { "const": "B" },
                "kind_job": { "const": "J3" },
                "params": { "type": "object", "required": ["p3"] }
              },
              "required": ["kind", "kind_job", "params"]
            },
            {
              "properties": {
                "kind": { "type": "string" },
                "kind_job": { "not": { "enum": ["J1", "J2", "J3"] } },
                "params": { "type": "object", "required": ["p4"] }
              },
              "required": ["kind", "kind_job", "params"]
            }
          ]
        }
    """.trimIndent()

    @Test
    fun testOneOfCommonValidationErrors() {
        assertKsonSchemaErrors(
            """
                description: 99
                thing: false
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
                // since `description` is wrong for ALL the sub-schemas, we get a precise error for it
                SCHEMA_VALUE_TYPE_MISMATCH
            )
        )
    }

    @Test
    fun testOneOfDiverseValidationErrors() {
        assertKsonSchemaErrors(
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
                SCHEMA_ONE_OF_VALIDATION_FAILED,
                // sub-schema errors are rolled up into this error
                SCHEMA_SUB_SCHEMA_ERRORS
            )
        )
    }

    @Test
    fun testOneOfMultipleMatches() {
        assertKsonSchemaErrors(
            """
                description: "hello"
            """.trimIndent(),
            """
                oneOf:
                  - properties:
                      description:
                        type: string

                  - properties:
                      description:
                        type: string
            """.trimIndent(),
            listOf(
                SCHEMA_ONE_OF_MULTIPLE_MATCHES
            )
        )
    }

    /**
     * A discriminated union (branches keyed by distinct `kind` consts) whose discriminator matches
     * one branch reports only that branch's deeper failure — here `params` is missing a required
     * property — instead of dumping every branch's errors.  The branches require *different* `params`
     * properties (as real discriminated unions do), so without discriminator-awareness this dumps
     * every branch; with it, only branch A's `alpha` requirement surfaces.
     */
    @Test
    fun testOneOfDiscriminatorSelectsMatchingBranch() {
        val errors = assertKsonSchemaErrorAtLocation(
            """
                kind: "A"
                params: {}
            """.trimIndent(),
            """
                {
                  "oneOf": [
                    {
                      "properties": {
                        "kind": { "const": "A" },
                        "params": { "type": "object", "required": ["alpha"] }
                      },
                      "required": ["kind"]
                    },
                    {
                      "properties": {
                        "kind": { "const": "B" },
                        "params": { "type": "object", "required": ["beta"] }
                      },
                      "required": ["kind"]
                    }
                  ]
                }
            """.trimIndent(),
            // only branch A's error — no SCHEMA_ONE_OF_VALIDATION_FAILED, no SCHEMA_SUB_SCHEMA_ERRORS dump
            listOf(
                SCHEMA_REQUIRED_PROPERTY_MISSING
            ),
            // and it lands inside `params`, the value the user must actually fix
            listOf(
                Location(Coordinates(1, 8), Coordinates(1, 10), 18, 20)
            )
        )

        // branch A was selected: its `alpha` requirement is reported, not branch B's `beta`
        assertContains(errors[0].message.toString(), "alpha")
    }

    /**
     * The motivating real-world discriminator shape is `{ type: string, const: X }` — a `const` with a
     * sibling `type` — not the bare `{ const: X }` the other tests use.  The sibling `type` parses into
     * a separate type-validator, so the property's `schemaValidators` is still just `[ConstValidator]`
     * and discriminator detection selects the matching branch exactly as it does for a bare `const`.
     */
    @Test
    fun testOneOfDiscriminatorWithSiblingTypeSelectsMatchingBranch() {
        val errors = assertKsonSchemaErrorAtLocation(
            """
                kind: "A"
                params: {}
            """.trimIndent(),
            """
                {
                  "oneOf": [
                    {
                      "properties": {
                        "kind": { "type": "string", "const": "A" },
                        "params": { "type": "object", "required": ["alpha"] }
                      },
                      "required": ["kind"]
                    },
                    {
                      "properties": {
                        "kind": { "type": "string", "const": "B" },
                        "params": { "type": "object", "required": ["beta"] }
                      },
                      "required": ["kind"]
                    }
                  ]
                }
            """.trimIndent(),
            // only branch A's error — no SCHEMA_ONE_OF_VALIDATION_FAILED, no SCHEMA_SUB_SCHEMA_ERRORS dump
            listOf(
                SCHEMA_REQUIRED_PROPERTY_MISSING
            ),
            // and it lands inside `params`, the value the user must actually fix
            listOf(
                Location(Coordinates(1, 8), Coordinates(1, 10), 18, 20)
            )
        )

        // branch A selected via the `{ type, const }` discriminator: its `alpha` requirement surfaces, not `beta`
        assertContains(errors[0].message.toString(), "alpha")
    }

    /**
     * When the discriminator value matches no branch's `const`, collapse to one enum error at the
     * discriminator value rather than dumping every branch.
     */
    @Test
    fun testOneOfDiscriminatorMatchesNoBranch() {
        val errors = assertKsonSchemaErrorAtLocation(
            """
                kind: "Z"
                params: {}
            """.trimIndent(),
            """
                {
                  "oneOf": [
                    {
                      "properties": {
                        "kind": { "const": "A" },
                        "params": { "type": "object", "required": ["alpha"] }
                      },
                      "required": ["kind"]
                    },
                    {
                      "properties": {
                        "kind": { "const": "B" },
                        "params": { "type": "object", "required": ["beta"] }
                      },
                      "required": ["kind"]
                    }
                  ]
                }
            """.trimIndent(),
            listOf(
                SCHEMA_ENUM_VALUE_NOT_ALLOWED
            ),
            // the enum error hangs off the `kind` value `Z` (the discriminator the user must fix)
            listOf(
                Location(Coordinates(0, 7), Coordinates(0, 8), 7, 8)
            )
        )

        assertContains(errors[0].message.toString(), "Value must be one of: \"A\", \"B\"")
    }

    /**
     * Mirrors the real `allOf[ base, oneOf[ branches ] ]` shape: the discriminator-aware reporting
     * still applies through the `allOf` wrapper, and branches keyed by dual consts (`kind` +
     * `kind_job`) discriminate on the first const property in declaration order.
     */
    @Test
    fun testOneOfDiscriminatorThroughAllOfWrapper() {
        val errors = assertKsonSchemaErrorAtLocation(
            """
                kind: "A"
                kind_job: "A_JOB"
                params: {}
            """.trimIndent(),
            """
                {
                  "allOf": [
                    { "type": "object" },
                    {
                      "oneOf": [
                        {
                          "properties": {
                            "kind": { "const": "A" },
                            "kind_job": { "const": "A_JOB" },
                            "params": { "type": "object", "required": ["alpha"] }
                          },
                          "required": ["kind", "kind_job", "params"]
                        },
                        {
                          "properties": {
                            "kind": { "const": "B" },
                            "kind_job": { "const": "B_JOB" },
                            "params": { "type": "object", "required": ["beta"] }
                          },
                          "required": ["kind", "kind_job", "params"]
                        }
                      ]
                    }
                  ]
                }
            """.trimIndent(),
            listOf(
                SCHEMA_REQUIRED_PROPERTY_MISSING
            ),
            // branch A selected via `kind`; its `params` failure is reported inside `params`
            listOf(
                Location(Coordinates(2, 8), Coordinates(2, 10), 36, 38)
            )
        )

        // selected branch A via `kind` (the first const property), so its `alpha` requirement surfaces
        assertContains(errors[0].message.toString(), "alpha")
    }

    /**
     * When branches don't share a single distinct-`const` property there's no discriminator, so we
     * keep the existing full dump of every branch's errors.
     */
    @Test
    fun testOneOfWithoutDiscriminatorDumpsSubSchemaErrors() {
        assertKsonSchemaErrors(
            """
                value: "hello"
            """.trimIndent(),
            """
                {
                  "oneOf": [
                    { "properties": { "value": { "type": "number" } } },
                    { "properties": { "value": { "type": "boolean" } } }
                  ]
                }
            """.trimIndent(),
            listOf(
                SCHEMA_ONE_OF_VALIDATION_FAILED,
                SCHEMA_SUB_SCHEMA_ERRORS
            )
        )
    }

    /**
     * Real-schema reduction: `kind` consts repeat (A, A, B) so it can't discriminate, but `kind_job`
     * consts are distinct (J1, J2, J3) and a trailing wildcard branch pins neither.  Detection must
     * tolerate the duplicate-const property and the wildcard, choose `kind_job`, and report only the
     * branch the document's `kind_job` selects — here the J1 branch's `params` requirement.
     *
     * The document's `kind` is `A`, shared by the J1 and J2 branches, so `J1` and `kind: A` point at
     * different branches: selecting J1's branch (requires `p1`) — not the `kind`-ambiguous J2 branch
     * (requires `p2`) — proves `kind_job`, not `kind`, drove the choice.
     */
    @Test
    fun testOneOfDiscriminatorToleratesDuplicateConstsAndWildcard() {
        val errors = assertKsonSchemaErrors(
            """
                kind: "A"
                kind_job: "J1"
                params: {}
            """.trimIndent(),
            duplicateConstUnionWithWildcard,
            listOf(
                SCHEMA_REQUIRED_PROPERTY_MISSING
            )
        )

        assertContains(errors[0].message.toString(), "p1")
    }

    /**
     * With a wildcard branch present, a `kind_job` value that matches no pinned branch must NOT
     * collapse to an enum error — the wildcard might legitimately accept it — so we keep the dump.
     */
    @Test
    fun testOneOfDiscriminatorWildcardNoMatchDumps() {
        assertKsonSchemaErrors(
            """
                kind: "A"
                kind_job: "J9"
                params: {}
            """.trimIndent(),
            duplicateConstUnionWithWildcard,
            listOf(
                SCHEMA_ONE_OF_VALIDATION_FAILED,
                SCHEMA_SUB_SCHEMA_ERRORS
            )
        )
    }

    /**
     * When the only shared const property repeats consts across branches (`kind`: A, A, B), it can't
     * identify a single branch, so there is no discriminator and we keep the full dump rather than
     * arbitrarily picking one of the `A` branches.
     */
    @Test
    fun testOneOfDuplicateConstPropertyDoesNotDiscriminate() {
        assertKsonSchemaErrors(
            """
                kind: "A"
                params: {}
            """.trimIndent(),
            """
                {
                  "oneOf": [
                    {
                      "properties": {
                        "kind": { "const": "A" },
                        "params": { "type": "object", "required": ["p1"] }
                      },
                      "required": ["kind", "params"]
                    },
                    {
                      "properties": {
                        "kind": { "const": "A" },
                        "params": { "type": "object", "required": ["p2"] }
                      },
                      "required": ["kind", "params"]
                    },
                    {
                      "properties": {
                        "kind": { "const": "B" },
                        "params": { "type": "object", "required": ["p3"] }
                      },
                      "required": ["kind", "params"]
                    }
                  ]
                }
            """.trimIndent(),
            listOf(
                SCHEMA_ONE_OF_VALIDATION_FAILED,
                SCHEMA_SUB_SCHEMA_ERRORS
            )
        )
    }
}
