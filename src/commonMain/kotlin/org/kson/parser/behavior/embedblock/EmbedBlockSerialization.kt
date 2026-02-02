package org.kson.parser.behavior.embedblock

/**
 * An Embed Block is equivalent to an object with a string property named [EmbedObjectKeys.EMBED_CONTENT] and NO
 * other properties except (optionally) [EmbedObjectKeys.EMBED_TAG].  Objects of this shape can be serialized to
 * and from our Embed Block syntax without any loss or corruption of the data.
 *
 * The embed block syntax may be considered a "view" on objects of this shape.
 */
enum class EmbedObjectKeys(val key: String) {
    EMBED_TAG("embedTag"),
    EMBED_CONTENT("embedContent");
}
