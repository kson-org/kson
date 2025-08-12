package org.kson.jetbrains.parser

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.ExternalAnnotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import org.kson.KsonCore
import org.kson.jetbrains.psi.KsonPsiFile
import org.kson.parser.LoggedMessage

class KsonValidationAnnotator : ExternalAnnotator<ValidationInfo?, List<LoggedMessage>>() {

    override fun collectInformation(file: PsiFile, editor: Editor, hasErrors: Boolean): ValidationInfo? {
        if (file !is KsonPsiFile) return null

        val text = file.text
        if (text.isBlank()) return null

        return ValidationInfo(text)
    }

    override fun doAnnotate(info: ValidationInfo?): List<LoggedMessage> {
        if (info == null) return emptyList()
        return KsonCore.parseToAst(info.sourceText).messages
    }

    override fun apply(file: PsiFile, annotationResult: List<LoggedMessage>?, holder: AnnotationHolder) {
        if (file !is KsonPsiFile || annotationResult == null) return

        val documentLength = file.textLength

        for (loggedMessage in annotationResult) {
            val location = loggedMessage.location
            val startOffset = location.startOffset.coerceAtLeast(0)
            val endOffset = location.endOffset.coerceAtMost(documentLength)

            if (startOffset >= endOffset || startOffset >= documentLength) continue

            holder.newAnnotation(HighlightSeverity.ERROR, loggedMessage.message.toString())
                .range(TextRange(startOffset, endOffset))
                .create()
        }
    }
}

// Helper class to store information between phases
data class ValidationInfo(
    val sourceText: String
)
