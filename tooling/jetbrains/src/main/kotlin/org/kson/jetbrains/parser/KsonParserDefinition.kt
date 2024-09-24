package org.kson.jetbrains.parser

import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet
import org.kson.jetbrains.KsonLanguage
import org.kson.jetbrains.psi.KsonPsiElement
import org.kson.jetbrains.psi.KsonPsiFile
import org.kson.parser.TokenType

class KsonParserDefinition : ParserDefinition {
    override fun createLexer(project: Project?): Lexer {
        return KsonLexer()
    }

    override fun createParser(project: Project?): PsiParser {
        return KsonParser()
    }

    override fun getFileNodeType(): IFileElementType {
        return IFileElementType(KsonLanguage)
    }

    override fun getCommentTokens(): TokenSet {
        return commentTokenSet
    }

    override fun getWhitespaceTokens(): TokenSet {
        return whitespaceTokenSet
    }

    override fun getStringLiteralElements(): TokenSet {
        return stringTokenSet
    }

    override fun createElement(node: ASTNode): PsiElement {
        return KsonPsiElement(node)
    }

    override fun createFile(viewProvider: FileViewProvider): PsiFile {
        return KsonPsiFile(viewProvider)
    }

}

private val commentTokenSet = TokenSet.create(elem(TokenType.COMMENT))
private val whitespaceTokenSet = TokenSet.create(elem(TokenType.WHITESPACE))
private val stringTokenSet = TokenSet.create(elem(TokenType.STRING))