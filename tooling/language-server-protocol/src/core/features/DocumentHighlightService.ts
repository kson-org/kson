import {
    DocumentHighlight,
    DocumentHighlightKind,
    Position,
    SymbolKind
} from 'vscode-languageserver';
import { KsonDocument } from '../document/KsonDocument.js';
import {DocumentSymbolService, DocumentSymbolWithParent} from './DocumentSymbolService.js';
import {IndexedDocumentSymbols} from "./IndexedDocumentSymbols";

export class DocumentHighlightService {

    getDocumentHighlights(
        document: KsonDocument,
        position: Position
    ): DocumentHighlight[] {
        // Try to retrieve the cached symbols
        let symbolsWithIndex = document.getSymbolsWithIndex();
        if (!symbolsWithIndex) {
            // Create and cache symbols
            const documentSymbols = new DocumentSymbolService()
                .getDocumentSymbols(document.getAnalysisResult().ksonValue);
            symbolsWithIndex = new IndexedDocumentSymbols(documentSymbols)
            document.setSymbolsWithIndex(symbolsWithIndex)
        }

        // Use the position index to find symbols at the position
        const symbolAtPosition = symbolsWithIndex.getMostSpecificSymbolAtPosition(position);
        if (!symbolAtPosition) {
            return [];
        }

        return this.getHighlightsForSymbol(symbolAtPosition);
    }

    /**
     * Get highlights based on the symbol at the cursor position. If the cursor is on a {@link SymbolKind.Property} we
     * highlight the sibling properties (meaning the properties that are also part of the parent object).
     */
    private getHighlightsForSymbol(symbol: DocumentSymbolWithParent): DocumentHighlight[] {
        const highlights: DocumentHighlight[] = [];

        if(symbol.kind !== SymbolKind.Key){
            return []
        }

        const parent = symbol.parent
        if (parent) {
            // Highlight all property keys in the parent object
            for (const child of parent.children) {
                if (child.kind === SymbolKind.Key) {
                    highlights.push({
                        range: child.selectionRange, // Use selectionRange for the key only
                        kind: DocumentHighlightKind.Read
                    });
                }
            }
            return highlights;
        }

        return [];
    }
}