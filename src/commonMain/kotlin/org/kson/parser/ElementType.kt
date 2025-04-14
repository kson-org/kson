package org.kson.parser

/**
 * [ElementType] unifies the two different types of elements marked by [AstMarker.done]:
 * [TokenType] and [ParsedElementType]
 */
interface ElementType

/**
 * [ElementType]s for the tokens produced by [Lexer].
 *
 * Note: these generally correspond to the terminals in the Kson grammar documented on the [Parser] class, though
 *   some are produced by [Lexer] for the purpose of helping the parser produce more effective help/errors for
 *   the end user
 */
enum class TokenType : ElementType {
    // {
    CURLY_BRACE_L,
    // }
    CURLY_BRACE_R,
    // [
    SQUARE_BRACKET_L,
    // ]
    SQUARE_BRACKET_R,
    // <
    ANGLE_BRACKET_L,
    // >
    ANGLE_BRACKET_R,
    // :
    COLON,
    // ,
    COMMA,
    // lines starting with `#`
    COMMENT,
    /**
     * Opening delimiter for an embed block, either `%%` or `$$`, see [EmbedDelim.Percent] and [EmbedDelim.Dollar]
     */
    EMBED_OPEN_DELIM,
    /**
     * Closing delimiter for an embed block, matches the [EMBED_OPEN_DELIM] for the block it closes
     */
    EMBED_CLOSE_DELIM,
    /**
     * A single `%` or `$` where an [EMBED_OPEN_DELIM] should be. Used to give helpful errors to the user.
     */
    EMBED_DELIM_PARTIAL,
    /**
     * The line of text starting at an embed block's [EMBED_OPEN_DELIM], "tagging" that embedded content
     */
    EMBED_TAG,
    /**
     * The newline that ends the "preamble" of an embed block (i.e. the [EMBED_OPEN_DELIM] and possibly an [EMBED_TAG])
     * [EMBED_CONTENT] begins on the line immediately after the [EMBED_PREAMBLE_NEWLINE]
     */
    EMBED_PREAMBLE_NEWLINE,
    /**
     * The content of an [EMBED_OPEN_DELIM]/[EMBED_CLOSE_DELIM] delimited embed block
     */
    EMBED_CONTENT,
    // false
    FALSE,
    /**
     * An unquoted alpha-numeric-with-underscores string (must not start with a number)
     */
    IDENTIFIER,
    /**
     * A char completely outside the Kson grammar. Used to give helpful errors to the user.
     */
    ILLEGAL_CHAR,
    /**
     * The `-` denoting a dashed list element
     */
    LIST_DASH,
    // null
    NULL,
    /**
     * A number, to be parsed by [NumberParser]
     */
    NUMBER,
    // " or ' opening a string
    STRING_OPEN_QUOTE,
    // " or ' closing a string
    STRING_CLOSE_QUOTE,
    /**
     * A [STRING_OPEN_QUOTE]/[STRING_CLOSE_QUOTE] delimited chunk of text, i.e. "This is a string"
     */
    STRING,
    /**
     * Control character prohibited from appearing in a Kson [String]
     */
    STRING_ILLEGAL_CONTROL_CHARACTER,
    /**
     * A unicode escape sequence embedded in a [STRING] as "\uXXXX", where "X" is a hex digit.
     * Used to give helpful errors to the user when their escape sequence is incorrect.
     */
    STRING_UNICODE_ESCAPE,
    /**
     * A "\x" escape embedded in a [STRING], where "x" is a legal escape (see [validStringEscapes])
     * Used to give helpful errors to the user when their escape is incorrect.
     */
    STRING_ESCAPE,
    // true
    TRUE,
    /**
     * Any whitespace such as spaces, newlines and tabs
     */
    WHITESPACE,
    /**
     * A special token to denote the end of a "file" or token stream
     */
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
