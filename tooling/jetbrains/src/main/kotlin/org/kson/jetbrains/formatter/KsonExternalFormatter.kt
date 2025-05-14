package org.kson.jetbrains.formatter

import com.intellij.formatting.FormattingContext
import com.intellij.formatting.service.AbstractDocumentFormattingService
import com.intellij.formatting.service.FormattingService.Feature
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import org.kson.jetbrains.psi.KsonPsiFile
import org.kson.tools.IndentType
import org.kson.tools.KsonFormatterConfig
import org.kson.tools.format

class KsonExternalFormatter : AbstractDocumentFormattingService() {
    override fun canFormat(file: PsiFile): Boolean {
        return file is KsonPsiFile
    }

    override fun getFeatures(): Set<Feature> = emptySet()

    override fun formatDocument(
        document: Document,
        formattingRanges: MutableList<TextRange>,
        formattingContext: FormattingContext,
        canChangeWhiteSpaceOnly: Boolean,
        quickFormat: Boolean
    ) {
        val indentOptions = formattingContext.codeStyleSettings
            .getIndentOptions(formattingContext.containingFile.fileType)
        val indentType = if (indentOptions.USE_TAB_CHARACTER) {
            IndentType.Tab()
        } else {
            IndentType.Space(indentOptions.INDENT_SIZE)
        }

        val formatted = format(document.text, KsonFormatterConfig(indentType))
        document.setText(formatted)
    }
}
