import {Position, Range} from 'vscode-languageserver';
import {DocumentSymbolWithParent} from './DocumentSymbolService.js';

/**
 * A position index by line number that allows fast lookups of DocumentSymbols by position.
 */
export class IndexedDocumentSymbols {
    private lineIndexedSymbols = new Map<number, DocumentSymbolWithParent[]>
    private readonly documentSymbols: DocumentSymbolWithParent[] = [];

    /**
     * Build the index from an array of DocumentSymbols
     */
    constructor(symbols: DocumentSymbolWithParent[]) {
        this.documentSymbols = symbols
        this.collectAndIndexSymbols(symbols);
    }

    getDocumentSymbols(){
        return this.documentSymbols
    }

    /**
     * Find all symbols that contain the given position
     */
    findSymbolsAtPosition(position: Position): DocumentSymbolWithParent[] {
        const symbolsOnLine = this.lineIndexedSymbols.get(position.line) || [];

        // Filter to only symbols that actually contain the position
        return symbolsOnLine.filter(symbol =>
            this.isPositionInRange(position, symbol.range)
        )
    }

    /**
     * Get the most specific (deepest) symbol at a position. The most specific symbol is the 'smallest'
     * symbol at any given position.
     */
    getMostSpecificSymbolAtPosition(position: Position): DocumentSymbolWithParent | null {
        const symbols = this.findSymbolsAtPosition(position);

        // Sort by range size (smallest first) to get most specific symbol
        symbols.sort((a, b) =>
            this.getRangeSize(a.range) - this.getRangeSize(b.range)
        );
        return symbols.length > 0 ? symbols[0] : null;
    }

    /**
     * Recursively collect all symbols and index them by the lines they span
     */
    private collectAndIndexSymbols(symbols: DocumentSymbolWithParent[]): void {
        for (const symbol of symbols) {
            // Add symbol to index for every line it spans
            const startLine = symbol.range.start.line;
            const endLine = symbol.range.end.line;
            
            for (let line = startLine; line <= endLine; line++) {
                if (!this.lineIndexedSymbols.has(line)) {
                    this.lineIndexedSymbols.set(line, []);
                }
                this.lineIndexedSymbols.get(line)!.push(symbol);
            }
            
            // Recursively process children
            if (symbol.children && symbol.children.length > 0) {
                this.collectAndIndexSymbols(symbol.children);
            }
        }
    }

    /**
     * Check if a position is within a range
     */
    private isPositionInRange(position: Position, range: Range): boolean {
        const start = range.start;
        const end = range.end;
        
        if (position.line < start.line || position.line > end.line) {
            return false;
        }
        
        if (position.line === start.line && position.character < start.character) {
            return false;
        }
        
        return !(position.line === end.line && position.character > end.character);

    }
    
    /**
     * Get the size of a range (for sorting) in {@link getMostSpecificSymbolAtPosition}
     */
    private getRangeSize(range: Range): number {
        const lines = range.end.line - range.start.line;
        const chars = range.end.character - range.start.character;
        return lines * 100000 + chars;
    }
}