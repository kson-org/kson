package org.kson.value.navigation.json_pointer

import org.kson.parser.messages.Message
import org.kson.uri.UriReference
import org.kson.uri.percentDecode
import org.kson.value.navigation.json_pointer.PointerParser.ParseResult

/** The JSON Pointer a URI fragment holds, or what keeps it from being a valid one */
internal sealed interface FragmentPointer {
    data class Valid(val pointer: JsonPointer) : FragmentPointer
    data class Malformed(val message: Message) : FragmentPointer
}

/**
 * The JSON Pointer this URI's fragment holds, read as
 * [RFC 6901 §6](https://datatracker.ietf.org/doc/html/rfc6901#section-6) says: percent-decoded, then parsed.  Null
 * when there is no fragment, or when it is a plain name such as `#foo`, which is an anchor name rather than a pointer.
 */
internal fun UriReference.fragmentPointer(): FragmentPointer? {
    val pointerFragment = fragment?.takeIf { it.isEmpty() || it.startsWith('/') } ?: return null
    val pointerString = percentDecode(pointerFragment)
    return when (val parsed = JsonPointerParser(pointerString).parse()) {
        is ParseResult.Success -> FragmentPointer.Valid(JsonPointer(pointerString))
        is ParseResult.Error -> FragmentPointer.Malformed(parsed.message)
    }
}
