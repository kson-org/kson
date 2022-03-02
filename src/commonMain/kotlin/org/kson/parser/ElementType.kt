package org.kson.parser

/**
 * [ElementType] unifies the two different types of elements marked by [AstMarker.done]:
 * [TokenType] and [ParsedElementType]
 */
interface ElementType

/**
 * [ElementType]s for the tokens produced by [Lexer]
 */
enum class TokenType : ElementType {
    BRACE_L,
    BRACE_R,
    BRACKET_L,
    BRACKET_R,
    COLON,
    COMMA,
    COMMENT,
    EMBED_END,
    EMBED_START,
    EMBED_TAG,
    EMBED_CONTENT,
    EOF,
    FALSE,
    IDENTIFIER,
    ILLEGAL_TOKEN,
    NULL,
    NUMBER,
    STRING,
    TRUE,
    WHITESPACE
}

/**
 * [ElementType]s for the elements marked by [Parser]
 */
enum class ParsedElementType : ElementType {
    EMBED_BLOCK,
    ERROR,
    LIST,
    OBJECT_DEFINITION,
    OBJECT_INTERNALS,
    OBJECT_NAME,
    PROPERTY,
    ROOT
}
