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
import org.kson.parser.messages.MessageSeverity

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
        /**
         * Messages that have not been annotated yet are either:
         * - Messages that are not collected during the parsing phase, i.e.
         * [org.kson.parser.messages.Message.coreParseMessage] is false.
         * - Messages that are not-fatal, and hence [org.kson.parser.messages.MessageType.severity] is not a
         * [MessageSeverity.ERROR]. The [org.kson.parser.AstMarker.error] drops these, so they won't be annotated double.
         */
        annotationResult.filter { !it.message.coreParseMessage || it.message.type.severity != MessageSeverity.ERROR }
            .forEach {
                val startOffset = it.location.startOffset.coerceAtLeast(0)
                val endOffset = it.location.endOffset.coerceAtMost(documentLength)

                if (startOffset >= endOffset || startOffset >= documentLength) return@forEach

                val severity = when (it.message.type.severity) {
                    MessageSeverity.ERROR -> HighlightSeverity.ERROR
                    MessageSeverity.WARNING -> HighlightSeverity.WARNING
                }
                holder.newAnnotation(severity, it.message.toString())
                    .range(TextRange(startOffset, endOffset))
                    .create()
            }
    }
}

// Helper class to store information between phases
data class ValidationInfo(
    val sourceText: String
)
