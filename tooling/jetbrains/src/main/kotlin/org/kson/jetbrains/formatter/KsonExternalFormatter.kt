package org.kson.jetbrains.formatter

import com.intellij.formatting.service.AsyncDocumentFormattingService
import com.intellij.formatting.service.AsyncFormattingRequest
import com.intellij.formatting.service.FormattingService.Feature
import com.intellij.psi.PsiFile
import org.kson.tools.format
import org.kson.jetbrains.file.KsonFileType
import org.kson.jetbrains.psi.KsonPsiFile
import org.kson.tools.IndentType
import org.kson.tools.KsonFormatterConfig

class KsonExternalFormatter : AsyncDocumentFormattingService() {
    override fun canFormat(file: PsiFile): Boolean {
        return file is KsonPsiFile
    }

    override fun getFeatures(): Set<Feature> = emptySet()
    override fun getNotificationGroupId(): String = KsonFileType.name
    override fun getName(): String = KsonFileType.name

    override fun createFormattingTask(request: AsyncFormattingRequest): FormattingTask? {
        val formattingContext = request.context
        val file = request.ioFile ?: return null

        val indentOptions = formattingContext.codeStyleSettings
            .getIndentOptions(formattingContext.containingFile.fileType)
        val indentType = if (indentOptions.USE_TAB_CHARACTER) {
            IndentType.Tab()
        } else {
            IndentType.Space(indentOptions.INDENT_SIZE)
        }
            
        return object : FormattingTask {
            override fun run() {
                val source = file.readText()
                val formatted = format(source, KsonFormatterConfig(indentType))
                request.onTextReady(formatted)
            }

            override fun cancel(): Boolean = false

            override fun isRunUnderProgress(): Boolean = true
        }
    }
}
