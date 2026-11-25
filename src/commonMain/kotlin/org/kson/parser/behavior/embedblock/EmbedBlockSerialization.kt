package org.kson.parser.behavior.embedblock

/**
 * An Embed Block is equivalent to an object with exactly two string properties: [EmbedObjectKeys.EMBED_TAG] and
 * [EmbedObjectKeys.EMBED_CONTENT].  Objects of this shape can be serialized to and from our Embed Block syntax
 * without any loss or corruption of the data.
 *
 * The embed block syntax may be considered a "view" on objects of this shape.
 */
enum class EmbedObjectKeys(val key: String) {
    EMBED_TAG("embedTag"),
    EMBED_CONTENT("embedContent");
}
