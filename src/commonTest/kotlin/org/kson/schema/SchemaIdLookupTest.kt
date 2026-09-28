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

    /** Resolves [schema]'s property `p`, returning its `title` and base URI. */
    private fun resolveP(schema: String): Pair<String?, String> {
        val root = KsonCore.parseToAst(schema).ksonValue as KsonObject
        val p = (root.propertyLookup["properties"] as KsonObject).propertyLookup.getValue("p")
        val resolved = SchemaIdLookup(root).resolveRefIfPresent(p, "")
        val title = (resolved.resolvedValue as KsonObject).propertyLookup["title"] as? KsonString
        return title?.value to resolved.resolvedValueBaseUri
    }

    @Test
    fun followsAChainOfRefs() {
        // The validator ignores `a`'s title beside its `$ref`
        val schema = $$"""
            {
              "properties": { "p": { "$ref": "#/$defs/a" } },
              "$defs": { "a": { "$ref": "#/$defs/b", "title": "a" }, "b": { "title": "b" } }
            }
        """
        assertEquals("b" to "", resolveP(schema))
    }

    @Test
    fun resolvesEachRefUnderTheBaseUriItsSchemaIsReadUnder() {
        // `a` is read under `outer`'s `$id`, while its own is ignored beside its `$ref`: the result is
        // `outer`'s `b`, read under outer.json rather than a.json
        val schema = $$"""
            {
              "properties": { "p": { "$ref": "#/$defs/outer/$defs/a" } },
              "$defs": {
                "outer": {
                  "$id": "http://example.com/outer.json",
                  "$defs": {
                    "a": { "$id": "http://example.com/a.json", "$ref": "#/$defs/b" },
                    "b": { "title": "outer b" }
                  }
                },
                "b": { "title": "root b" }
              }
            }
        """
        assertEquals("outer b" to "http://example.com/outer.json", resolveP(schema))
    }

    @Test
    fun stopsAtARefLeadingBackIntoTheChain() {
        // `b`'s `$ref` leads back to `a`, which the chain already holds, so the chain ends at `b`
        val schema = $$"""
            {
              "properties": { "p": { "$ref": "#/$defs/a" } },
              "$defs": {
                "a": { "$ref": "#/$defs/b", "title": "a" },
                "b": { "$ref": "#/$defs/a", "title": "b" }
              }
            }
        """
        assertEquals("b" to "", resolveP(schema))
    }
}
