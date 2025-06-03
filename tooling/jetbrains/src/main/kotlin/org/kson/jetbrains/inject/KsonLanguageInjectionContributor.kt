package org.kson.jetbrains.inject

import com.intellij.lang.Language
import com.intellij.lang.LanguageUtil
import com.intellij.lang.injection.general.Injection
import com.intellij.lang.injection.general.LanguageInjectionContributor
import com.intellij.lang.injection.general.SimpleInjection
import com.intellij.psi.PsiElement
import org.kson.jetbrains.psi.KsonEmbedContent

/**
 * Language injection contributor for KSON embed content blocks.
 * In this class an injection is 'prepared' that is performed by the [KsonLanguageInjectionPerformer]
 */
class KsonLanguageInjectionContributor : LanguageInjectionContributor {
    
    override fun getInjection(context: PsiElement): Injection? {
        // Only inject into KsonEmbedContent elements
        if (context !is KsonEmbedContent || !context.isValidHost) {
            return null
        }

        val embedBlock = context.embedBlock ?: return null
        val languageTag = embedBlock.embedBlockTag ?: return null
        
        // Find the language by tag
        val language = findLanguageForInjection(languageTag) ?: return null
        
        return SimpleInjection(language, "", "", null)
    }
    
    private fun findLanguageForInjection(languageTag: String): Language? {
        val registeredLanguages = Language.getRegisteredLanguages()
        val language = registeredLanguages.find { it.id.equals(languageTag, ignoreCase = true) }
        return language?.takeIf { LanguageUtil.isInjectableLanguage(it) }
    }
}