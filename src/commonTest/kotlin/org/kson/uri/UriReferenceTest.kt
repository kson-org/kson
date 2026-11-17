package org.kson.uri

import kotlin.test.Test
import kotlin.test.assertEquals

class UriReferenceTest {

    /** [this]'s components by name, as its text can hide which component a mismatch is in */
    private fun UriReference.components() =
        "scheme=$scheme authority=$authority path=$path query=$query fragment=$fragment"

    private fun assertParsesAs(expected: List<Pair<String, UriReference>>) {
        assertEquals(
            expected.map { (text, uri) -> text to uri.components() },
            expected.map { (text, _) -> text to UriReference.parse(text).components() }
        )
    }

    /** Asserts that resolving each reference in [expected] against [base] gives its paired URI */
    private fun assertResolvesAgainst(base: String, expected: List<Pair<String, String>>) {
        assertEquals(
            expected,
            expected.map { (reference, _) -> reference to UriReference.parse(base).resolve(reference).toString() }
        )
    }

    @Test
    fun parsesTheRfcExampleUris() {
        // the examples of RFC 3986 §1.1.2 and §3
        assertParsesAs(
            listOf(
                "ftp://ftp.is.co.za/rfc/rfc1808.txt" to
                    UriReference("ftp", "ftp.is.co.za", "/rfc/rfc1808.txt", null, null),
                "http://www.ietf.org/rfc/rfc2396.txt" to
                    UriReference("http", "www.ietf.org", "/rfc/rfc2396.txt", null, null),
                "ldap://[2001:db8::7]/c=GB?objectClass?one" to
                    UriReference("ldap", "[2001:db8::7]", "/c=GB", "objectClass?one", null),
                "mailto:John.Doe@example.com" to
                    UriReference("mailto", null, "John.Doe@example.com", null, null),
                "news:comp.infosystems.www.servers.unix" to
                    UriReference("news", null, "comp.infosystems.www.servers.unix", null, null),
                "tel:+1-816-555-1212" to
                    UriReference("tel", null, "+1-816-555-1212", null, null),
                "telnet://192.0.2.16:80/" to
                    UriReference("telnet", "192.0.2.16:80", "/", null, null),
                "urn:oasis:names:specification:docbook:dtd:xml:4.1.2" to
                    UriReference("urn", null, "oasis:names:specification:docbook:dtd:xml:4.1.2", null, null),
                "foo://example.com:8042/over/there?name=ferret#nose" to
                    UriReference("foo", "example.com:8042", "/over/there", "name=ferret", "nose"),
            )
        )
    }

    @Test
    fun tellsAnEmptyComponentFromAMissingOne() {
        assertParsesAs(
            listOf(
                "file:///etc/hosts" to UriReference("file", "", "/etc/hosts", null, null),
                "file:/etc/hosts" to UriReference("file", null, "/etc/hosts", null, null),
                "//" to UriReference(null, "", "", null, null),
                "http://a/b?" to UriReference("http", "a", "/b", "", null),
                "http://a/b" to UriReference("http", "a", "/b", null, null),
                "http://a/b#" to UriReference("http", "a", "/b", null, ""),
                "#" to UriReference(null, null, "", null, ""),
                "" to UriReference(null, null, "", null, null),
            )
        )
    }

    @Test
    fun endsAnAuthorityAtTheQueryFollowingIt() {
        assertParsesAs(listOf("https://other.test?query" to UriReference("https", "other.test", "", "query", null)))
    }

    @Test
    fun parsesUrns() {
        assertParsesAs(
            listOf(
                "urn:uuid:deadbeef-1234-ffff-ffff-4321feebdaed" to
                    UriReference("urn", null, "uuid:deadbeef-1234-ffff-ffff-4321feebdaed", null, null),
                "urn:example:root#/definitions/a" to
                    UriReference("urn", null, "example:root", null, "/definitions/a"),
            )
        )
    }

    @Test
    fun readsAReferenceThatDoesNotStartWithASchemeAsRelative() {
        assertParsesAs(
            listOf(
                "this:that" to UriReference("this", null, "that", null, null),
                "./this:that" to UriReference(null, null, "./this:that", null, null),
                "a/b:c" to UriReference(null, null, "a/b:c", null, null),
                "1a:b" to UriReference(null, null, "1a:b", null, null),
                "?a:b" to UriReference(null, null, "", "a:b", null),
                "#/definitions/a:b" to UriReference(null, null, "", null, "/definitions/a:b"),
                "//a:8080/b" to UriReference(null, "a:8080", "/b", null, null),
            )
        )
    }

    @Test
    fun keepsWhatTheGrammarDisallowsInTheComponentItIsIn() {
        assertParsesAs(
            listOf(
                "a b/c d?e f#g h" to UriReference(null, null, "a b/c d", "e f", "g h"),
                "#a#b" to UriReference(null, null, "", null, "a#b"),
                "#/definitions/100%" to UriReference(null, null, "", null, "/definitions/100%"),
            )
        )
    }

    @Test
    fun recomposesAReferenceIntoTheTextItWasParsedFrom() {
        val texts = listOf(
            "foo://example.com:8042/over/there?name=ferret#nose",
            "urn:example:animal:ferret:nose",
            "file:///etc/hosts",
            "//",
            "http://a/b?",
            "http://a/b#",
            "#",
            "",
            "./this:that",
            "a b/c d?e f#g h",
        )
        assertEquals(texts, texts.map { UriReference.parse(it).toString() })
    }

    @Test
    fun resolvesTheRfcNormalExamples() {
        // RFC 3986 §5.4.1
        assertResolvesAgainst(
            "http://a/b/c/d;p?q",
            listOf(
                "g:h" to "g:h",
                "g" to "http://a/b/c/g",
                "./g" to "http://a/b/c/g",
                "g/" to "http://a/b/c/g/",
                "/g" to "http://a/g",
                "//g" to "http://g",
                "?y" to "http://a/b/c/d;p?y",
                "g?y" to "http://a/b/c/g?y",
                "#s" to "http://a/b/c/d;p?q#s",
                "g#s" to "http://a/b/c/g#s",
                "g?y#s" to "http://a/b/c/g?y#s",
                ";x" to "http://a/b/c/;x",
                "g;x" to "http://a/b/c/g;x",
                "g;x?y#s" to "http://a/b/c/g;x?y#s",
                "" to "http://a/b/c/d;p?q",
                "." to "http://a/b/c/",
                "./" to "http://a/b/c/",
                ".." to "http://a/b/",
                "../" to "http://a/b/",
                "../g" to "http://a/b/g",
                "../.." to "http://a/",
                "../../" to "http://a/",
                "../../g" to "http://a/g",
            )
        )
    }

    @Test
    fun resolvesTheRfcAbnormalExamples() {
        // RFC 3986 §5.4.2, reading `http:g` strictly
        assertResolvesAgainst(
            "http://a/b/c/d;p?q",
            listOf(
                "../../../g" to "http://a/g",
                "../../../../g" to "http://a/g",
                "/./g" to "http://a/g",
                "/../g" to "http://a/g",
                "g." to "http://a/b/c/g.",
                ".g" to "http://a/b/c/.g",
                "g.." to "http://a/b/c/g..",
                "..g" to "http://a/b/c/..g",
                "./../g" to "http://a/b/g",
                "./g/." to "http://a/b/c/g/",
                "g/./h" to "http://a/b/c/g/h",
                "g/../h" to "http://a/b/c/h",
                "g;x=1/./y" to "http://a/b/c/g;x=1/y",
                "g;x=1/../y" to "http://a/b/c/y",
                "g?y/./x" to "http://a/b/c/g?y/./x",
                "g?y/../x" to "http://a/b/c/g?y/../x",
                "g#s/./x" to "http://a/b/c/g#s/./x",
                "g#s/../x" to "http://a/b/c/g#s/../x",
                "http:g" to "http:g",
            )
        )
    }

    @Test
    fun resolvesAgainstABaseWithAnAuthorityAndNoPath() {
        assertResolvesAgainst("http://example.com", listOf("foo" to "http://example.com/foo"))
    }

    @Test
    fun resolvesAgainstABaseEndingInASlash() {
        assertResolvesAgainst("http://example.com/a/b/", listOf("c" to "http://example.com/a/b/c"))
    }

    @Test
    fun dropsTheFragmentOfTheBase() {
        assertResolvesAgainst(
            "http://a/b/c/d;p?q#f",
            listOf(
                "" to "http://a/b/c/d;p?q",
                "#s" to "http://a/b/c/d;p?q#s",
            )
        )
    }

    @Test
    fun resolvesAgainstAUrnBase() {
        assertResolvesAgainst(
            "urn:uuid:deadbeef-1234-ffff-ffff-4321feebdaed",
            listOf(
                "#/definitions/a" to "urn:uuid:deadbeef-1234-ffff-ffff-4321feebdaed#/definitions/a",
                "urn:example:other" to "urn:example:other",
            )
        )
    }

    @Test
    fun resolvesAgainstARelativeBase() {
        assertResolvesAgainst(
            "",
            listOf(
                "other.schema.kson" to "other.schema.kson",
                "/foo/bar" to "/foo/bar",
                "" to "",
                "#/definitions/a" to "#/definitions/a",
                "../other.schema.kson" to "other.schema.kson",
                "http://example.com/a" to "http://example.com/a",
            )
        )
        assertResolvesAgainst(
            "folder/root.schema.kson",
            listOf(
                "other.schema.kson#/definitions/a" to "folder/other.schema.kson#/definitions/a",
                "#foo" to "folder/root.schema.kson#foo",
                "./sub/../other.schema.kson" to "folder/other.schema.kson",
            )
        )
        assertResolvesAgainst("root.schema.kson", listOf("other.schema.kson" to "other.schema.kson"))
        assertResolvesAgainst(
            "//example.test:8080/root.json",
            listOf("//other.test/types.json" to "//other.test/types.json")
        )
    }

    @Test
    fun percentDecodesEncodedOctets() {
        val decodings = listOf(
            "a%7E0b" to "a~0b",
            "a%7e0b" to "a~0b",
            "100%25" to "100%",
            "%2541" to "%41",
            "caf%C3%A9" to "café",
            "café%20" to "café ",
            "no encoding" to "no encoding",
        )
        assertEquals(decodings, decodings.map { (encoded, _) -> encoded to percentDecode(encoded) })
    }

    @Test
    fun keepsAPercentSignThatEncodesNothing() {
        val decodings = listOf(
            "100%" to "100%",
            "%4" to "%4",
            "%zz" to "%zz",
            "%-1" to "%-1",
            "%%41" to "%A",
        )
        assertEquals(decodings, decodings.map { (encoded, _) -> encoded to percentDecode(encoded) })
    }

    @Test
    fun decodesOctetsThatAreNotUtf8ToTheReplacementCharacter() {
        assertEquals("\uFFFD", percentDecode("%FF"))
        assertEquals("a\uFFFD", percentDecode("a%C3"))
    }
}
