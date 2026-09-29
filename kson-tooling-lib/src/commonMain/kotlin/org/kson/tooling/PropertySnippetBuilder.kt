package org.kson.tooling

import org.kson.CoreCompileConfig
import org.kson.KsonCore
import org.kson.ast.AstNode
import org.kson.parser.Coordinates
import org.kson.parser.Lexer
import org.kson.parser.Token
import org.kson.parser.TokenType
import org.kson.parser.behavior.StringUnquoted
import org.kson.tooling.navigation.NavigatedSchema
import org.kson.tooling.navigation.isAtOrAfter
import org.kson.tooling.navigation.isFalseSchema
import org.kson.value.KsonList
import org.kson.value.KsonObject
import org.kson.value.KsonString
import org.kson.value.KsonValue
import org.kson.walker.KsonValueWalker
import org.kson.walker.TreePointer
import org.kson.walker.navigateToLocationWithPointer

/** Gives property completions a [SnippetEdit] that opens the value, where the schema pins its type. */
internal object PropertySnippetBuilder {

    internal const val PLACEHOLDER_KEY = "snippetProbe"

    private val keyCanFollow = setOf(TokenType.WHITESPACE, TokenType.CURLY_BRACE_L, TokenType.COMMA)
    private val propertyCanPrecede =
        setOf(TokenType.WHITESPACE, TokenType.EOF, TokenType.CURLY_BRACE_R, TokenType.COMMA)

    fun addSnippetEdits(
        completions: List<CompletionItem>,
        document: ToolingDocument,
        caret: Coordinates,
        objectPointer: TreePointer<AstNode>,
        navigateToNewProperty: (TreePointer<AstNode>) -> List<NavigatedSchema>
    ): List<CompletionItem> {
        if (completions.none { it.kind == CompletionKind.PROPERTY }) return completions
        val keyRange = standaloneKeyRange(document.tokens, caret) ?: return completions
        if (!keyLandsIn(objectPointer, document, keyRange)) return completions
        return completions.map { item ->
            val openedValue = item
                .takeIf { it.kind == CompletionKind.PROPERTY && StringUnquoted.isUnquotable(it.label) }
                ?.let { openedValue(navigateToNewProperty(objectPointer.child(it.label))) }
                ?: return@map item
            val snippet = SnippetEdit(keyRange, "${item.label}: $openedValue")
            CompletionItem(item.label, item.detail, item.documentation, item.kind, snippet)
        }
    }

    private fun standaloneKeyRange(tokens: List<Token>, caret: Coordinates): Range? {
        val typedKey = tokenBefore(tokens, caret)
            ?.takeIf { it.tokenType == TokenType.UNQUOTED_STRING }
            ?.lexeme?.location
        val start = typedKey?.start ?: caret
        val end = typedKey?.end ?: caret

        val startsAtBoundary = tokenBefore(tokens, start)?.let { it.tokenType in keyCanFollow } ?: true
        val endsAtBoundary = tokenAt(tokens, end).tokenType in propertyCanPrecede
        val nextToken = tokens.firstOrNull {
            it.tokenType !in Lexer.ignoredTokens && it.lexeme.location.start.isAtOrAfter(end)
        } ?: return null

        return if (startsAtBoundary && endsAtBoundary && nextToken.tokenType != TokenType.COLON) {
            Range(start.line, start.column, end.line, end.column)
        } else {
            null
        }
    }

    private fun tokenBefore(tokens: List<Token>, position: Coordinates): Token? =
        tokens.lastOrNull { !it.lexeme.location.start.isAtOrAfter(position) }

    private fun tokenAt(tokens: List<Token>, position: Coordinates): Token =
        tokens.firstOrNull { !position.isAtOrAfter(it.lexeme.location.end) } ?: tokens.last()

    private fun keyLandsIn(objectPointer: TreePointer<AstNode>, document: ToolingDocument, keyRange: Range): Boolean {
        val content = document.content
        val key = generateSequence(PLACEHOLDER_KEY) { "${it}_" }.first { it !in content }
        val start = offsetOf(content, Coordinates(keyRange.startLine, keyRange.startColumn))
        val end = offsetOf(content, Coordinates(keyRange.endLine, keyRange.endColumn))
        val probe = KsonCore.parseToAst(
            content.substring(0, start) + "$key: 0" + content.substring(end),
            CoreCompileConfig(sourceContext = document.sourceContext)
        )
        val probeValue = probe.ksonValue ?: return false
        val placeholderValue = Coordinates(keyRange.startLine, keyRange.startColumn + "$key: ".length)
        val landedAt = KsonValueWalker.navigateToLocationWithPointer(probeValue, placeholderValue)?.pointerFromRoot
        return landedAt?.pointer?.tokens == objectPointer.child(key).pointer.tokens
    }

    private fun offsetOf(content: String, position: Coordinates): Int {
        var lineStart = 0
        repeat(position.line) { lineStart = content.indexOf('\n', lineStart) + 1 }
        return lineStart + position.column
    }

    private fun openedValue(navigated: List<NavigatedSchema>): String? =
        when (possibleTypes(navigated)?.singleOrNull()) {
            "object" -> "{\$0}"
            "array" -> "[\$0]"
            "string" -> "'\$0'"
            else -> null
        }

    /** The types a value may take given the schemas [navigated] to it, or null for any type. */
    private fun possibleTypes(navigated: List<NavigatedSchema>, trailIndex: Int = 0): Set<String>? {
        val (unconditional, underChoices) = navigated.partition { it.branchTrail.size == trailIndex }
        val typesPerChoice = groupByChoice(underChoices, trailIndex).map { choice ->
            choice.first().branchTrail[trailIndex].branches
                .map { branch -> choice.filter { it.branchTrail[trailIndex].branch == branch } }
                .map { onBranch -> if (onBranch.isEmpty()) null else possibleTypes(onBranch, trailIndex + 1) }
                .reduce(::union)
        }
        return (unconditional.map { declaredTypes(it.resolvedValue) } + typesPerChoice).reduceOrNull(::intersect)
    }

    // Groups with isSameChoice rather than by hash, as hashing a schema node walks all of it
    private fun groupByChoice(schemas: List<NavigatedSchema>, trailIndex: Int): List<List<NavigatedSchema>> {
        val groups = mutableListOf<MutableList<NavigatedSchema>>()
        for (schema in schemas) {
            val step = schema.branchTrail[trailIndex]
            groups.find { it.first().branchTrail[trailIndex].isSameChoice(step) }?.add(schema)
                ?: groups.add(mutableListOf(schema))
        }
        return groups
    }

    private fun union(a: Set<String>?, b: Set<String>?): Set<String>? = if (a == null || b == null) null else a + b

    private fun intersect(a: Set<String>?, b: Set<String>?): Set<String>? = when {
        a == null -> b
        b == null -> a
        else -> a intersect b
    }

    private fun declaredTypes(schema: KsonValue): Set<String>? {
        if (schema.isFalseSchema) return emptySet()
        val names = when (val type = (schema as? KsonObject)?.propertyLookup?.get("type")) {
            is KsonString -> listOf(type.value)
            is KsonList -> type.elements.mapNotNull { (it as? KsonString)?.value }
            else -> return null
        }
        return names.map { if (it == "integer") "number" else it }.toSet()
    }
}
