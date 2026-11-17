package org.kson.schema

import org.kson.KsonCore
import org.kson.value.KsonObject
import org.kson.value.KsonString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for [IndexedSchemaDocuments.resolveRefIfPresent].
 */
class IndexedSchemaDocumentsTest {

    private fun resolve(schema: String): ResolvedRef {
        val root = KsonCore.parseToAst(schema).ksonValue as KsonObject
        val field = (root.propertyLookup["properties"] as KsonObject).propertyLookup.getValue("field")
        val indexedDocuments = IndexedSchemaDocuments(SchemaDocument(root))
        return indexedDocuments.resolveRefIfPresent(field, indexedDocuments.rootSubschemaBaseUri)
    }

    /** Resolves [schema]'s property `field`, returning its `title` and base URI. */
    private fun resolveField(schema: String): Pair<String?, String> {
        val resolved = resolve(schema)
        val title = (resolved.resolvedValue as KsonObject).propertyLookup["title"] as? KsonString
        return title?.value to resolved.resolvedValueBaseUri.toString()
    }

    /** Whether resolving [schema]'s property `field` ends at a `$ref` it couldn't follow. */
    private fun endsAtUnresolvedRef(schema: String): Boolean = resolve(schema).endsAtUnresolvedRef

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
        assertFalse(endsAtUnresolvedRef(schema))
    }

    @Test
    fun resolvesEachRefUnderTheBaseUriItsSchemaIsReadUnder() {
        // `first` is read under `outer`'s `$id`, while its own is ignored beside its `$ref`: the result is
        // `outer`'s `second`, read under outer.json rather than first.json
        val schema = $$"""
            {
              "properties": { "field": { "$ref": "#/definitions/outer/definitions/first" } },
              "definitions": {
                "outer": {
                  "$id": "http://example.com/outer.json",
                  "definitions": {
                    "first": { "$id": "http://example.com/first.json", "$ref": "#/definitions/second" },
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
        assertTrue(endsAtUnresolvedRef(schema))
    }

    @Test
    fun stopsAtARefToNothing() {
        // `first`'s `$ref` names no schema, so the chain ends at `first`
        val schema = $$"""
            {
              "properties": { "field": { "$ref": "#/$defs/first" } },
              "$defs": {
                "first": { "$ref": "#/$defs/missing", "title": "first" }
              }
            }
        """
        assertEquals("first" to "", resolveField(schema))
        assertTrue(endsAtUnresolvedRef(schema))
    }

    @Test
    fun stopsAtARefWithAMalformedPointer() {
        // `~2` is no JSON Pointer escape, so `first`'s `$ref` names no schema and the chain ends at `first`
        val schema = $$"""
            {
              "properties": { "field": { "$ref": "#/$defs/first" } },
              "$defs": {
                "first": { "$ref": "#/$defs/a~2b", "title": "first" }
              }
            }
        """
        assertEquals("first" to "", resolveField(schema))
        assertTrue(endsAtUnresolvedRef(schema))
    }

    @Test
    fun resolvesAPercentEncodedUtf8Pointer() {
        val schema = $$"""
            {
              "properties": { "field": { "$ref": "#/$defs/caf%C3%A9" } },
              "$defs": { "café": { "title": "café" } }
            }
        """
        assertEquals("café" to "", resolveField(schema))
    }

    @Test
    fun resolvesAPointerWithinTheSchemaItsRefNames() {
        val schema = $$"""
            {
              "properties": { "field": { "$ref": "other.json#/definitions/target" } },
              "definitions": {
                "target": { "title": "root target" },
                "other": {
                  "$id": "other.json",
                  "definitions": { "target": { "title": "other target" } }
                }
              }
            }
        """
        assertEquals("other target" to "other.json", resolveField(schema))
    }

    @Test
    fun doesNotResolveAPlainNameFragmentToAnIdWithoutOne() {
        // `#target` names a fragment of this document, which no `$id` declares; the `$id` `target` names another
        // document
        val schema = $$"""
            {
              "properties": { "field": { "$ref": "#target" } },
              "definitions": {
                "target": { "$id": "target", "title": "target" }
              }
            }
        """
        assertEquals(null to "", resolveField(schema))
        assertTrue(endsAtUnresolvedRef(schema))
    }

    @Test
    fun doesNotReadAColonInARelativePathAsAScheme() {
        // against no base, `./a:b` is the relative path `a:b`, while the `$id` `a:b` is a URI with the scheme `a`
        val schema = $$"""
            {
              "properties": { "field": { "$ref": "./a:b" } },
              "definitions": {
                "target": { "$id": "a:b", "title": "target" }
              }
            }
        """
        assertEquals(null to "", resolveField(schema))
        assertTrue(endsAtUnresolvedRef(schema))
    }

    @Test
    fun doesNotResolveARefToAnIdUnderDefs() {
        // Draft 7 has no `$defs`, so `target` is data, whose `$id` identifies no schema
        val schema = $$"""
            {
              "properties": { "field": { "$ref": "http://example.com/target.json" } },
              "$defs": {
                "target": { "$id": "http://example.com/target.json", "title": "target" }
              }
            }
        """
        assertEquals(null to "", resolveField(schema))
        assertTrue(endsAtUnresolvedRef(schema))
    }

    @Test
    fun resolvesAPointerIntoDefsUnderTheRootsBaseUri() {
        // Draft 7 has no `$defs`, so `outer` is data, whose `$id` sets no base URI for `inner`
        val schema = $$"""
            {
              "$id": "http://example.com/root.json",
              "properties": { "field": { "$ref": "#/$defs/outer/$defs/inner" } },
              "$defs": {
                "outer": {
                  "$id": "http://example.com/outer.json",
                  "$defs": { "inner": { "title": "inner" } }
                }
              }
            }
        """
        assertEquals("inner" to "http://example.com/root.json", resolveField(schema))
    }

    @Test
    fun resolvesAPointerWithinASchemaWithARelativeId() {
        // `other`'s `$id` resolves against the root's once, so `a` is read under sub/other.json rather than
        // sub/sub/other.json
        val schema = $$"""
            {
              "$id": "http://example.com/root.json",
              "properties": { "field": { "$ref": "sub/other.json#/definitions/a" } },
              "definitions": {
                "other": {
                  "$id": "sub/other.json",
                  "definitions": { "a": { "title": "a" } }
                }
              }
            }
        """
        assertEquals("a" to "http://example.com/sub/other.json", resolveField(schema))
    }

    @Test
    fun resolvesAPointerWithinASchemaWithARelativeIdWithoutARootId() {
        val schema = $$"""
            {
              "properties": { "field": { "$ref": "sub/other.json#/definitions/a" } },
              "definitions": {
                "other": {
                  "$id": "sub/other.json",
                  "definitions": { "a": { "title": "a" } }
                }
              }
            }
        """
        assertEquals("a" to "sub/other.json", resolveField(schema))
    }

    @Test
    fun resolvesARefWithoutAFragmentToAnIdWithAnEmptyOne() {
        // `other.json#` identifies the same schema as `other.json`
        val schema = $$"""
            {
              "properties": { "field": { "$ref": "other.json" } },
              "definitions": {
                "other": { "$id": "other.json#", "title": "other" }
              }
            }
        """
        assertEquals("other" to "", resolveField(schema))
    }

    @Test
    fun resolvesAPointerWithinASchemaWhoseIdHasAnEmptyFragment() {
        val schema = $$"""
            {
              "properties": { "field": { "$ref": "other.json#/definitions/target" } },
              "definitions": {
                "other": {
                  "$id": "other.json#",
                  "definitions": { "target": { "title": "target" } }
                }
              }
            }
        """
        assertEquals("target" to "other.json", resolveField(schema))
    }

    @Test
    fun resolvesARefWithDotSegments() {
        val schema = $$"""
            {
              "$id": "http://example.com/schemas/root.json",
              "properties": { "field": { "$ref": "../shared/defs.json#/definitions/target" } },
              "definitions": {
                "shared": {
                  "$id": "http://example.com/shared/defs.json",
                  "definitions": { "target": { "title": "target" } }
                }
              }
            }
        """
        assertEquals("target" to "http://example.com/shared/defs.json", resolveField(schema))
    }

    @Test
    fun resolvesANetworkPathRef() {
        // the ref names a host of its own, keeping only the base's scheme
        val schema = $$"""
            {
              "$id": "http://example.com/root.json",
              "properties": { "field": { "$ref": "//other.example/target.json" } },
              "definitions": {
                "target": { "$id": "http://other.example/target.json", "title": "target" }
              }
            }
        """
        assertEquals("target", resolveField(schema).first)
    }

    @Test
    fun resolvesAUrnRefToAUrnIdReadUnderAnotherBase() {
        // a URN takes nothing from the base it is read under, so the ref and the `$id` name the same schema
        val schema = $$"""
            {
              "$id": "http://example.com/root.json",
              "properties": { "field": { "$ref": "urn:example:target" } },
              "definitions": {
                "nested": {
                  "$id": "http://example.com/nested/nested.json",
                  "definitions": { "target": { "$id": "urn:example:target", "title": "target" } }
                }
              }
            }
        """
        assertEquals("target", resolveField(schema).first)
    }
}
