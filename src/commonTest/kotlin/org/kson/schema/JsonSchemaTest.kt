package org.kson.schema

import org.kson.CoreCompileConfig
import org.kson.KsonCore
import org.kson.parser.Location
import org.kson.parser.LoggedMessage
import org.kson.parser.MessageSink
import org.kson.parser.messages.MessageType
import org.kson.validation.SourceContext
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Interface to tie together our Json Schema tests and give a home to our custom assertions for these tests
 */
interface JsonSchemaTest {
    /**
     * Pass a [sourceContext] carrying [org.kson.validation.ValidationMode.PARTIAL] to exercise
     * draft-mode validation; it defaults to a full validation.
     *
     * @param externalSchemaSources the sources of the external schema documents [schemaJson] references, by
     *   retrieval URI
     */
    fun assertKsonEnforcesSchema(ksonSource: String,
                                 schemaJson: String,
                                 shouldAcceptAsValid: Boolean,
                                 message: String? = null,
                                 sourceContext: SourceContext = SourceContext(),
                                 externalSchemaSources: Map<String, String> = emptyMap()) {
        val jsonSchema = assertValidSchema(schemaJson, message, externalSchemaSources)
        val parseResult = KsonCore.parseToAst(
            ksonSource.trimIndent(),
            coreCompileConfig = CoreCompileConfig(schemaJson = jsonSchema, sourceContext = sourceContext)
        )
        // accepted as valid if and only if we parsed without error
        val acceptedAsValid = parseResult.messages.isEmpty()

        assertEquals(
            shouldAcceptAsValid,
            acceptedAsValid,
            message)
    }

    /**
     * Pass a [sourceContext] carrying [org.kson.validation.ValidationMode.PARTIAL] to exercise
     * draft-mode validation; it defaults to a full validation.
     *
     * @param externalSchemaSources the sources of the external schema documents [schemaJson] references, by
     *   retrieval URI
     */
    fun assertKsonSchemaErrors(
        ksonSource: String,
        schemaJson: String,
        expectedParseMessageTypes: List<MessageType>,
        message: String? = null,
        sourceContext: SourceContext = SourceContext(),
        externalSchemaSources: Map<String, String> = emptyMap()): List<LoggedMessage> {
        val jsonSchema = assertValidSchema(schemaJson, externalSchemaSources = externalSchemaSources)
        val parseResult = KsonCore.parseToAst(
            ksonSource.trimIndent(),
            coreCompileConfig = CoreCompileConfig(schemaJson = jsonSchema, sourceContext = sourceContext)
        )

        assertTrue(parseResult.messages.isNotEmpty(), "Expected schema validation errors but got none")
        assertEquals(expectedParseMessageTypes, parseResult.messages.map { it.message.type })
        return parseResult.messages
    }

    fun assertKsonSchemaErrorAtLocation(
        ksonSource: String,
        schemaJson: String,
        expectedParseMessageTypes: List<MessageType>,
        expectedParseMessageLocation: List<Location>,
        message: String? = null
    ): List<LoggedMessage> {
        val messages = assertKsonSchemaErrors(
            ksonSource,
            schemaJson,
            expectedParseMessageTypes,
            message
        )
        assertEquals(expectedParseMessageLocation, messages.map { it.location })
        return messages
    }

    /**
     * Assertion helper for testing that [source] is successfully parsed by the schema parser
     * (produces non-null jsonSchema) with no error messages
     */
    fun assertValidSchema(
        source: String,
        message: String? = null,
        externalSchemaSources: Map<String, String> = emptyMap()
    ): JsonSchema {
        val messageSink = MessageSink()
        val jsonSchema = SchemaParser.parseSchemaRoot(
            indexWithExternalDocuments(source, message, externalSchemaSources),
            messageSink
        )

        val parseMessages = messageSink.loggedMessages()
        assertTrue(
            parseMessages.isEmpty(), "Should have no error messages when parsing succeeds, got: " +
                    parseMessages.joinToString("\n") + message
        )
        assertNotNull(jsonSchema, "Should produce a non-null schema when parsing succeeds $message")

        return jsonSchema
    }

    /** The schema [source] holds, indexed with the documents it references, loaded from [externalSchemaSources] */
    private fun indexWithExternalDocuments(
        source: String,
        message: String?,
        externalSchemaSources: Map<String, String>
    ): IndexedSchemaDocuments {
        val parsedSource = KsonCore.parseToAst(source)
        val rootValue =
            assertNotNull(parsedSource.ksonValue, "Schema should parse, got: ${parsedSource.messages} $message")
        val root = SchemaDocument(rootValue)
        val documents = mutableListOf<SchemaDocument>()
        var indexedDocuments = IndexedSchemaDocuments(root)
        while (indexedDocuments.missingDocumentUris.isNotEmpty()) {
            for (uri in indexedDocuments.missingDocumentUris) {
                val documentSource =
                    assertNotNull(externalSchemaSources[uri.toString()], "Missing schema fixture: $uri $message")
                val documentValue = assertNotNull(KsonCore.parseToAst(documentSource).ksonValue, uri.toString())
                documents.add(SchemaDocument(documentValue, uri))
            }
            indexedDocuments = IndexedSchemaDocuments(root, documents)
        }
        return indexedDocuments
    }
}
