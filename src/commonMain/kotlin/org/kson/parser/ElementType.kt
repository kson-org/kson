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
    CURLY_BRACE_L,
    CURLY_BRACE_R,
    SQUARE_BRACKET_L,
    SQUARE_BRACKET_R,
    ANGLE_BRACKET_L,
    ANGLE_BRACKET_R,
    COLON,
    COMMA,
    COMMENT,
    EMBED_DELIM,
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
