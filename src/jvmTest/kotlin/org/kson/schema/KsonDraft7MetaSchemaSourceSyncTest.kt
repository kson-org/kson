package org.kson.schema

import org.kson.KsonCore
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KsonDraft7MetaSchemaSourceSyncTest {

    @Test
    fun testSourceMatchesVSCodeSchemaFile() {
        val schemaFile = File("tooling/lsp-clients/vscode/schemas/metaschema.draft7.kson")
        assertTrue(schemaFile.exists(), "Schema file not found at ${schemaFile.absolutePath}")

        val fileResult = KsonCore.parseToKson(schemaFile.readText())
        val sourceResult = KsonCore.parseToKson(KsonDraft7MetaSchema.SOURCE.trimIndent())

        assertFalse(fileResult.hasErrors(), "Schema file failed to parse: ${fileResult.messages}")
        assertFalse(sourceResult.hasErrors(), "SOURCE constant failed to parse: ${sourceResult.messages}")

        assertEquals(
            fileResult.kson,
            sourceResult.kson,
            /**
             * Note: enforcing this with a test is a fairly hacky way to keep these in sync, but since this schema will
             * not change much, this is an easy and clear way to ensure this invariant is maintained
             */
            "KsonDraft7MetaSchema.SOURCE must stay in sync with ${schemaFile.path}"
        )
    }
}
