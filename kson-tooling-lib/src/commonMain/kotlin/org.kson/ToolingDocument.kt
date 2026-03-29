@file:OptIn(ExperimentalJsExport::class)

package org.kson

import org.kson.ast.AstNode
import org.kson.ast.KsonRoot
import org.kson.ast.KsonRootImpl
import org.kson.parser.Lexer
import org.kson.parser.Token
import org.kson.value.KsonObject
import org.kson.value.KsonString
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
 * The document stores the original source text, the parsed [KsonValue] tree, and
 * both the full gap-free token list (for semantic tokens and folding) and a filtered
 * list of meaningful tokens (for cursor-context analysis in path building).
 *
 * Created via [KsonTooling.parse].
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

    /**
     * The root value node of the AST, or null if the document is completely unparseable.
     *
     * Unlike [ksonValue], this is available even when the AST contains error nodes
     * (e.g. a missing value after a colon), enabling navigation of broken documents
     * where error nodes are treated as leaves.
     */
    internal val rootAstNode: AstNode? get() = (parseResult.ast as? KsonRootImpl)?.rootNode

    /** Full gap-free token list (includes WHITESPACE and COMMENT). */
    internal val tokens: List<Token> get() = parseResult.lexedTokens

    internal val ast: KsonRoot get() = parseResult.ast

    /**
     * The `$schema` value from the root object, or null if the document is not
     * an object or has no `$schema` string property.
     */
    val schemaId: String? by lazy {
        val obj = ksonValue as? KsonObject ?: return@lazy null
        val schemaValue = obj.propertyMap["\$schema"]?.propValue as? KsonString ?: return@lazy null
        schemaValue.value
    }

    /**
     * Cached document symbol tree, built lazily on first access.
     */
    internal val documentSymbols: List<DocumentSymbol> by lazy {
        val kv = ksonValue ?: return@lazy emptyList()
        DocumentSymbolBuilder.build(kv)
    }

    /**
     * Tokens with WHITESPACE and COMMENT filtered out—the same set a
     * non-gap-free parse would produce, useful for cursor-context analysis.
     */
    internal val meaningfulTokens: List<Token> by lazy {
        parseResult.lexedTokens.filter { it.tokenType !in Lexer.ignoredTokens }
    }
}
