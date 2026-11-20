package org.kson.jsonsuite

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The drafts of [JSON-Schema-Test-Suite](https://github.com/json-schema-org/JSON-Schema-Test-Suite) we generate tests for
 *
 * @param release the number the suite's `compatibility` property uses for this draft, see [SchemaTestCompatibility]
 * @param metaSchemaUri the `$schema` designating this draft
 * @param testClassPrefix the name prefix of the test classes generated for this draft
 */
enum class SchemaDraft(val release: Int, val metaSchemaUri: String, val testClassPrefix: String) {
    DRAFT_7(7, "http://json-schema.org/draft-07/schema#", "SchemaDraft7SuiteTest"),
    DRAFT_2020_12(2020, "https://json-schema.org/draft/2020-12/schema", "SchemaDraft2020_12SuiteTest"),
}

/**
 * Designates [draft] as the dialect of [schema] by inserting its `$schema`.  A boolean schema cannot carry one,
 * so it is returned as is.
 */
internal fun schemaForDraft(schema: JsonElement, draft: SchemaDraft): JsonElement {
    return if (schema is JsonObject) {
        JsonObject(mapOf("\$schema" to JsonPrimitive(draft.metaSchemaUri)) + schema)
    } else {
        schema
    }
}
