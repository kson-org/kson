package org.kson.parser.behavior.embedblock

import org.kson.ast.StringNodeImpl
import org.kson.ast.EmbedBlockNode

/**
 * An Embed Block is equivalent to an object with a string property named [EmbedObjectKeys.EMBED_CONTENT] and NO
 * other properties except (optionally) [EmbedObjectKeys.EMBED_TAG] or [EmbedObjectKeys.EMBED_METADATA].  Objects
 * of this shape can be serialized to and from our Embed Block syntax without any loss or corruption of the data.
 * The embed block syntax may be considered a "view" on objects of this shape.
 */
enum class EmbedObjectKeys(val key: String) {
    EMBED_TAG("embedTag"),
    EMBED_METADATA("embedMetadata"),
    EMBED_CONTENT("embedContent");

    companion object {
        /**
         * Check whether the given [properties] can be decoded to an [EmbedBlockNode].
         * This is the case when:
         * 1. All values of [properties] are [StringNodeImpl]'s.
         * 2. [properties] contains an [EMBED_CONTENT] key
         * 3. [properties] contains no other keys than [EmbedObjectKeys.entries]
         */
        fun canBeDecoded(properties: Map<String, StringNodeImpl?>): Boolean{
            val allStrings = properties.all { it.value != null }
            val containsContent = properties.containsKey(EMBED_CONTENT.key)
            val hasOnlyEmbedObjectKeys = properties.keys.all { key ->
                EmbedObjectKeys.entries.any { it.key == key }
            }
            return !(!allStrings || !containsContent || !hasOnlyEmbedObjectKeys)
        }
    }
}