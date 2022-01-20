package org.kson.jetbrains.parser

import com.intellij.psi.tree.IElementType
import org.kson.jetbrains.KsonLanguage
import org.kson.parser.TokenType

data class KsonElementType(val ksonTokenType: TokenType) : IElementType(ksonTokenType.name, KsonLanguage) {
    override fun toString(): String {
        return "[Kson] ${super.toString()}"
    }
}