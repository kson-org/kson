package org.kson.jetbrains.inject

import com.intellij.lang.Language
import com.intellij.lang.LanguageUtil
import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.kson.jetbrains.psi.KsonEmbedBlock
import org.kson.jetbrains.psi.KsonEmbedContent
import org.kson.jetbrains.psi.KsonPsiElement

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

        registrar.startInjecting(language)
        registrar.addPlace(
            null,
            null,
            host,
            TextRange(0, host.textLength)
        )
        registrar.doneInjecting()
    }

    protected open fun findLangForInjection(element: KsonEmbedBlock): Language? {
        val name = element.embedBlockTag ?: return null
        val registeredLanguages = Language.getRegisteredLanguages()
        val language = registeredLanguages.find { it.id.equals(name, ignoreCase = true) }
        return language?.takeIf { LanguageUtil.isInjectableLanguage(it) }
    }
}
