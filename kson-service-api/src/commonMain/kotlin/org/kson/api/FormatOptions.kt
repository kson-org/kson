package org.kson.api

/**
 * Options for formatting Kson output.
 *
 * @param indentType The type of indentation to use (spaces or tabs)
 * @param formattingStyle The formatting style (PLAIN, DELIMITED, COMPACT, CLASSIC)
 * @param embedBlockRules Rules for formatting specific paths as embed blocks
 */
class FormatOptions(
    val indentType: IndentType = IndentType.Spaces(2),
    val formattingStyle: FormattingStyle = FormattingStyle.PLAIN,
    val embedBlockRules: List<EmbedRule> = emptyList()
)

/**
 * [FormattingStyle] options for Kson Output
 */
enum class FormattingStyle {
    PLAIN,
    DELIMITED,
    COMPACT,
    CLASSIC
}

/**
 * Options for indenting Kson Output
 */
sealed class IndentType {
    /** Use spaces for indentation with the specified count */
    class Spaces(val size: Int = 2) : IndentType()

    /** Use tabs for indentation */
    data object Tabs : IndentType()
}

/**
 * A rule for formatting string values at specific paths as embed blocks.
 *
 * When formatting KSON, strings at paths matching [pathPattern] will be rendered
 * as embed blocks instead of regular strings.
 *
 * **Warning:** JsonPointerGlob syntax is experimental and may change in future versions.
 */
class EmbedRule(
    val pathPattern: String,
    val tag: String? = null
)
