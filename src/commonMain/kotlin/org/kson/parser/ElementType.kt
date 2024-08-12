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
    FALSE,
    IDENTIFIER,
    ILLEGAL_CHAR,
    LIST_DASH,
    NULL,
    NUMBER,
    STRING_QUOTE,
    STRING,
    STRING_ILLEGAL_CONTROL_CHARACTER,
    STRING_UNICODE_ESCAPE,
    STRING_ESCAPE,
    TRUE,
    WHITESPACE,
    EOF
}

/**
 * [ElementType]s for the elements marked by [Parser]
 */
enum class ParsedElementType : ElementType {
    INCOMPLETE,
    ERROR,
    EMBED_BLOCK,
    KEYWORD,
    LIST,
    LIST_ELEMENT,
    OBJECT_DEFINITION,
    OBJECT_INTERNALS,
    OBJECT_PROPERTY,
    ROOT
}
