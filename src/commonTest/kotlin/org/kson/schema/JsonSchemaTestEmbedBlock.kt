package org.kson.schema

import org.kson.parser.Coordinates
import org.kson.parser.Location
import org.kson.parser.messages.MessageType
import kotlin.test.Test

class JsonSchemaTestEmbedBlock : JsonSchemaTest {
    @Test
    fun testEmbedBlockValidatesAsObject() {
        val schema = """
        {
          "${'$'}schema": "http://json-schema.org/draft-07/schema#",
          "type": "object",
          "properties": {
            "embedTag": {
              "type": "string",
              "const": "sqlite"
            },
            "embedContent": {
              "type": "string"
            }
          },
          "required": ["embedTag", "embedContent"],
          "additionalProperties": false
        }
        """

        assertKsonEnforcesSchema(
            """
            %sqlite
              SELECT * FROM users WHERE active = 1
            %%
            """,
            schema,
            true,
            "EmbedBlock with matching tag should validate"
        )

        assertKsonEnforcesSchema(
            """
            %mysql
              SELECT * FROM users WHERE active = 1
            %%
            """,
            schema,
            false,
            "EmbedBlock with non-matching tag should fail validation"
        )

        assertKsonEnforcesSchema(
            """
            %
              SELECT * FROM users WHERE active = 1
            %%
            """,
            schema,
            false,
            "EmbedBlock without tag should fail validation when tag is required"
        )
    }

    @Test
    fun testEmbedBlockWithMetadataTag() {
        val schema = """
        {
          "${'$'}schema": "http://json-schema.org/draft-07/schema#",
          "type": "object",
          "properties": {
            "embedTag": {
              "type": "string"
            },
            "embedMetadata": {
              "type": "string"
            },
            "embedContent": {
              "type": "string"
            }
          },
          "required": ["embedContent"],
          "additionalProperties": false
        }
        """

        assertKsonEnforcesSchema(
            """
            %sql: production_db
              SELECT COUNT(*) FROM transactions
            %%
            """,
            schema,
            true,
            "EmbedBlock with metadata tag should validate"
        )

        assertKsonEnforcesSchema(
            """
            %: just_metadata
              some content here
            %%
            """,
            schema,
            true,
            "EmbedBlock with only metadata tag should validate"
        )
    }

    @Test
    fun testEmbedBlockInArray() {
        val schema = """
        {
          "${'$'}schema": "http://json-schema.org/draft-07/schema#",
          "type": "array",
          "items": {
            "type": "object",
            "properties": {
              "embedTag": {
                "type": "string",
                "enum": ["sql", "javascript", "python"]
              },
              "embedContent": {
                "type": "string",
                "minLength": 1
              }
            },
            "required": ["embedContent"]
          }
        }
        """

        assertKsonEnforcesSchema(
            """
            [
              %sql
                SELECT * FROM users
              %%,
              %javascript
                console.log("Hello World");
              %%,
              %python
                print("Hello World")
              %%
            ]
            """,
            schema,
            true,
            "Array of embed blocks with valid tags should validate"
        )

        assertKsonEnforcesSchema(
            """
            [
              %ruby
                puts "Hello World"
              %%
            ]
            """,
            schema,
            false,
            "Array with embed block having invalid tag should fail validation"
        )
    }

    @Test
    fun testEmbedBlockErrorLocation() {
        val schema = """
        {
          "${'$'}schema": "http://json-schema.org/draft-07/schema#",
          "type": "object",
          "properties": {
            "embedTag": {
              "type": "string",
              "const": "sql"
            },
            "embedContent": {
              "type": "string"
            }
          },
          "required": ["embedTag", "embedContent"]
        }
        """

        // Test error location when tag doesn't match
        assertKsonSchemaErrorAtLocation(
            """
            %python
              print("Hello")
            %%
            """,
            schema,
            listOf(MessageType.SCHEMA_VALUE_NOT_EQUAL_TO_CONST),
            listOf(
                Location(
                    Coordinates(0, 1),
                    Coordinates(0, 7),
                    1, 7
                )
            ),
            "Tag mismatch error should be reported at the embed block location"
        )

        // Test error location when tag is missing
        assertKsonSchemaErrorAtLocation(
            """
            %
              SELECT * FROM users
            %%
            """,
            schema,
            listOf(MessageType.SCHEMA_REQUIRED_PROPERTY_MISSING),
            listOf(
                Location(
                    Coordinates(0, 0),
                    Coordinates(0, 5),  // Length 5 due to trimToFirstLine
                    0, 5
                )
            ),
            "Missing tag error should be reported at the embed block location"
        )
    }

    @Test
    fun testEmbedBlockInObjectErrorLocation() {
        val schema = """
        {
          "${'$'}schema": "http://json-schema.org/draft-07/schema#",
          "type": "object",
          "properties": {
            "query": {
              "type": "object",
              "properties": {
                "embedTag": {
                  "type": "string",
                  "enum": ["sqlite", "postgres"]
                },
                "embedContent": {
                  "type": "string",
                  "minLength": 10
                }
              },
              "required": ["embedTag", "embedContent"]
            }
          },
          "required": ["query"]
        }
        """

        // Test error location for enum mismatch in nested embed block
        assertKsonSchemaErrorAtLocation(
            """
            {
              query: %mysql
                SELECT id
              %%
            }
            """,
            schema,
            listOf(MessageType.SCHEMA_ENUM_VALUE_NOT_ALLOWED),
            listOf(
                Location(
                    Coordinates(1, 10),
                    Coordinates(1, 15),
                    12, 17
                )
            ),
            "Enum mismatch in nested embed block should report correct location"
        )

        // Test error location for content length violation
        assertKsonSchemaErrorAtLocation(
            """
            {
              query: %sqlite
                SELECT
              %%
            }
            """,
            schema,
            listOf(MessageType.SCHEMA_STRING_LENGTH_TOO_SHORT),
            listOf(
                Location(
                    Coordinates(2, 0),
                    Coordinates(3, 2),
                    19, 32
                )
            ),
            "Content length violation should report at embed block location"
        )
    }

    @Test
    fun testEmbedBlockInArrayErrorLocation() {
        val schema = """
        {
          "${'$'}schema": "http://json-schema.org/draft-07/schema#",
          "type": "array",
          "items": {
            "type": "object",
            "properties": {
              "embedTag": {
                "type": "string",
                "pattern": "^(sql|js)${'$'}"
              },
              "embedContent": {
                "type": "string"
              }
            },
            "required": ["embedTag"]
          }
        }
        """

        // Test error location for pattern mismatch in array
        assertKsonSchemaErrorAtLocation(
            """
            [
              %sql
                SELECT * FROM users
              %%,
              %python
                print("test")
              %%
            ]
            """,
            schema,
            listOf(MessageType.SCHEMA_STRING_PATTERN_MISMATCH),
            listOf(
                Location(
                    Coordinates(4, 3),
                    Coordinates(4, 9),
                    42, 48
                )
            ),
            "Pattern violation in array element should report correct location"
        )
    }

    @Test
    fun testNestedEmbedBlockValidation() {
        val schema = """
        {
          "${'$'}schema": "http://json-schema.org/draft-07/schema#",
          "type": "object",
          "properties": {
            "name": {
              "type": "string"
            },
            "script": {
              "type": "object",
              "properties": {
                "embedTag": {
                  "type": "string",
                  "const": "bash"
                },
                "embedContent": {
                  "type": "string"
                }
              },
              "required": ["embedTag", "embedContent"]
            }
          },
          "required": ["name", "script"]
        }
        """

        assertKsonEnforcesSchema(
            """
            {
              name: "deployment-script",
              script: %bash
                #!/bin/bash
                echo "Deploying application..."
                docker-compose up -d
              %%
            }
            """,
            schema,
            true,
            "Nested embed block with correct tag should validate"
        )

        assertKsonEnforcesSchema(
            """
            {
              name: "deployment-script",
              script: %powershell
                Write-Host "Deploying application..."
              %%
            }
            """,
            schema,
            false,
            "Nested embed block with incorrect tag should fail validation"
        )
    }
}