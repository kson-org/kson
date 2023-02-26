package org.kson.jetbrains.parser

import com.intellij.psi.tree.IElementType
import org.kson.collections.toImmutableMap
import org.kson.jetbrains.KsonLanguage
import org.kson.parser.ElementType
import org.kson.parser.TokenType
import org.kson.parser.ParsedElementType

/**
 * Get the [IElementType] instance corresponding to the given [ElementType].
 *
 * We need this indirection because the [IElementType]s we create to be used in parsing must _only ever_ be created
 * once, statically.  This is because [IElementType] uses a "registry" of tokens created
 * (see [com.intellij.psi.tree.IElementType.ourRegistry]) as an optimization, so two different instances are
 * ALWAYS considered different by Intellij internals, even if they have well-defined equals and hashcode.  So:
 *
 * [IElementType]s for Kson are created once in this file, carefully encapsulated to make it difficult to introduce
 * bugs by violating this "only-once-instantiated IElementTypes" invariant, and exposed here in [elem] by "name"
 */
fun elem(elementType: ElementType): IElementType {
    if (elementType is TokenType) {
        return LEXED_ELEMENT[elementType]
            ?: throw RuntimeException("Bug: all " + TokenType::class.simpleName + " items should be defined in this map")
    } else if (elementType is ParsedElementType) {
        return PARSED_ELEMENT[elementType]
            ?: throw RuntimeException("Bug: all " + ParsedElementType::class.simpleName + " items should be defined in this map")
    }

    throw RuntimeException("Bug: all " + ElementType::class.simpleName + " instances should be defined in the above maps")
}

/**
 * Expose the part of [IElementTokenType]'s interface needed externally here so [IElementTokenType] can remain private
 * (See [elem] for details on the careful encapsulation needed in this file)
 */
interface KsonLexedElementType {
    val tokenType: TokenType
}

/**
 * Our static collection of [IElementTokenType] used in parsing.  See the doc on [elem] for notes on why this
 * must be private and these must ONLY be created here
 */
private val LEXED_ELEMENT: Map<TokenType, IElementType> = TokenType.values()
    .associateWith {
        IElementTokenType(it)
    }.toImmutableMap()

/**
 * Our static collection of [IElementParserElementType] used in parsing.  See the doc on [elem] for notes on why this
 * must be private and these must ONLY be created here
 */
private val PARSED_ELEMENT: Map<ParsedElementType, IElementType> =
    ParsedElementType.values().associateWith { IElementParserElementType(it) }.toImmutableMap()

/**
 * A class adapting [TokenType] to [IElementType] for our plugin
 */
private data class IElementTokenType(override val tokenType: TokenType) :
    ElementType by tokenType,
    KsonLexedElementType,
    IElementType("[Kson-lexed] " + tokenType.name, KsonLanguage)

/**
 * A class adapting [ParsedElementType] to [IElementType] for our plugin
 */
private class IElementParserElementType(parsedElementType: ParsedElementType) :
    ElementType by parsedElementType,
    IElementType("[Kson-parsed] " + parsedElementType.name, KsonLanguage)