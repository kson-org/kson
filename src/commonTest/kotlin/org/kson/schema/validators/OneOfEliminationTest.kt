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
 * Elimination: a branch whose pin the document's value falls outside of drops out while another
 * survives.  Pin selection and presence only narrow among the survivors.
 */
class OneOfEliminationTest : JsonSchemaTest {
    /**
     * `kind` A repeats, so a value can match several branches' pins.
     */
    private val duplicateConstUnion = """
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
    """.trimIndent()

    private val unionWithEliminatedDuplicatePin = """
        {
          "oneOf": [
            {
              "properties": {
                "kind": { "const": "A" },
                "version": { "const": 1 }
              },
              "required": ["kind", "version"]
            },
            {
              "properties": { "kind": { "const": "A" } },
              "required": ["kind", "alpha"]
            },
            {
              "properties": {
                "kind": { "type": "string" },
                "beta": { "type": "string" }
              },
              "required": ["kind", "gamma"]
            }
          ]
        }
    """.trimIndent()

    /**
     * One pinned branch is enough to eliminate.
     */
    @Test
    fun testOneOfSinglePinnedBranchMismatchEliminates() {
        val errors = assertKsonSchemaErrors(
            """
                kind: "B"
            """.trimIndent(),
            """
                {
                  "oneOf": [
                    {
                      "properties": {
                        "kind": { "const": "A" },
                        "extra_a": { "type": "string" }
                      },
                      "required": ["kind", "extra_a"]
                    },
                    {
                      "properties": { "value": { "type": "boolean" } },
                      "required": ["value"]
                    }
                  ]
                }
            """.trimIndent(),
            listOf(
                SCHEMA_REQUIRED_PROPERTY_MISSING
            )
        )

        assertContains(errors[0].message.toString(), "value")
        assertFalse(errors[0].message.toString().contains("extra_a"))
    }

    @Test
    fun testOneOfNonDisjointPinsEliminateContradictedBranches() {
        val errors = assertKsonSchemaErrors(
            """
                kind: "B"
                params: {}
            """.trimIndent(),
            duplicateConstUnion,
            listOf(
                SCHEMA_REQUIRED_PROPERTY_MISSING
            )
        )

        assertContains(errors[0].message.toString(), "p3")
        assertFalse(errors[0].message.toString().contains("p1"))
        assertFalse(errors[0].message.toString().contains("p2"))
    }

    /**
     * `version: 2` eliminates the first branch, making `kind: "A"` unique among the surviving pins.
     */
    @Test
    fun testOneOfEliminatedDuplicatePinDoesNotPreventSelection() {
        val errors = assertKsonSchemaErrors(
            """
                kind: "A"
                version: 2
            """.trimIndent(),
            unionWithEliminatedDuplicatePin,
            listOf(SCHEMA_REQUIRED_PROPERTY_MISSING)
        )

        assertEquals("Missing required properties: alpha", errors[0].message.toString())
    }

    /**
     * The surviving unique pin selects before presence of `beta` can favor the unpinned branch.
     */
    @Test
    fun testOneOfSelectionAfterEliminationTakesPrecedenceOverPresence() {
        val errors = assertKsonSchemaErrors(
            """
                kind: "A"
                version: 2
                beta: "present"
            """.trimIndent(),
            unionWithEliminatedDuplicatePin,
            listOf(SCHEMA_REQUIRED_PROPERTY_MISSING)
        )

        assertEquals("Missing required properties: alpha", errors[0].message.toString())
    }

    /**
     * Each pinned value is listed once, though `A` is pinned twice.
     */
    @Test
    fun testOneOfValueNoBranchAdmitsReportsAllowedValues() {
        val errors = assertKsonSchemaErrorAtLocation(
            """
                kind: "Z"
                params: {}
            """.trimIndent(),
            duplicateConstUnion,
            listOf(
                SCHEMA_ENUM_VALUE_NOT_ALLOWED
            ),
            // the enum error hangs off the `kind` value `Z`
            listOf(
                Location(Coordinates(0, 7), Coordinates(0, 8), 7, 8)
            )
        )

        assertEquals("Value must be one of: \"A\", \"B\"", errors[0].message.toString())
    }

    @Test
    fun testOneOfEachValueNoBranchAdmitsIsReported() {
        val errors = assertKsonSchemaErrorAtLocation(
            """
                kind: "Z"
                mode: "sync"
            """.trimIndent(),
            """
                {
                  "oneOf": [
                    {
                      "properties": { "kind": { "const": "A" }, "mode": { "const": "read" } },
                      "required": ["kind", "mode"]
                    },
                    {
                      "properties": { "kind": { "const": "B" }, "mode": { "const": "write" } },
                      "required": ["kind", "mode"]
                    }
                  ]
                }
            """.trimIndent(),
            listOf(
                SCHEMA_ENUM_VALUE_NOT_ALLOWED,
                SCHEMA_ENUM_VALUE_NOT_ALLOWED
            ),
            listOf(
                Location(Coordinates(0, 7), Coordinates(0, 8), 7, 8),
                Location(Coordinates(1, 7), Coordinates(1, 11), 17, 21)
            )
        )

        assertEquals("Value must be one of: \"A\", \"B\"", errors[0].message.toString())
        assertEquals("Value must be one of: \"read\", \"write\"", errors[1].message.toString())
    }

    /**
     * `kind: "A"` is admitted by branch A alone, but `version: 2` rules branch A out with every other,
     * so the shared value is blamed alone; branch A's missing `alpha` waits until `version` is fixed.
     */
    @Test
    fun testOneOfSharedPinRejectionIsReportedAlone() {
        val errors = assertKsonSchemaErrorAtLocation(
            """
                version: 2
                kind: "A"
                params: {}
            """.trimIndent(),
            """
                {
                  "oneOf": [
                    {
                      "properties": {
                        "version": { "const": 1 },
                        "kind": { "const": "A" },
                        "params": { "type": "object", "required": ["alpha"] }
                      },
                      "required": ["version", "kind"]
                    },
                    {
                      "properties": {
                        "version": { "const": 1 },
                        "kind": { "const": "B" },
                        "params": { "type": "object", "required": ["beta"] }
                      },
                      "required": ["version", "kind"]
                    }
                  ]
                }
            """.trimIndent(),
            listOf(
                SCHEMA_ENUM_VALUE_NOT_ALLOWED
            ),
            // at the `version` value `2`
            listOf(
                Location(Coordinates(0, 9), Coordinates(0, 10), 9, 10)
            )
        )

        assertEquals("Value must be one of: 1", errors[0].message.toString())
    }

    /**
     * A lone branch keeps its own errors, which say more than the enum error would.
     */
    @Test
    fun testOneOfSingleBranchKeepsItsOwnErrors() {
        val errors = assertKsonSchemaErrors(
            """
                kind: "B"
            """.trimIndent(),
            """
                {
                  "oneOf": [
                    {
                      "properties": { "kind": { "const": "A" }, "name": { "type": "string" } },
                      "required": ["kind", "name"]
                    }
                  ]
                }
            """.trimIndent(),
            listOf(
                SCHEMA_VALUE_NOT_EQUAL_TO_CONST,
                SCHEMA_REQUIRED_PROPERTY_MISSING
            )
        )

        assertContains(errors[1].message.toString(), "name")
    }

    /**
     * No survivor, yet no value every branch rejects: nothing narrows.
     */
    @Test
    fun testOneOfBranchesEliminatedOnDifferentPropertiesReportEveryBranch() {
        val errors = assertKsonSchemaErrors(
            """
                kind: "X"
                mode: "N"
            """.trimIndent(),
            """
                {
                  "oneOf": [
                    {
                      "title": "BranchA",
                      "properties": { "kind": { "const": "A" }, "need_a": { "type": "string" } },
                      "required": ["need_a"]
                    },
                    {
                      "title": "BranchB",
                      "properties": { "mode": { "const": "M" }, "need_b": { "type": "string" } },
                      "required": ["need_b"]
                    }
                  ]
                }
            """.trimIndent(),
            listOf(
                SCHEMA_ONE_OF_VALIDATION_FAILED,
                SCHEMA_SUB_SCHEMA_ERRORS
            )
        )

        val report = errors[1].message.toString()
        assertContains(report, "BranchA")
        assertContains(report, "BranchB")
    }

    /**
     * Every branch of `oneOf: []` vacuously rejects every value; that must not become enum errors
     * listing nothing.
     */
    @Test
    fun testOneOfWithNoBranchesBlamesNoValue() {
        assertKsonSchemaErrors(
            """
                kind: "A"
            """.trimIndent(),
            """
                { "oneOf": [] }
            """.trimIndent(),
            listOf(
                SCHEMA_ONE_OF_VALIDATION_FAILED,
                SCHEMA_SUB_SCHEMA_ERRORS
            )
        )
    }

    @Test
    fun testOneOfMultiValueEnumPinEliminates() {
        val errors = assertKsonSchemaErrors(
            """
                kind: "C"
            """.trimIndent(),
            """
                {
                  "oneOf": [
                    {
                      "properties": {
                        "kind": { "enum": ["A", "B"] },
                        "foo": { "type": "string" }
                      },
                      "required": ["kind", "foo"]
                    },
                    {
                      "properties": { "baz": { "type": "boolean" } },
                      "required": ["baz"]
                    }
                  ]
                }
            """.trimIndent(),
            listOf(
                SCHEMA_REQUIRED_PROPERTY_MISSING
            )
        )

        assertContains(errors[0].message.toString(), "baz")
        assertFalse(errors[0].message.toString().contains("foo"))
    }

    /**
     * Survivors {B, C} are refined by presence of `kind`, which B knows and C doesn't.
     */
    @Test
    fun testOneOfEliminationRefinesPresenceMatch() {
        val errors = assertKsonSchemaErrors(
            """
                kind: "X"
            """.trimIndent(),
            """
                {
                  "oneOf": [
                    {
                      "title": "BranchA",
                      "properties": { "kind": { "const": "A" }, "need_a": { "type": "string" } },
                      "required": ["kind", "need_a"]
                    },
                    {
                      "title": "BranchB",
                      "properties": { "kind": { "type": "string" }, "need_b": { "type": "string" } },
                      "required": ["kind", "need_b"]
                    },
                    {
                      "title": "BranchC",
                      "properties": { "other": { "type": "string" }, "need_c": { "type": "string" } },
                      "required": ["other", "need_c"]
                    }
                  ]
                }
            """.trimIndent(),
            listOf(
                SCHEMA_REQUIRED_PROPERTY_MISSING
            )
        )

        assertContains(errors[0].message.toString(), "need_b")
        assertFalse(errors[0].message.toString().contains("need_a"))
        assertFalse(errors[0].message.toString().contains("need_c"))
    }

    /**
     * `kind` is known only by branch A, so presence alone would match the branch `kind: "X"`
     * just eliminated.
     */
    @Test
    fun testOneOfPresenceCannotResurrectEliminatedBranch() {
        val errors = assertKsonSchemaErrors(
            """
                kind: "X"
            """.trimIndent(),
            """
                {
                  "oneOf": [
                    {
                      "title": "BranchA",
                      "properties": { "kind": { "const": "A" }, "need_a": { "type": "string" } },
                      "required": ["kind", "need_a"]
                    },
                    {
                      "title": "BranchB",
                      "properties": { "sel_b": { "type": "string" }, "need_b": { "type": "string" } },
                      "required": ["sel_b", "need_b"]
                    },
                    {
                      "title": "BranchC",
                      "properties": { "sel_c": { "type": "string" }, "need_c": { "type": "string" } },
                      "required": ["sel_c", "need_c"]
                    }
                  ]
                }
            """.trimIndent(),
            listOf(
                SCHEMA_ONE_OF_VALIDATION_FAILED,
                SCHEMA_SUB_SCHEMA_ERRORS
            )
        )

        val report = errors[1].message.toString()
        assertContains(report, "BranchB")
        assertContains(report, "BranchC")
        assertFalse(report.contains("BranchA"))
    }

    /**
     * `enum: []` admits nothing, so carrying the property at all eliminates the branch.
     */
    @Test
    fun testOneOfEmptyEnumPinEliminatesWhenPropertyPresent() {
        val errors = assertKsonSchemaErrors(
            """
                kind: "anything"
            """.trimIndent(),
            """
                {
                  "oneOf": [
                    {
                      "properties": {
                        "kind": { "enum": [] },
                        "foo": { "type": "string" }
                      },
                      "required": ["kind", "foo"]
                    },
                    {
                      "properties": { "bar": { "type": "boolean" } },
                      "required": ["bar"]
                    }
                  ]
                }
            """.trimIndent(),
            listOf(
                SCHEMA_REQUIRED_PROPERTY_MISSING
            )
        )

        assertContains(errors[0].message.toString(), "bar")
        assertFalse(errors[0].message.toString().contains("foo"))
    }
}
