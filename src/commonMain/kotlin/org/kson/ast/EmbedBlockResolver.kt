package org.kson.ast

import org.kson.tools.InternalEmbedRule
import org.kson.value.KsonString
import org.kson.value.navigation.KsonValueNavigation
import org.kson.value.navigation.json_pointer.ExperimentalJsonPointerGlobLanguage
import org.kson.value.toKsonValue

/**
 * Result of resolving embed block rules against an AST.
 * Contains a map of StringNode instances to their matching rules.
 * EmbedBlockNode instances don't need tracking - they're already embed blocks.
 */
data class EmbedBlockResolution(
    val stringNodes: Map<StringNode, InternalEmbedRule>
) {
    companion object {
        val EMPTY = EmbedBlockResolution(emptyMap())
    }
}

/**
 * Resolves which string nodes should be formatted as embed blocks based on the provided rules.
 *
 * This function pre-processes the AST to build a map of StringNodes to their matching embed rules,
 * eliminating the need to thread path context through the serialization process.
 *
 * Only StringNode instances need tracking - EmbedBlockNode instances are already embed blocks
 * and will format correctly using their existing tags.
 *
 * @param root The root of the AST to process
 * @param rules The embed block rules to match against document paths
 * @return An EmbedBlockResolution containing the map of StringNodes to their matching rules
 */
@OptIn(ExperimentalJsonPointerGlobLanguage::class)
fun resolveEmbedBlocks(
    root: KsonRoot,
    rules: List<InternalEmbedRule>
): EmbedBlockResolution {
    if (rules.isEmpty()) return EmbedBlockResolution.EMPTY
    val rootValue = root.toKsonValue()
    val stringResult = mutableMapOf<StringNode, InternalEmbedRule>()
    for (rule in rules) {
        val matchingValues = KsonValueNavigation.navigateWithJsonPointerGlob(rootValue, rule.pathPattern)
        for (value in matchingValues) {
            if (value is KsonString) {
                stringResult[value.stringNode] = rule
            }
        }
    }
    return EmbedBlockResolution(stringResult)
}
