package org.kson.schema

import org.kson.KsonCore
import org.kson.value.KsonObject
import org.kson.value.KsonString
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests for [SchemaIdLookup.resolveRefIfPresent].
 */
class SchemaIdLookupTest {

    /** Resolves [schema]'s property `field`, returning its `title` and base URI. */
    private fun resolveField(schema: String): Pair<String?, String> {
        val root = KsonCore.parseToAst(schema).ksonValue as KsonObject
        val field = (root.propertyLookup["properties"] as KsonObject).propertyLookup.getValue("field")
        val resolved = SchemaIdLookup(root).resolveRefIfPresent(field, "")
        val title = (resolved.resolvedValue as KsonObject).propertyLookup["title"] as? KsonString
        return title?.value to resolved.resolvedValueBaseUri
    }

    @Test
    fun followsAChainOfRefs() {
        // `first`'s title is ignored because it sits beside a `$ref`
        val schema = $$"""
            {
              "properties": { "field": { "$ref": "#/$defs/first" } },
              "$defs": {
                "first": { "$ref": "#/$defs/second", "title": "first" },
                "second": { "title": "second" }
              }
            }
        """
        assertEquals("second" to "", resolveField(schema))
    }

    @Test
    fun resolvesEachRefUnderTheBaseUriItsSchemaIsReadUnder() {
        // `first` is read under `outer`'s `$id`, while its own is ignored beside its `$ref`: the result is
        // `outer`'s `second`, read under outer.json rather than first.json
        val schema = $$"""
            {
              "properties": { "field": { "$ref": "#/$defs/outer/$defs/first" } },
              "$defs": {
                "outer": {
                  "$id": "http://example.com/outer.json",
                  "$defs": {
                    "first": { "$id": "http://example.com/first.json", "$ref": "#/$defs/second" },
                    "second": { "title": "outer second" }
                  }
                },
                "second": { "title": "root second" }
              }
            }
        """
        assertEquals("outer second" to "http://example.com/outer.json", resolveField(schema))
    }

    @Test
    fun stopsAtARefLeadingBackIntoTheChain() {
        // `second`'s `$ref` leads back to `first`, which the chain already holds, so the chain ends
        // at `second`
        val schema = $$"""
            {
              "properties": { "field": { "$ref": "#/$defs/first" } },
              "$defs": {
                "first": { "$ref": "#/$defs/second", "title": "first" },
                "second": { "$ref": "#/$defs/first", "title": "second" }
              }
            }
        """
        assertEquals("second" to "", resolveField(schema))
    }
}
