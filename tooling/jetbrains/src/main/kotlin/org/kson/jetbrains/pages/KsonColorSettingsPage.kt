package org.kson.jetbrains.pages

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import org.kson.jetbrains.KsonBundle
import org.kson.jetbrains.KsonIcons
import org.kson.jetbrains.highlighter.KsonSyntaxHighlighter
import javax.swing.Icon

class KsonColorSettingsPage : ColorSettingsPage {
    override fun getIcon(): Icon {
        return KsonIcons.FILE
    }

    override fun getHighlighter(): SyntaxHighlighter {
        return KsonSyntaxHighlighter()
    }

    override fun getDemoText(): String {
        return """
            key: value
            string: "a string"
            dashList:
                - "list element"
                - <
                    - "element of delimited sub-list"
                    - "another sub-list element"
                  >
                - "another list element"
            list: [1, 2, 3, true, false]
            invalid: ``
            embed_block: %%kotlin
                println("Hello y'all")
            %%
            # this is a comment
            nested: {
                null_keyword: null
            }
        """.trimIndent()
    }

    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey>? {
        return null
    }

    override fun getAttributeDescriptors(): Array<AttributesDescriptor> {
        return DESCRIPTORS
    }

    override fun getColorDescriptors(): Array<ColorDescriptor> {
        return ColorDescriptor.EMPTY_ARRAY
    }

    override fun getDisplayName(): String {
        return KsonBundle.message("kson.name")
    }

    companion object {
        private val DESCRIPTORS = KsonSyntaxHighlighter.KsonColorTag.values().map {
            AttributesDescriptor(
                it.displayName,
                KsonSyntaxHighlighter.getTextAttributesKey(it)
            )
        }.toTypedArray()
    }
}
