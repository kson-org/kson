package org.kson

/**
 * Finds sibling key ranges for a cursor position in a KSON document.
 *
 * Accepts a [DocumentSymbol] tree, finds the most specific KEY symbol
 * containing the cursor, and returns the selection ranges of all sibling
 * KEY symbols from the parent.
 */
internal object SiblingKeyBuilder {
    fun build(
        symbols: List<DocumentSymbol>,
        line: Int,
        column: Int,
    ): List<Range> {
        // Flatten the tree, collecting each symbol with its parent
        val allSymbols = mutableListOf<SymbolWithParent>()
        collectAll(symbols, null, allSymbols)

        // Find all symbols containing the position
        val containing = allSymbols.filter { containsPosition(it.symbol.range, line, column) }

        // Pick the most specific (smallest range) KEY symbol
        val keySymbol =
            containing
                .filter { it.symbol.kind == DocumentSymbolKind.KEY }
                .minByOrNull { rangeSize(it.symbol.range) }
                ?: return emptyList()

        // Return selection ranges of all sibling KEY children from the parent
        val parent = keySymbol.parent ?: return emptyList()
        return parent.children
            .filter { it.kind == DocumentSymbolKind.KEY }
            .map { it.selectionRange }
    }

    private fun collectAll(
        symbols: List<DocumentSymbol>,
        parent: DocumentSymbol?,
        result: MutableList<SymbolWithParent>,
    ) {
        for (symbol in symbols) {
            result.add(SymbolWithParent(symbol, parent))
            collectAll(symbol.children, symbol, result)
        }
    }

    private fun containsPosition(
        range: Range,
        line: Int,
        column: Int,
    ): Boolean {
        if (line < range.startLine || line > range.endLine) return false
        if (line == range.startLine && column < range.startColumn) return false
        if (line == range.endLine && column > range.endColumn) return false
        return true
    }

    private fun rangeSize(range: Range): Int =
        (range.endLine - range.startLine) * MAX_COLUMNS_PER_LINE + (range.endColumn - range.startColumn)

    private const val MAX_COLUMNS_PER_LINE = 100_000

    private data class SymbolWithParent(
        val symbol: DocumentSymbol,
        val parent: DocumentSymbol?,
    )
}
