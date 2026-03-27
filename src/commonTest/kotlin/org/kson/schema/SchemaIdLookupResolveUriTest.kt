package org.kson.schema

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Direct tests for [SchemaIdLookup.Companion.resolveUri].
 *
 * These exercise the RFC 3986 §5.2 transform rules that the schema ref
 * resolver depends on for consistent idMap lookups.
 */
class SchemaIdLookupResolveUriTest {

    private fun resolve(uri: String, base: String): String =
        SchemaIdLookup.resolveUri(uri, base).toString()

    @Test
    fun testRelativeRefAgainstEmptyBase_returnsRefUnchanged() {
        // A bare filename resolved against an empty base should remain unchanged.
        assertEquals("pubmed.schema.kson", resolve("pubmed.schema.kson", ""))
    }

    @Test
    fun testAbsolutePathRef_usedAsIs_inheritsBaseOrigin() {
        // Per RFC 3986 §5.2.2, an absolute-path reference inherits the base's
        // scheme/authority, but its own path overrides the base's path.
        assertEquals("/foo/bar", resolve("/foo/bar", ""))
        assertEquals("http://example.com/foo/bar", resolve("/foo/bar", "http://example.com/a/b"))
    }

    @Test
    fun testEmptyRef_inheritsBasePath() {
        assertEquals("http://example.com/a/b", resolve("", "http://example.com/a/b"))
        assertEquals("", resolve("", ""))
    }

    @Test
    fun testRelativeRefAgainstBaseWithAuthorityAndEmptyPath_prependsSlash() {
        // RFC 3986 §5.2.3: base has authority but empty path — merged path starts with "/".
        assertEquals("http://example.com/foo", resolve("foo", "http://example.com"))
    }

    @Test
    fun testRelativeRefAgainstBaseWithTrailingSlash_appendsAfterLastSlash() {
        assertEquals("http://example.com/a/b/c", resolve("c", "http://example.com/a/b/"))
    }

    @Test
    fun testRelativeRefAgainstBaseWithoutTrailingSlash_replacesLastSegment() {
        // Last segment "b" is replaced by the reference.
        assertEquals("http://example.com/a/c", resolve("c", "http://example.com/a/b"))
    }

    @Test
    fun testRelativeRefAgainstBaseWithNoSlash_discardsEntireBasePath() {
        // RFC 3986 §5.2.3: "excluding the entire base URI path if it does not contain any / characters".
        assertEquals("bar", resolve("bar", "foo"))
    }

    @Test
    fun testFragmentAlwaysComesFromRef() {
        // Pure fragment: inherits the base path.
        assertEquals("http://example.com/a/b#frag", resolve("#frag", "http://example.com/a/b"))
        // Relative path + fragment: merges the path and keeps the ref's fragment.
        assertEquals("http://example.com/a/c#frag", resolve("c#frag", "http://example.com/a/b"))
    }

    @Test
    fun testAbsoluteUriIgnoresBase() {
        assertEquals(
            "http://other.example/x",
            resolve("http://other.example/x", "http://example.com/a/b")
        )
    }
}
