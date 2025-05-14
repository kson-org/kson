package org.kson.jetbrains.inject

import com.intellij.lang.Language
import com.intellij.lang.LanguageUtil
import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.kson.jetbrains.psi.*

/**
 * Injector for Kson Embed Content
 *
 * It is capable of injecting code in:
 *
 * * Top-level [org.kson.parser.TokenType.EMBED_CONTENT]'s with support
 * of formatting and correct alignment on enter
 *
 */
open class EmbedContentInjector : MultiHostInjector {

    override fun elementsToInjectIn(): List<Class<out PsiElement>?> {
        return listOf(KsonPsiElement::class.java)
    }

    override fun getLanguagesToInject(registrar: MultiHostRegistrar, host: PsiElement) {
        if (host !is KsonEmbedContent || !host.isValidHost) {
            return
        }

        val embedBlock = host.embedBlock ?: return

        val language = findLangForInjection(embedBlock) ?: return

        // Get the ranges that will be used for injection
        val ranges = host.indentHandler.getUntrimmedRanges(host)
        if (ranges.isEmpty()) return

        registrar.startInjecting(language)

        // Process each range individually to maintain proper mapping
        for (range in ranges) {
            registrar.addPlace(
                null,
                null,
                host,
                range
            )
        }
        registrar.doneInjecting()
    }

    protected open fun findLangForInjection(element: KsonEmbedBlock): Language? {
        val name = element.embedBlockTag ?: return null
        val registeredLanguages = Language.getRegisteredLanguages()
        val language = registeredLanguages.find { it.id.equals(name, ignoreCase = true) }
        return language?.takeIf { LanguageUtil.isInjectableLanguage(it) }
    }
}
