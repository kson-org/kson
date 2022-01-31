package org.kson.jetbrains.parser

import com.intellij.psi.tree.IElementType
import org.kson.jetbrains.KsonLanguage
import org.kson.parser.TokenType
import org.kson.parser.Lexer

/**
 * Get the element type corresponding to a lexed [TokenType] produced by [Lexer].  See also the [elem] override
 * that takes a [ParsedElementType]
 */
fun elem(lexedElementType: TokenType): IElementType {
    return LEXED_ELEMENT[lexedElementType]
        ?: throw RuntimeException("Bug: all " + ParsedElementType::class.simpleName + " items should be defined in this map")
}

/**
 * Get the element type corresponding to a compound token defined in [KsonParser].  Compare this with the tokens
 * from the [elem] override that takes a [TokenType]
 */
fun elem(parsedElementType: ParsedElementType): IElementType {
    return PARSED_ELEMENT[parsedElementType]
        ?: throw RuntimeException("Bug: all " + ParsedElementType::class.simpleName + " items should be defined in this map")
}

/**
 * Expose the part of [LexedElementType]'s interface needed externally here so that class can be
 * private (See [LexedElementType] for details)
 */
interface KsonLexedElementType {
    val tokenType: TokenType
}

/**
 * Enumerate the types of tokens our [KsonParser] defines
 */
enum class ParsedElementType {
    ROOT,
    PROPERTY,
    KEYWORD,
    VALUE
}

/**
 * We want a set of [IElementType]s defined that directly correspond to our [TokenType]s so that we can
 * easily define them in [KsonLexer.getTokenType]
 */
private val LEXED_ELEMENT: Map<TokenType, IElementType> = TokenType.values()
    .associateWith {
        LexedElementType(it)
    }

/**
 * Our token types that we produce in [KsonParser]
 */
private val PARSED_ELEMENT: Map<ParsedElementType, IElementType> =
    ParsedElementType.values().associateWith { CompositeElementType(it) }

/**
 * [LexedElementType] is private in this file so that we _only_ ever create these tokens once, statically.  This is
 * needed because [IElementType] uses a "registry" of tokens created (see [com.intellij.psi.tree.IElementType.ourRegistry])
 * as an optimization, so two different instances are ALWAYS considered different by Intellij internals, even if they
 * have well-defined equals and hashcode
 */
private data class LexedElementType(override val tokenType: TokenType) :
    KsonLexedElementType,
    IElementType("[Kson-lexed] " + tokenType.name, KsonLanguage)

private class CompositeElementType(parsedElementType: ParsedElementType) :
    IElementType("[Kson-parsed] " + parsedElementType.name, KsonLanguage)