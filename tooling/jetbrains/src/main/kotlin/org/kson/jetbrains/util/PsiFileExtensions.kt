package org.kson.jetbrains.util

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.elementType
import com.intellij.psi.util.findParentInFile

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