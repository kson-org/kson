package org.kson.jetbrains.highlighter

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement
import org.kson.jetbrains.parser.elem
import org.kson.parser.ParsedElementType

/**
 * [KsonSemanticHighlightAnnotator] provides semantic highlighting for Kson files.
 *
 * Since we don't require any indices we can implement [DumbAware]
 */
class KsonSemanticHighlightAnnotator : Annotator, DumbAware {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        // Implement syntax highlighter that distinguishes regular strings from object keys.
        if (elem(ParsedElementType.OBJECT_KEY) == element.node.elementType){
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION).textAttributes(
                KsonSyntaxHighlighter.getTextAttributesKey(KsonSyntaxHighlighter.KsonColorTag.KSON_OBJECT_KEY)
            ).create()
        }
    }
}
