package org.kson.jetbrains.folding

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.CustomFoldingBuilder
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.*
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import org.kson.jetbrains.parser.elem
import org.kson.jetbrains.psi.KsonEmbedBlock
import org.kson.parser.ParsedElementType
import org.kson.stdlibx.exceptions.ShouldNotHappenException

/**
 * Provides code folding support for Kson files.
 *
 * This builder implements intelligent folding behavior for various Kson elements:
 *
 * Supported folding types:
 * - Lists:
 *   - Dash lists (-): Shows count as "n items"
 *   - Angle bracket lists (<>): Shows count as "< n items >"
 *   - Bracket lists ([]): Shows count as "[ n items ]"
 * - Objects ({}): Shows count as "{ n objects }"
 * - Embedded blocks: Shows as "{delim}...{delim}" where delim is the block delimiter
 *
 * For embedded blocks, only the multi-line condition applies.

 * This class implements [CustomFoldingBuilder] to provide the folding behavior and [DumbAware] to indicate
 * that folding can be performed without requiring access to indices and for testing the folding functionality.
 */
internal class KsonFoldingBuilder : CustomFoldingBuilder() {
    /**
     * Set of PSI element types that can be folded.
     */
    private val foldableElements = setOf(
        elem(ParsedElementType.DASH_LIST),
        elem(ParsedElementType.DASH_DELIMITED_LIST),
        elem(ParsedElementType.BRACKET_LIST),
        elem(ParsedElementType.EMBED_BLOCK),
        elem(ParsedElementType.OBJECT_PROPERTY)
    )

    override fun buildLanguageFoldRegions(
        descriptors: MutableList<FoldingDescriptor>,
        root: PsiElement,
        document: Document,
        quick: Boolean
    ) {
        if (root.language !== root.containingFile.viewProvider.baseLanguage) {
            return
        }

        recursivelyProcessElements(root, descriptors, document)
    }

    /**
     * Recursively processes PSI elements to build folding regions.
     *
     * @param element The current PSI element being processed
     * @param descriptors List to store the folding descriptors
     * @param document The document being processed
     */
    private fun recursivelyProcessElements(
        element: PsiElement,
        descriptors: MutableList<FoldingDescriptor>,
        document: Document
    ) {
        // Check if this element should be folded
        if (isFoldable(document, element)) {
            addDescriptor(element, descriptors)
        }

        element.children.forEach { child ->
            recursivelyProcessElements(child, descriptors, document)
        }
    }

    /**
     * Adds a folding descriptor for the given element.
     *
     * If the element is a list we fold, starting from the previous whitespace.
     * This is to ensure that in case a list starts at a new line:
     * ```
     * list:
     *   - 1
     *   - 2
     * ```
     * It is folded on the same line as the key: `list: < 2 items >`
     * If the element is not a list the element is folded.
     *
     * @param element The PSI element to potentially fold
     * @param descriptors List to store the folding descriptors
     */
    private fun addDescriptor(element: PsiElement, descriptors: MutableList<FoldingDescriptor>) {
        val range = when (element.elementType) {
            elem(ParsedElementType.DASH_LIST), elem(ParsedElementType.BRACKET_LIST), elem(ParsedElementType.DASH_DELIMITED_LIST) -> {
                if (element.prevSibling != null && element.prevSibling.text.contains("\n")) {
                    TextRange(element.prevSibling.startOffset, element.endOffset)
                } else {
                    element.textRange
                }
            }
            else -> element.textRange
        }

        descriptors.add(FoldingDescriptor(element.node, range))

    }

    /**
     * Determines if a PSI element can be collapsed.
     * An element can be collapsed if it:
     * 1. Is contained in [foldableElements]
     * 2. Spans multiple lines
     * 3. If it's an object it should have more than 0 children keys
     *
     * @param element The PSI element to check
     * @return true if the element's type is in [foldableElements]
     */
    private fun isFoldable(document: Document, element: PsiElement): Boolean {
        // Check if the element is in [foldableElements]
        if (element.node.elementType !in foldableElements) {
            return false
        }

        // Check if the element spans multiple lines
        val range = element.textRange
        if (document.getLineNumber(range.startOffset) == document.getLineNumber(range.endOffset - 1)) {
            return false
        }

        // Check if the object contains any keys
        if (element.node.elementType == elem(ParsedElementType.OBJECT_PROPERTY) && countChildKeys(element) == 0) {
            return false
        }

        return true
    }

    /**
     * Generates the placeholder text for a folded region based on its type.
     * The text includes the count of child elements and appropriate delimiters.
     *
     * Text formats:
     * - Dash list and angle-bracket lists: "< n items >"
     * - Bracket list: "[ n items ]"
     * - Object: "{key: n keys }"
     * - Embed block: "delim tag...delim"
     *
     * @param node The ASTNode being folded
     * @param range The TextRange of the element being folded
     * @return The placeholder text to display
     */
    override fun getLanguagePlaceholderText(node: ASTNode, range: TextRange): String {
        return when (node.elementType) {
            elem(ParsedElementType.DASH_LIST), elem(ParsedElementType.DASH_DELIMITED_LIST) -> {
                val numItems = node.psi.children.count()
                "< $numItems ${pluralize(numItems, "item")} >"
            }

            elem(ParsedElementType.BRACKET_LIST) -> {
                val numItems = node.psi.children.count()
                "[ $numItems ${pluralize(numItems, "item")} ]"
            }

            elem(ParsedElementType.OBJECT_PROPERTY) -> {
                val keyWord = (node.psi.firstChild?.text) ?: return ""
                val numKeys = countChildKeys(node.psi)
                "${keyWord}: { ${numKeys} ${pluralize(numKeys, "key")} }"
            }

            elem(ParsedElementType.EMBED_BLOCK) -> {
                val embedBlock = node.psi as KsonEmbedBlock
                val embedDelim = embedBlock.embedDelim
                val embedBlockTag = embedBlock.embedBlockTag
                "${embedDelim.openDelimiter}${embedBlockTag}...${embedDelim.closeDelimiter}"
            }

            else -> {
                throw ShouldNotHappenException("did not expect element type ${node.elementType} to be foldable")
            }
        }
    }

    /**
     * Helper function to pluralize words based on count.
     *
     * @param count The number of items
     * @param word The singular form of the word
     * @return The word with "s" appended if count != 1
     */
    private fun pluralize(count: Int, word: String): String {
        return if (count == 1) word else "${word}s"
    }

    /**
     * Counts the number of child keys within an object represented by the given PSI element.
     *
     * @param element The PSI element to process, expected to have child elements.
     * @return The number of child keys found, or 0 if none are present or the element is not an object.
     */
    private fun countChildKeys(element: PsiElement): Int {
        val childObject = element.children.find { it.elementType == elem(ParsedElementType.OBJECT) } ?: return 0
        return childObject.children.count { it.elementType == elem(ParsedElementType.OBJECT_PROPERTY) }
    }

    override fun isRegionCollapsedByDefault(node: ASTNode): Boolean {
        return false
    }

}