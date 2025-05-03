package org.kson.jetbrains.util

import com.intellij.application.options.CodeStyle
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.elementType
import com.intellij.psi.util.findParentInFile
import org.kson.tools.IndentType

/**
 * Returns true if the given [offset] within this [PsiFile] lands inside a [PsiElement] of one of the types
 * given in [elements]
 */
fun PsiFile.hasElementAtOffset(offset: Int, elements: Set<IElementType>): Boolean {
    val elemAtFirstDelimChar = this.findElementAt(offset) ?: return false
    val parentFound = elemAtFirstDelimChar.findParentInFile(true) {
        elements.contains(it.elementType)
    }
    return parentFound != null
}

/**
 * Gets the indent type (tabs or spaces) configured for this file
 */
fun PsiFile.getIndentType(): IndentType {
    val indentOptions = CodeStyle.getIndentOptions(this)
    return if (indentOptions.USE_TAB_CHARACTER) {
        IndentType.Tab()
    } else {
        IndentType.Space(indentOptions.INDENT_SIZE)
    }
}
