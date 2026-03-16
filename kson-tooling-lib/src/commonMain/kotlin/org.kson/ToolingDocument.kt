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
class ToolingDocument internal constructor(content: String) {
    private val parseResult = KsonCore.parseToAst(content, CoreCompileConfig(ignoreErrors = true))

    /**
     * The parsed [KsonValue], or null if the document has parse errors or is empty.
     *
     * With ignoreErrors = true, the error-walking step is skipped, so
     * [AstParseResult.hasErrors] may return false even when the AST contains
     * error nodes. A [KsonRootError] root means the document is completely
     * unparseable. But the parser can also produce a valid-looking root with
     * [org.kson.ast.AstNodeError] nodes deeper inside — in which case
     * [org.kson.ast.AstNode.toKsonValue] throws during the tree walk.
     *
     * Value-based features (document symbols, selection ranges, sibling keys)
     * return empty results when this is null. Token-based features (semantic
     * tokens, folding ranges) still work via [tokens] and [ast].
     */
    val ksonValue: KsonValue? by lazy {
        if (parseResult.ast is KsonRootError) {
            null
        } else {
            try {
                parseResult.ksonValue
            } catch (_: RuntimeException) {
                // toKsonValue() throws when it encounters an AstNodeError
                // anywhere in the tree. This happens with ignoreErrors = true
                // because error messages aren't walked, so hasErrors() returns
                // false even though the AST contains error nodes.
                null
            }
        }
    }

    internal val tokens: List<Token> get() = parseResult.lexedTokens
    internal val ast: KsonRoot get() = parseResult.ast
}
