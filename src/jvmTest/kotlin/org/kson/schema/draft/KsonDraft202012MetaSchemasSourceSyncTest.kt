package org.kson.schema.draft

import org.kson.KsonCore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class KsonDraft202012MetaSchemasSourceSyncTest {

    @Test
    fun testEachSourceMatchesItsPublishedMetaSchema() {
        for ((id, source) in KsonDraft202012MetaSchemas.sources) {
            // a verbatim copy of the meta-schema published at `id`, filed under that URL's path
            val publishedCopy = "/" + id.removePrefix("https://") + ".json"
            val resource = assertNotNull(javaClass.getResource(publishedCopy), "No copy of $id at $publishedCopy")

            val publishedResult = KsonCore.parseToKson(resource.readText())
            val sourceResult = KsonCore.parseToKson(source.trimIndent())

            assertFalse(publishedResult.hasErrors(), "$publishedCopy failed to parse: ${publishedResult.messages}")
            assertFalse(sourceResult.hasErrors(), "The source of $id failed to parse: ${sourceResult.messages}")
            assertEquals(publishedResult.kson, sourceResult.kson, "The source of $id must match $publishedCopy")
        }
    }
}
