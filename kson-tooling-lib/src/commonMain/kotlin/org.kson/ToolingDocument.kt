@file:OptIn(ExperimentalJsExport::class)

package org.kson

import org.kson.ast.KsonRoot
import org.kson.parser.Token
import org.kson.value.KsonValue
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * A pre-parsed KSON document that can be shared across multiple tooling operations.
 *
 * Parses with [CoreCompileConfig.ignoreErrors] = true so that partial results are
 * available even for documents with syntax errors. This gives better editor behavior:
 * features like folding, semantic tokens, and document symbols continue to work in
 * broken documents rather than returning empty results.
 *
 */
@JsExport
class ToolingDocument internal constructor(val content: String) {
    private val parseResult = KsonCore.parseToAst(content, CoreCompileConfig(ignoreErrors = true))

    /**
     * The parsed [KsonValue] from error-tolerant parsing, or null if the
     * document is empty. Partial results are available even with syntax errors.
     *
     * Value-based features (document symbols, selection ranges, sibling keys)
     * return empty results when this is null. Token-based features (semantic
     * tokens, folding ranges) still work via [tokens] and [ast].
     */
    val ksonValue: KsonValue? get() = parseResult.ksonValue

    internal val tokens: List<Token> get() = parseResult.lexedTokens
    internal val ast: KsonRoot get() = parseResult.ast

    /**
     * Cached document symbol tree, built lazily on first access.
     */
    internal val documentSymbols: List<DocumentSymbol> by lazy {
        val kv = ksonValue ?: return@lazy emptyList()
        DocumentSymbolBuilder.build(kv)
    }
}
