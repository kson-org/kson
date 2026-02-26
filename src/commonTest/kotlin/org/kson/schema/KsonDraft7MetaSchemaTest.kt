package org.kson.schema

import kotlin.test.Test

class KsonDraft7MetaSchemaTest : JsonSchemaTest {

    private val draft7MetaSchemaRef = $$"""
        {
            "$ref": "http://json-schema.org/draft-07/schema#"
        }
    """

    @Test
    fun testPlainStringDescriptionStillValidates() {
        assertKsonEnforcesSchema(
            """
            {
              "type": "object",
              "description": "A simple person object"
            }
            """,
            draft7MetaSchemaRef,
            true,
            "Plain string description should still validate against the meta-schema"
        )
    }

    @Test
    fun testEmbedBlockDescriptionWithTag() {
        assertKsonEnforcesSchema(
            """
            type: object
            description: %markdown
              A **person** object representing a user in the system.

              Must include at least a `name` and `email`.
            %%
            """,
            draft7MetaSchemaRef,
            true,
            "Embed block description with tag should validate against the meta-schema"
        )
    }

    @Test
    fun testEmbedBlockDescriptionWithoutTag() {
        assertKsonEnforcesSchema(
            """
            type: object
            description: %
              A person object representing a user in the system.
            %%
            """,
            draft7MetaSchemaRef,
            true,
            "Embed block description without tag should validate against the meta-schema"
        )
    }

    @Test
    fun testPlainStringCommentStillValidates() {
        assertKsonEnforcesSchema(
            $$"""
            {
              "type": "string",
              "$comment": "This schema is intentionally permissive"
            }
            """,
            draft7MetaSchemaRef,
            true,
            $$"Plain string $comment should still validate against the meta-schema"
        )
    }

    @Test
    fun testEmbedBlockComment() {
        assertKsonEnforcesSchema(
            $$"""
            type: string
            '$comment': %markdown
              This schema is **intentionally** permissive.

              See the design doc for rationale.
            %%
            """,
            draft7MetaSchemaRef,
            true,
            $$"Embed block $comment should validate against the meta-schema"
        )
    }

    @Test
    fun testArbitraryObjectDescriptionIsRejected() {
        assertKsonEnforcesSchema(
            """
            {
              "type": "object",
              "description": {
                "language": "en",
                "text": "A person object"
              }
            }
            """,
            draft7MetaSchemaRef,
            false,
            "An arbitrary object for description should be rejected by the meta-schema"
        )
    }

    @Test
    fun testEmbedBlockShapeWithExtraPropertiesIsRejected() {
        assertKsonEnforcesSchema(
            """
            {
              "type": "object",
              "description": {
                "embedContent": "some content",
                "embedTag": "markdown",
                "extra": "should not be here"
              }
            }
            """,
            draft7MetaSchemaRef,
            false,
            "Embed-block-shaped object with extra properties should be rejected"
        )
    }

    @Test
    fun testJsonObjectWithEmbedBlockShapeValidates() {
        assertKsonEnforcesSchema(
            """
            {
              "type": "object",
              "description": {
                "embedContent": "A person object representing a user.",
                "embedTag": "markdown"
              }
            }
            """,
            draft7MetaSchemaRef,
            true,
            "A JSON object with the embed block shape should also validate"
        )
    }
}
