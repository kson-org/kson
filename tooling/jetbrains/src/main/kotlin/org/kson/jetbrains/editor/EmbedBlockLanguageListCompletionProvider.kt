package org.kson.jetbrains.editor

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.lang.Language
import com.intellij.lang.LanguageUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.DeferredIconImpl
import com.intellij.util.ProcessingContext
import javax.swing.Icon

internal class EmbedBlockLanguageListCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        for (language in LanguageUtil.getInjectableLanguages()) {
            val lookupElement = LookupElementBuilder.create(StringUtil.toLowerCase(language.id))
                .withIcon(createLanguageIcon(language))
                .withTypeText(language.displayName, true)
            result.addElement(lookupElement)
        }
    }

    companion object {
        fun createLanguageIcon(language: Language): Icon {
            return DeferredIconImpl(null, language, true) { curLanguage: Language ->
                curLanguage.associatedFileType?.icon
            }
        }
    }
}
