package org.kson.jetbrains.formatter

import com.intellij.formatting.service.AsyncDocumentFormattingService
import com.intellij.formatting.service.AsyncFormattingRequest
import com.intellij.formatting.service.FormattingService.Feature
import com.intellij.psi.PsiFile
import org.kson.Kson
import org.kson.KsonFormatterConfig
import org.kson.jetbrains.file.KsonFileType
import org.kson.jetbrains.psi.KsonPsiFile

class KsonExternalFormatter : AsyncDocumentFormattingService() {
    override fun canFormat(file: PsiFile): Boolean {
        return file is KsonPsiFile
    }

    override fun getFeatures(): Set<Feature> = setOf(Feature.FORMAT_FRAGMENTS)
    override fun getNotificationGroupId(): String = KsonFileType.name
    override fun getName(): String = KsonFileType.name

    override fun createFormattingTask(request: AsyncFormattingRequest): FormattingTask? {
        val formattingContext = request.context
        val file = request.ioFile ?: return null

        val indentSize = formattingContext.codeStyleSettings
            .getIndentOptions(formattingContext.containingFile.fileType)
            .INDENT_SIZE
            
        return object : FormattingTask {
            override fun run() {
                val source = file.readText()
                val formatted = Kson.format(source, KsonFormatterConfig(indentSize))
                request.onTextReady(formatted)
            }

            override fun cancel(): Boolean = false

            override fun isRunUnderProgress(): Boolean = true
        }
    }
} 
