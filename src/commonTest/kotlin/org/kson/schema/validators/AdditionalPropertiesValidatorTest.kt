package org.kson.schema.validators

import org.kson.schema.JsonSchemaTest
import org.kson.parser.messages.MessageType.SCHEMA_ADDITIONAL_PROPERTIES_NOT_ALLOWED
import org.kson.parser.messages.MessageType.SCHEMA_ADDITIONAL_PROPERTY_SCHEMA_MISMATCH
import org.kson.parser.messages.MessageType.SCHEMA_VALUE_TYPE_MISMATCH
import kotlin.test.Test
import kotlin.test.assertEquals

class AdditionalPropertiesValidatorTest : JsonSchemaTest {
    @Test
    fun testErrorMessageIncludesPropertyName() {
        val errors = assertKsonSchemaErrors(
            """
                {
                    "foo": 1,
                    "unknownProp": 2
                }
            """,
            """
                {
                    "properties": {
                        "foo": {}
                    },
                    "additionalProperties": false
                }
            """,
            listOf(SCHEMA_ADDITIONAL_PROPERTIES_NOT_ALLOWED)
        )

        assertEquals("Additional property 'unknownProp' is not allowed", errors[0].message.toString())
    }

    @Test
    fun testErrorMessageIncludesSchemaTitle() {
        val errors = assertKsonSchemaErrors(
            """
                {
                    "foo": 1,
                    "unknownProp": 2
                }
            """,
            """
                {
                    "title": "TaskModel",
                    "properties": {
                        "foo": {}
                    },
                    "additionalProperties": false
                }
            """,
            listOf(SCHEMA_ADDITIONAL_PROPERTIES_NOT_ALLOWED)
        )

        assertEquals("Additional property 'unknownProp' is not allowed in TaskModel", errors[0].message.toString())
    }

    @Test
    fun testSchemaValidatorEmitsContextualError() {
        val errors = assertKsonSchemaErrors(
            """
                metadata:
                  integration: SNOWFLAKE
            """,
            """
                properties:
                  metadata:
                    type: object
                    additionalProperties:
                      title: MetadataModel
                      type: object
                      .
                    .
                  .
            """,
            listOf(
                SCHEMA_ADDITIONAL_PROPERTY_SCHEMA_MISMATCH,
                SCHEMA_VALUE_TYPE_MISMATCH
            )
        )

        assertEquals("Property 'integration' must conform to 'MetadataModel'", errors[0].message.toString())
        assertEquals("Expected one of: object, but got: string", errors[1].message.toString())
    }

    @Test
    fun testSchemaValidatorWithoutTitleUsesDefault() {
        val errors = assertKsonSchemaErrors(
            """
                metadata:
                  integration: SNOWFLAKE
            """,
            """
                properties:
                  metadata:
                    type: object
                    additionalProperties:
                      type: object
                      .
                    .
                  .
            """,
            listOf(
                SCHEMA_ADDITIONAL_PROPERTY_SCHEMA_MISMATCH,
                SCHEMA_VALUE_TYPE_MISMATCH
            )
        )

        assertEquals("Property 'integration' must conform to 'JSON Object Schema'", errors[0].message.toString())
        assertEquals("Expected one of: object, but got: string", errors[1].message.toString())
    }

    @Test
    fun testCombinatorUsesRefShortNames() {
        // Two lone `$ref`s to untitled `$defs` entries — the combinator description should name them
        // by the last JSON Pointer token (e.g. "TaskGroupModel") rather than the raw ref string.
        // Both branches fail the same way (string is not an object); `OneOfValidator`'s universal-message
        // collapsing and `MessageSink`'s (location, message) dedup each yield a single
        // `SCHEMA_VALUE_TYPE_MISMATCH` in the final list.
        for ((keyword, prefix) in listOf("oneOf" to "one of", "anyOf" to "any of", "allOf" to "all of")) {
            val errors = assertKsonSchemaErrors(
                """
                    task_a: "oops, should be an object"
                """,
                """
                    {
                        "additionalProperties": {
                            "$keyword": [
                                {"${'$'}ref": "#/${'$'}defs/TaskGroupModel"},
                                {"${'$'}ref": "#/${'$'}defs/TaskModel"}
                            ]
                        },
                        "${'$'}defs": {
                            "TaskGroupModel": {"type": "object"},
                            "TaskModel": {"type": "object"}
                        }
                    }
                """,
                listOf(
                    SCHEMA_ADDITIONAL_PROPERTY_SCHEMA_MISMATCH,
                    SCHEMA_VALUE_TYPE_MISMATCH
                )
            )

            assertEquals(
                "Property 'task_a' must conform to '$prefix: TaskGroupModel, TaskModel'",
                errors[0].message.toString()
            )
        }
    }

    @Test
    fun testOneOfMixesTitledAndUntitledBranches() {
        // When one branch has a title and another is a lone ref, we use the title for the titled
        // branch and the pointer tail for the ref — each branch's best available short name.
        val errors = assertKsonSchemaErrors(
            """
                task_a: "oops, should be an object"
            """,
            """
                {
                    "additionalProperties": {
                        "oneOf": [
                            {"title": "NamedModel", "type": "object"},
                            {"${'$'}ref": "#/${'$'}defs/TaskModel"}
                        ]
                    },
                    "${'$'}defs": {
                        "TaskModel": {"type": "object"}
                    }
                }
            """,
            listOf(
                SCHEMA_ADDITIONAL_PROPERTY_SCHEMA_MISMATCH,
                SCHEMA_VALUE_TYPE_MISMATCH
            )
        )

        assertEquals(
            "Property 'task_a' must conform to 'one of: NamedModel, TaskModel'",
            errors[0].message.toString()
        )
    }

    @Test
    fun testCombinatorWithAnonymousBranchFallsBackToGeneric() {
        // Strict all-or-nothing: an anonymous branch (no title, no ref) poisons the combinator
        // description, so we fall back to the generic "JSON Object Schema" rather than emit a
        // partial list that invites the reader to assume those are the only allowed shapes.
        for (keyword in listOf("oneOf", "anyOf", "allOf")) {
            val errors = assertKsonSchemaErrors(
                """
                    task_a: "oops, should be an object"
                """,
                """
                    {
                        "additionalProperties": {
                            "$keyword": [
                                {"${'$'}ref": "#/${'$'}defs/TaskModel"},
                                {"type": "object", "properties": {"foo": {}}}
                            ]
                        },
                        "${'$'}defs": {
                            "TaskModel": {"type": "object"}
                        }
                    }
                """,
                listOf(
                    SCHEMA_ADDITIONAL_PROPERTY_SCHEMA_MISMATCH,
                    SCHEMA_VALUE_TYPE_MISMATCH
                )
            )

            assertEquals(
                "Property 'task_a' must conform to 'JSON Object Schema'",
                errors[0].message.toString()
            )
        }
    }

    @Test
    fun testLoneRefPrefersTargetTitleOverPointerTail() {
        // When the ref's target declares its own `title`, that title wins over the pointer tail —
        // a target that names itself should be trusted over a name scraped from the pointer.
        val errors = assertKsonSchemaErrors(
            """
                task_a: "oops, should be an object"
            """,
            """
                {
                    "additionalProperties": {
                        "oneOf": [
                            {"${'$'}ref": "#/${'$'}defs/task_group"},
                            {"${'$'}ref": "#/${'$'}defs/task"}
                        ]
                    },
                    "${'$'}defs": {
                        "task_group": {"title": "TaskGroupModel", "type": "object"},
                        "task":       {"title": "TaskModel",      "type": "object"}
                    }
                }
            """,
            listOf(
                SCHEMA_ADDITIONAL_PROPERTY_SCHEMA_MISMATCH,
                SCHEMA_VALUE_TYPE_MISMATCH
            )
        )

        assertEquals(
            "Property 'task_a' must conform to 'one of: TaskGroupModel, TaskModel'",
            errors[0].message.toString()
        )
    }

    @Test
    fun testLoneRefOutsideCombinatorUsesTargetTitle() {
        // A lone `$ref` sitting directly under `additionalProperties` (no `oneOf`/`anyOf`/`allOf` wrapper)
        // should still get the nice short name — the target declares `title: TaskModel`, so that wins.
        val errors = assertKsonSchemaErrors(
            """
                task_a: "oops, should be an object"
            """,
            """
                {
                    "additionalProperties": {"${'$'}ref": "#/${'$'}defs/task"},
                    "${'$'}defs": {
                        "task": {"title": "TaskModel", "type": "object"}
                    }
                }
            """,
            listOf(
                SCHEMA_ADDITIONAL_PROPERTY_SCHEMA_MISMATCH,
                SCHEMA_VALUE_TYPE_MISMATCH
            )
        )

        assertEquals("Property 'task_a' must conform to 'TaskModel'", errors[0].message.toString())
    }

    @Test
    fun testLoneRefOutsideCombinatorFallsBackToPointerTail() {
        // Target has no `title`, so we fall back to the last JSON Pointer token ("TaskModel") rather
        // than exposing the raw ref string.
        val errors = assertKsonSchemaErrors(
            """
                task_a: "oops, should be an object"
            """,
            """
                {
                    "additionalProperties": {"${'$'}ref": "#/${'$'}defs/TaskModel"},
                    "${'$'}defs": {
                        "TaskModel": {"type": "object"}
                    }
                }
            """,
            listOf(
                SCHEMA_ADDITIONAL_PROPERTY_SCHEMA_MISMATCH,
                SCHEMA_VALUE_TYPE_MISMATCH
            )
        )

        assertEquals("Property 'task_a' must conform to 'TaskModel'", errors[0].message.toString())
    }

    @Test
    fun testExplicitDescriptionWinsOverCombinatorSynthesis() {
        // If the outer schema declares its own `description`, that should be used verbatim —
        // synthesis only kicks in when neither `description` nor `title` is set.
        val errors = assertKsonSchemaErrors(
            """
                task_a: "oops, should be an object"
            """,
            """
                {
                    "additionalProperties": {
                        "description": "A hand-written explanation",
                        "oneOf": [
                            {"${'$'}ref": "#/${'$'}defs/TaskGroupModel"},
                            {"${'$'}ref": "#/${'$'}defs/TaskModel"}
                        ]
                    },
                    "${'$'}defs": {
                        "TaskGroupModel": {"type": "object"},
                        "TaskModel": {"type": "object"}
                    }
                }
            """,
            listOf(
                SCHEMA_ADDITIONAL_PROPERTY_SCHEMA_MISMATCH,
                SCHEMA_VALUE_TYPE_MISMATCH
            )
        )

        assertEquals(
            "Property 'task_a' must conform to 'A hand-written explanation'",
            errors[0].message.toString()
        )
    }

    @Test
    fun testCombinatorAlongsideOtherValidatorsFallsBackToGeneric() {
        // `synthesizeDescription`'s `singleOrNull()` guard: when the schema carries a `oneOf` *plus*
        // another validator (here, `minProperties`), we don't try to name the combinator — the shape
        // isn't a lone combinator, so we fall through to the generic description.
        val errors = assertKsonSchemaErrors(
            """
                task_a: "oops, should be an object"
            """,
            """
                {
                    "additionalProperties": {
                        "minProperties": 1,
                        "oneOf": [
                            {"${'$'}ref": "#/${'$'}defs/TaskGroupModel"},
                            {"${'$'}ref": "#/${'$'}defs/TaskModel"}
                        ]
                    },
                    "${'$'}defs": {
                        "TaskGroupModel": {"type": "object"},
                        "TaskModel": {"type": "object"}
                    }
                }
            """,
            listOf(
                SCHEMA_ADDITIONAL_PROPERTY_SCHEMA_MISMATCH,
                SCHEMA_VALUE_TYPE_MISMATCH
            )
        )

        assertEquals(
            "Property 'task_a' must conform to 'JSON Object Schema'",
            errors[0].message.toString()
        )
    }

    @Test
    fun testSchemaValidatorPassesValidProperties() {
        assertKsonEnforcesSchema(
            """
                metadata:
                  integration:
                    name: snowflake
            """,
            """
                properties:
                  metadata:
                    type: object
                    additionalProperties:
                      type: object
                      .
                    .
                  .
            """,
            true
        )
    }
}
