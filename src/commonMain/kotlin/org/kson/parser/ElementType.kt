package org.kson.parser

import org.kson.parser.behavior.embedblock.EmbedDelim

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
    // .
    DOT,
    // =
    END_DASH,
    // ,
    COMMA,
    // lines starting with `#`
    COMMENT,
    /**
     * Opening delimiter for an embed block, either `%` or `$`, see [EmbedDelim.Percent] and [EmbedDelim.Dollar]
     */
    EMBED_OPEN_DELIM,
    /**
     * Closing delimiter for an embed block, either `%%` or `$$`, matches the [EMBED_OPEN_DELIM] for the block it closes
     */
    EMBED_CLOSE_DELIM,
    /**
     * The line of text starting at an embed block's [EMBED_OPEN_DELIM], "tagging" that embedded content
     */
    EMBED_TAG,
    /**
     * The divider between the [EMBED_TAG] and [EMBED_METADATA]
     */
    EMBED_TAG_STOP,
    /**
     * The part of the [EMBED_TAG] which can be used as metadata
     */
    EMBED_METADATA,
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
    UNQUOTED_STRING,
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
    STRING_CONTENT,
    /**
     * Control character prohibited from appearing in a Kson [String]
     */
    STRING_ILLEGAL_CONTROL_CHARACTER,
    /**
     * A unicode escape sequence embedded in a [STRING_CONTENT] as "\uXXXX", where "X" is a hex digit.
     * Used to give helpful errors to the user when their escape sequence is incorrect.
     */
    STRING_UNICODE_ESCAPE,
    /**
     * A "\x" escape embedded in a [STRING_CONTENT], where "x" is a legal escape (see [validStringEscapes])
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
    OBJECT_KEY,
    DASH_LIST,
    DASH_DELIMITED_LIST,
    BRACKET_LIST,
    LIST_ELEMENT,
    OBJECT,
    OBJECT_PROPERTY,
    QUOTED_STRING,
    ROOT
}
