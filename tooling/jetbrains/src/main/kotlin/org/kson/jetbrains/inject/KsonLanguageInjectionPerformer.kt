package org.kson.jetbrains.inject

import com.intellij.lang.injection.general.Injection
import com.intellij.lang.injection.general.LanguageInjectionPerformer
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.psi.PsiElement
import org.kson.jetbrains.psi.KsonEmbedContent

/**
 * Language injection performer for KSON embed content blocks.
 * Handles the actual injection mechanics using the existing robust infrastructure.
 */
class KsonLanguageInjectionPerformer : LanguageInjectionPerformer {
    override fun isPrimary(): Boolean {
        return true
    }

    override fun performInjection(registrar: MultiHostRegistrar, injection: Injection, context: PsiElement): Boolean {
        if (context !is KsonEmbedContent || !context.isValidHost) {
            return false
        }

        // Get the ranges that will be used for injection using existing infrastructure
        val ranges = context.indentHandler.getUntrimmedRanges(context)
        if (ranges.isEmpty()) return false

        val language = injection.injectedLanguage ?: return false
        registrar.startInjecting(language)

        // Process each range individually to maintain proper mapping
        for (range in ranges) {
            registrar.addPlace(
                injection.prefix,
                injection.suffix,
                context,
                range
            )
        }
        
        registrar.doneInjecting()
        return true
    }
}