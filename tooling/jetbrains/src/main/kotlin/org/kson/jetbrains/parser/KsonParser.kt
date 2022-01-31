package org.kson.jetbrains.parser

import com.intellij.lang.ASTNode
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiParser
import com.intellij.psi.tree.IElementType
import org.kson.jetbrains.parser.ParsedElementType.*
import org.kson.parser.TokenType.*
import org.kson.parser.Parser

/**
 * jbparser todo duping the grammar doc from [Parser] for now, but we will be hopefully unifying these parsers soon
 * ```
 * kson -> (objectInternals | value) EOF ;
 * objectInternals -> ( keyword value ","? )* ;
 * value -> objectDefinition
 *        | list
 *        | literal
 *        | embedBlock ;
 * objectDefinition -> ( objectName | "" ) "{" objectInternals "}" ;
 * list -> "[" (value ",")* value? "]"
 * keyword -> ( IDENTIFIER | STRING ) ":" ;
 * literal -> STRING, NUMBER, "true", "false", "null" ;
 * embeddedBlock -> "```" (embedTag) NEWLINE CONTENT "```" ;
 * ```
 */
class KsonParser : PsiParser {
    override fun parse(root: IElementType, builder: PsiBuilder): ASTNode {
        parseKson(builder)
        return builder.treeBuilt
    }

    /**
     * kson -> (objectInternals | value) EOF ;
     */
    private fun parseKson(builder: PsiBuilder) {
        if (builder.eof()) {
            return
        }

        val ksonRootMarker = builder.mark()

        // dm todo if not objectInternals, then value
        parseObjectInternals(builder)

        // jbparser todo this consumes any remaining/unparsed tokens so we have a
        //            "working" parser while we build out its details
        while (!builder.eof()) {
            builder.advanceLexer()
        }

        ksonRootMarker.done(elem(ROOT))
    }

    /**
     * objectInternals -> ( keyword value ","? )* ;
     */
    private fun parseObjectInternals(builder: PsiBuilder) {
        val propertyMark = builder.mark()
        keyword(builder)
        value(builder)
        propertyMark.done(elem(PROPERTY))
    }

    /**
     * value -> objectDefinition
     *        | list
     *        | literal
     *        | embedBlock ;
     */
    private fun value(builder: PsiBuilder) {
        val valueMark = builder.mark()
        literal(builder)
        valueMark.done(elem(VALUE))
    }

    /**
     * literal -> STRING, NUMBER, "true", "false", "null" ;
     */
    private fun literal(builder: PsiBuilder) {
        if (setOf(
                elem(STRING),
                elem(IDENTIFIER),
                elem(NUMBER),
                elem(TRUE),
                elem(FALSE),
                elem(NULL)
            ).any { it == builder.tokenType }
        ) {
            builder.advanceLexer()
        }
    }

    /**
     * keyword -> ( IDENTIFIER | STRING ) ":" ;
     */
    private fun keyword(builder: PsiBuilder) {
        val keywordMark = builder.mark()

        if ((builder.tokenType == elem(IDENTIFIER) || builder.tokenType == elem(STRING))
            && builder.lookAhead(1) == elem(COLON)
        ) {
            // consume ( IDENTIFIER | STRING )
            builder.advanceLexer()
            // consume COLON
            builder.advanceLexer()
            keywordMark.done(elem(KEYWORD))
        } else {
            // not a keyword
            keywordMark.drop()
        }
    }
}