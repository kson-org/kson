package org.kson.uri

/**
 * A URI reference, as [RFC 3986 §4.1](https://datatracker.ietf.org/doc/html/rfc3986#section-4.1) defines it: a URI,
 * or a relative reference that resolving against a base URI completes.  Components hold their text as written,
 * percent-encoding included.  A null component is absent, which an empty one is not: `http://a/b?` has an empty
 * query where `http://a/b` has none.
 *
 * @param scheme null for a relative reference
 */
data class UriReference(
    val scheme: String?,
    val authority: String?,
    val path: String,
    val query: String?,
    val fragment: String?
) {
    /**
     * [reference] resolved against this URI as its base, per
     * [RFC 3986 §5.2](https://datatracker.ietf.org/doc/html/rfc3986#section-5.2).  A reference with a scheme is taken
     * as it is, even one with this URI's scheme, which is the strict reading of §5.2.2.  RFC 3986 asks for an absolute
     * base; a relative one goes through the same steps.
     */
    fun resolve(reference: String): UriReference = resolve(parse(reference))

    private fun resolve(reference: UriReference): UriReference = when {
        reference.scheme != null -> reference.copy(path = removeDotSegments(reference.path))
        reference.authority != null -> reference.copy(scheme = scheme, path = removeDotSegments(reference.path))
        reference.path.isEmpty() -> copy(query = reference.query ?: query, fragment = reference.fragment)
        reference.path.startsWith('/') ->
            copy(path = removeDotSegments(reference.path), query = reference.query, fragment = reference.fragment)
        else ->
            copy(path = removeDotSegments(merge(reference.path)), query = reference.query, fragment = reference.fragment)
    }

    fun withoutFragment(): UriReference = copy(fragment = null)

    /**
     * [relativePath] appended to this URI's path without its last segment, or to `/` when this URI has an authority
     * and an empty path ([RFC 3986 §5.2.3](https://datatracker.ietf.org/doc/html/rfc3986#section-5.2.3))
     */
    private fun merge(relativePath: String): String =
        if (authority != null && path.isEmpty()) {
            "/$relativePath"
        } else {
            path.substring(0, path.lastIndexOf('/') + 1) + relativePath
        }

    /**
     * The text of this reference, recomposed from its components
     * ([RFC 3986 §5.3](https://datatracker.ietf.org/doc/html/rfc3986#section-5.3))
     */
    override fun toString(): String = buildString {
        if (scheme != null) append(scheme).append(':')
        if (authority != null) append("//").append(authority)
        append(path)
        if (query != null) append('?').append(query)
        if (fragment != null) append('#').append(fragment)
    }

    companion object {
        private val schemeSyntax = Regex("[A-Za-z][A-Za-z0-9+.-]*")

        /**
         * Splits [uriReference] into its components
         * ([RFC 3986 §3](https://datatracker.ietf.org/doc/html/rfc3986#section-3)).  Only a reference that starts with
         * a scheme and a colon is a URI (§4.1), so `this:that` has the scheme `this` while `./this:that` is a relative
         * path.  Like the regular expression of
         * [RFC 3986 Appendix B](https://datatracker.ietf.org/doc/html/rfc3986#appendix-B), this splits any text,
         * leaving characters the grammar disallows, such as spaces, in the component they appear in.
         */
        fun parse(uriReference: String): UriReference {
            val scheme = uriReference.substringBefore(':', missingDelimiterValue = "").takeIf { schemeSyntax.matches(it) }
            val afterScheme = if (scheme == null) uriReference else uriReference.substring(scheme.length + 1)
            val (beforeFragment, fragment) = afterScheme.splitAtFirst('#')
            val (hierarchicalPart, query) = beforeFragment.splitAtFirst('?')
            if (!hierarchicalPart.startsWith("//")) {
                return UriReference(scheme, null, hierarchicalPart, query, fragment)
            }
            val authorityAndPath = hierarchicalPart.substring(2)
            val (authority, path) = authorityAndPath.splitAt(authorityAndPath.indexOf('/'))
            return UriReference(scheme, authority, path, query, fragment)
        }
    }
}

private val percentEncodedOctets = Regex("(?:%[0-9A-Fa-f]{2})+")

/**
 * [text] with its percent-encoded octets ([RFC 3986 §2.1](https://datatracker.ietf.org/doc/html/rfc3986#section-2.1))
 * decoded, a run of them as UTF-8 as §2.5 recommends.  A `%` not followed by two hex digits encodes nothing and is
 * kept; octets that aren't UTF-8 decode to U+FFFD.
 */
internal fun percentDecode(text: String): String =
    percentEncodedOctets.replace(text) { octets ->
        octets.value.split('%').drop(1).map { it.toInt(16).toByte() }.toByteArray().decodeToString()
    }

/**
 * [path] with its `.` and `..` segments applied, following the steps of
 * [RFC 3986 §5.2.4](https://datatracker.ietf.org/doc/html/rfc3986#section-5.2.4) in order
 */
private fun removeDotSegments(path: String): String {
    var input = path
    var output = ""
    while (input.isNotEmpty()) {
        when {
            input.startsWith("../") -> input = input.removePrefix("../")
            input.startsWith("./") -> input = input.removePrefix("./")
            input.startsWith("/./") -> input = input.removePrefix("/.")
            input == "/." -> input = "/"
            input.startsWith("/../") -> {
                input = input.removePrefix("/..")
                output = output.withoutLastSegment()
            }
            input == "/.." -> {
                input = "/"
                output = output.withoutLastSegment()
            }
            input == "." || input == ".." -> input = ""
            else -> {
                val (segment, rest) = input.splitAt(input.indexOf('/', startIndex = 1))
                output += segment
                input = rest
            }
        }
    }
    return output
}

/** This path without its last segment and the `/` before it */
private fun String.withoutLastSegment(): String = substring(0, lastIndexOf('/').coerceAtLeast(0))

/** The text before [index] and the text from it on, or all the text and none when [index] is negative */
private fun String.splitAt(index: Int): Pair<String, String> =
    if (index < 0) this to "" else substring(0, index) to substring(index)

/**
 * The text before the first [delimiter] and the text after it, or all the text and null when there is no
 * [delimiter]
 */
private fun String.splitAtFirst(delimiter: Char): Pair<String, String?> {
    val index = indexOf(delimiter)
    return if (index < 0) this to null else substring(0, index) to substring(index + 1)
}
