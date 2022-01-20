package org.kson.jetbrains.parser

import com.intellij.lang.ASTNode
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiParser
import com.intellij.psi.tree.IElementType

class KsonParser : PsiParser {
    override fun parse(root: IElementType, builder: PsiBuilder): ASTNode {
        val rootMarker = builder.mark()

        // jetbrains todo this is a straight walk of the lexed tokens---parse an actual psi tree
        while (!builder.eof()) {
            builder.advanceLexer()
        }

        rootMarker.done(root)

        return builder.treeBuilt
    }
}