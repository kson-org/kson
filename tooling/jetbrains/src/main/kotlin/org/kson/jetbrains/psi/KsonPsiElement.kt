package org.kson.jetbrains.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode

open class KsonPsiElement(node: ASTNode) : ASTWrapperPsiElement(node)