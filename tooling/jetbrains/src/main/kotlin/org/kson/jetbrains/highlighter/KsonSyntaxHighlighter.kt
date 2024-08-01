package org.kson.jetbrains.highlighter

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.tree.IElementType
import org.kson.jetbrains.KsonBundle
import org.kson.jetbrains.highlighter.KsonSyntaxHighlighter.KsonColorTag.*
import org.kson.jetbrains.parser.KsonLexedElementType
import org.kson.jetbrains.parser.KsonLexer
import org.kson.parser.TokenType

class KsonSyntaxHighlighter : SyntaxHighlighterBase() {
    override fun getHighlightingLexer(): Lexer {
        return KsonLexer()
    }

    override fun getTokenHighlights(elementType: IElementType): Array<TextAttributesKey> {
        if (elementType is KsonLexedElementType) {
            return when (elementType.tokenType) {
                TokenType.BRACE_L -> getPackedTextAttributes(KSON_BRACE)
                TokenType.BRACE_R -> getPackedTextAttributes(KSON_BRACE)
                TokenType.BRACKET_L -> getPackedTextAttributes(KSON_BRACKET)
                TokenType.BRACKET_R -> getPackedTextAttributes(KSON_BRACKET)
                TokenType.COLON -> getPackedTextAttributes(KSON_COLON)
                TokenType.COMMA -> getPackedTextAttributes(KSON_COMMA)
                TokenType.COMMENT -> getPackedTextAttributes(KSON_COMMENT)
                TokenType.EMBED_END -> getPackedTextAttributes(KSON_DELIMITER)
                TokenType.EMBED_START -> getPackedTextAttributes(KSON_DELIMITER)
                TokenType.EMBED_TAG -> getPackedTextAttributes(KSON_EMBED_TAG)
                TokenType.EMBED_CONTENT -> getPackedTextAttributes(KSON_CONTENT)
                TokenType.FALSE -> getPackedTextAttributes(KSON_KEYWORD)
                TokenType.IDENTIFIER -> getPackedTextAttributes(KSON_IDENTIFIER)
                TokenType.ILLEGAL_CHAR -> getPackedTextAttributes(KSON_INVALID)
                TokenType.LIST_DASH -> getPackedTextAttributes(KSON_BRACKET)
                TokenType.NULL -> getPackedTextAttributes(KSON_KEYWORD)
                TokenType.NUMBER -> getPackedTextAttributes(KSON_NUMBER)
                TokenType.STRING -> getPackedTextAttributes(KSON_CONTENT)
                TokenType.STRING_UNICODE_ESCAPE -> getPackedTextAttributes(KSON_CONTENT)
                TokenType.STRING_ESCAPE -> getPackedTextAttributes(KSON_CONTENT)
                TokenType.STRING_QUOTE -> getPackedTextAttributes(KSON_CONTENT)
                TokenType.STRING_ILLEGAL_CONTROL_CHARACTER -> getPackedTextAttributes(KSON_INVALID)
                TokenType.TRUE -> getPackedTextAttributes(KSON_KEYWORD)
                TokenType.WHITESPACE -> TextAttributesKey.EMPTY_ARRAY
                TokenType.EOF -> TextAttributesKey.EMPTY_ARRAY
            }
        } else {
            return TextAttributesKey.EMPTY_ARRAY
        }
    }

    enum class KsonColorTag(val displayName: String) {
        KSON_BRACE(KsonBundle.message("kson.syntaxHighlighter.brace")),
        KSON_BRACKET(KsonBundle.message("kson.syntaxHighlighter.bracket")),
        KSON_COLON(KsonBundle.message("kson.syntaxHighlighter.colon")),
        KSON_COMMA(KsonBundle.message("kson.syntaxHighlighter.comma")),
        KSON_COMMENT(KsonBundle.message("kson.syntaxHighlighter.comment")),
        KSON_DELIMITER(KsonBundle.message("kson.syntaxHighlighter.delimiter")),
        KSON_EMBED_TAG(KsonBundle.message("kson.syntaxHighlighter.embed_tag")),
        KSON_IDENTIFIER(KsonBundle.message("kson.syntaxHighlighter.identifier")),
        KSON_KEYWORD(KsonBundle.message("kson.syntaxHighlighter.keyword")),
        KSON_INVALID(KsonBundle.message("kson.syntaxHighlighter.invalid")),
        KSON_NUMBER(KsonBundle.message("kson.syntaxHighlighter.number")),
        KSON_CONTENT(KsonBundle.message("kson.syntaxHighlighter.content"));
    }

    companion object {
        private val keyToColorMap = mapOf(
            KSON_BRACE to DefaultLanguageHighlighterColors.BRACES,
            KSON_BRACKET to DefaultLanguageHighlighterColors.BRACES,
            KSON_COLON to DefaultLanguageHighlighterColors.SEMICOLON,
            KSON_COMMA to DefaultLanguageHighlighterColors.PARENTHESES,
            KSON_COMMENT to DefaultLanguageHighlighterColors.BLOCK_COMMENT,
            KSON_DELIMITER to DefaultLanguageHighlighterColors.STRING,
            KSON_EMBED_TAG to DefaultLanguageHighlighterColors.METADATA,
            KSON_IDENTIFIER to DefaultLanguageHighlighterColors.INSTANCE_FIELD,
            KSON_KEYWORD to DefaultLanguageHighlighterColors.KEYWORD,
            KSON_INVALID to HighlighterColors.BAD_CHARACTER,
            KSON_NUMBER to DefaultLanguageHighlighterColors.NUMBER,
            KSON_CONTENT to DefaultLanguageHighlighterColors.STRING,
        )

        private val textAttributesMap = keyToColorMap.entries.associate {
            it.key to TextAttributesKey.createTextAttributesKey(
                it.key.name,
                it.value
            )
        }
        private val packedTextAttributesMap = textAttributesMap.entries.associate { it.key to arrayOf(it.value) }

        fun getTextAttributesKey(colorTag: KsonColorTag): TextAttributesKey {
            return textAttributesMap[colorTag]
                ?: throw RuntimeException("This color tag should have an entry in textAttributesMap: $colorTag")
        }

        private fun getPackedTextAttributes(colorTag: KsonColorTag): Array<TextAttributesKey> {
            return packedTextAttributesMap[colorTag]
                ?: throw RuntimeException("This color tag should have an entry in packedTextAttributesMap: $colorTag")
        }
    }
}
