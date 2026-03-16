import {DocumentSymbol, Range, SymbolKind} from 'vscode-languageserver';
import {KsonTooling, DocumentSymbol as KtDocumentSymbol, DocumentSymbolKind, ToolingDocument} from 'kson-tooling';

// Extended DocumentSymbol that includes parent reference
export class DocumentSymbolWithParent implements DocumentSymbol {
    name: string;
    detail?: string;
    kind: SymbolKind;
    range: Range;
    selectionRange: Range;
    children?: DocumentSymbolWithParent[];
    parent?: DocumentSymbolWithParent;

    constructor(
        name: string,
        kind: SymbolKind,
        range: Range,
        detail?: string,
        selectionRange?: Range,
        parent?: DocumentSymbolWithParent
    ) {
        this.name = name;
        this.kind = kind;
        this.range = range;
        this.detail = detail;
        this.selectionRange = selectionRange || range;
        this.children = [];

        // Make parent non-enumerable so it won't be serialized to JSON
        // This prevents circular reference errors when sending to LSP client
        Object.defineProperty(this, 'parent', {
            value: parent,
            writable: true,
            enumerable: false,
            configurable: true
        });
    }
}

function mapSymbolKind(kind: DocumentSymbolKind): SymbolKind {
    if (kind === DocumentSymbolKind.OBJECT) return SymbolKind.Object;
    if (kind === DocumentSymbolKind.ARRAY) return SymbolKind.Array;
    if (kind === DocumentSymbolKind.STRING) return SymbolKind.String;
    if (kind === DocumentSymbolKind.NUMBER) return SymbolKind.Number;
    if (kind === DocumentSymbolKind.BOOLEAN) return SymbolKind.Boolean;
    if (kind === DocumentSymbolKind.NULL) return SymbolKind.Null;
    if (kind === DocumentSymbolKind.KEY) return SymbolKind.Key;
    if (kind === DocumentSymbolKind.EMBED) return SymbolKind.Module;
    console.warn(`Unknown DocumentSymbolKind: ${kind}`);
    return SymbolKind.Object;
}

export class DocumentSymbolService {
    /**
     * Create document symbols from a pre-parsed KSON document.
     */
    getDocumentSymbols(document: ToolingDocument): DocumentSymbolWithParent[] {
        const tooling = KsonTooling.getInstance();
        const ktSymbols = tooling.getDocumentSymbols(document).asJsReadonlyArrayView();
        return ktSymbols.map(s => this.convertSymbol(s));
    }

    private convertSymbol(ktSymbol: KtDocumentSymbol, parent?: DocumentSymbolWithParent): DocumentSymbolWithParent {
        const range: Range = {
            start: { line: ktSymbol.range.startLine, character: ktSymbol.range.startColumn },
            end: { line: ktSymbol.range.endLine, character: ktSymbol.range.endColumn }
        };
        const selectionRange: Range = {
            start: { line: ktSymbol.selectionRange.startLine, character: ktSymbol.selectionRange.startColumn },
            end: { line: ktSymbol.selectionRange.endLine, character: ktSymbol.selectionRange.endColumn }
        };

        const symbol = new DocumentSymbolWithParent(
            ktSymbol.name,
            mapSymbolKind(ktSymbol.kind),
            range,
            ktSymbol.detail ?? undefined,
            selectionRange,
            parent
        );

        const ktChildren = ktSymbol.children.asJsReadonlyArrayView();
        symbol.children = ktChildren.map(c => this.convertSymbol(c, symbol));

        return symbol;
    }
}
