@file:OptIn(ExperimentalJsExport::class)

package org.kson.tooling

import org.kson.CoreCompileConfig
import org.kson.KsonCore
import org.kson.ast.*
import org.kson.parser.Lexer
import org.kson.parser.Token
import org.kson.validation.SourceContext
import org.kson.value.*
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
class ToolingDocument internal constructor(val content: String, internal val sourceContext: SourceContext = SourceContext()) {
    private val parseResult = KsonCore.parseToAst(content,
        CoreCompileConfig(ignoreErrors = true, sourceContext = sourceContext)
    )

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

    /**
     * Partial [KsonValue] that skips error nodes rather than returning null.
     *
     * Falls back to [ksonValue] when available (no errors), otherwise builds a
     * partial tree from the AST by silently dropping properties/elements that
     * contain parse errors. This allows IDE features like completion narrowing
     * to see successfully-parsed sibling values even when the cursor position
     * has an incomplete value (e.g. `key:` with no value yet).
     */
    internal val partialKsonValue: KsonValue? by lazy {
        ksonValue ?: rootAstNode?.toPartialKsonValue()
    }

    /** Full gap-free token list (includes WHITESPACE and COMMENT). */
    internal val tokens: List<Token> get() = parseResult.lexedTokens

    internal val ast: KsonRoot get() = parseResult.ast

    /**
     * The `$schema` value from the root object, or null if the document is not
     * an object or has no `$schema` string property.
     */
    val schemaId: String? by lazy {
        val obj = ksonValue as? KsonObject ?: return@lazy null
        val schemaValue = obj.propertyMap[$$"$schema"]?.propValue as? KsonString ?: return@lazy null
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

/**
 * Partial conversion from AST to [KsonValue], skipping error nodes.
 *
 * Unlike [toKsonValue], which throws on any [AstNodeError], this function
 * silently skips properties/elements that contain errors.  This produces a
 * partial [KsonValue] that preserves all successfully-parsed siblings—useful
 * for IDE features like completion narrowing where sibling values need to be
 * visible even when the cursor position has an incomplete value.
 *
 * Returns null only when the node itself is an error (not when children are).
 */
private fun AstNode.toPartialKsonValue(): KsonValue? {
    if (this !is AstNodeImpl) return null

    return when (this) {
        is AstNodeError -> null
        is KsonRootImpl -> rootNode.toPartialKsonValue()
        is ObjectNode -> {
            val validProperties = properties.mapNotNull { prop ->
                val propImpl = prop as? ObjectPropertyNodeImpl ?: return@mapNotNull null
                val propKey = propImpl.key as? ObjectKeyNodeImpl ?: return@mapNotNull null
                val keyValue = propKey.key.toPartialKsonValue() as? KsonString ?: return@mapNotNull null
                val propValue = propImpl.value.toPartialKsonValue() ?: return@mapNotNull null
                keyValue.value to KsonObjectProperty(keyValue, propValue)
            }
            KsonObject(validProperties.toMap(), this)
        }
        is ListNode -> {
            val validElements = elements.mapNotNull { elem ->
                val listElementNode = elem as? ListElementNodeImpl ?: return@mapNotNull null
                listElementNode.value.toPartialKsonValue()
            }
            KsonList(validElements, this)
        }
        is EmbedBlockNode -> EmbedBlock(this)
        is StringNodeImpl -> KsonString(this)
        is NumberNode -> KsonNumber(this)
        is TrueNode -> KsonBoolean(this)
        is FalseNode -> KsonBoolean(this)
        is NullNode -> KsonNull(this)
        is KsonValueNodeImpl -> this.toPartialKsonValue()
        is ObjectKeyNodeImpl, is ObjectPropertyNodeImpl, is ListElementNodeImpl -> null
    }
}
