package org.kson.jetbrains.editor

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionInitializationContext
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.patterns.PlatformPatterns.psiElement
import org.kson.jetbrains.parser.elem
import org.kson.parser.TokenType

/**
 * Provides completion for language tags in KSON embed blocks.
 * Supports both custom language providers and built-in injectable languages.
 */
class KsonCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            psiElement().withElementType(elem(TokenType.EMBED_TAG)),
            EmbedBlockLanguageListCompletionProvider()
        )
    }

    override fun beforeCompletion(context: CompletionInitializationContext) {
        // Use a newline in the dummy identifier to prevent it from being parsed as part of the tag
        context.dummyIdentifier = "${CompletionInitializationContext.DUMMY_IDENTIFIER}\n"
    }
}