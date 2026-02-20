import {Analysis, KsonValue, KsonValueType} from 'kson';
import {DocumentUri, TextDocuments, Range, Position} from "vscode-languageserver";
import {TextDocument} from "vscode-languageserver-textdocument";
import {IndexedDocumentSymbols} from "../features/IndexedDocumentSymbols";

/**
 * Kson Document Entry.
 * This class wraps a standard {@link TextDocument} and adds KSON-specific information,
 * like the {@link parseAnalysis}. It implements the {@link TextDocument} so the {@link KsonDocumentsManager} can
 * implement the standard {@link TextDocuments} manager.
 */
export class KsonDocument implements TextDocument {
    public readonly textDocument: TextDocument;
    private schemaDocument?: TextDocument;
    private readonly parseAnalysis: Analysis;
    private indexedDocumentSymbols?: IndexedDocumentSymbols;

    constructor(textDocument: TextDocument, parseAnalysis:Analysis, schemaDocument?: TextDocument) {
        this.schemaDocument = schemaDocument;
        this.textDocument = textDocument;
        this.parseAnalysis = parseAnalysis;
    }

    get uri(): DocumentUri {
        return this.textDocument.uri;
    }

    get languageId(): string {
        return this.textDocument.languageId;
    }

    get version(): number {
        return this.textDocument.version;
    }

    get lineCount(): number {
        return this.textDocument.lineCount;
    }

    getText(range?: Range): string {
        return this.textDocument.getText(range);
    }

    positionAt(offset: number): Position {
        return this.textDocument.positionAt(offset);
    }

    offsetAt(position: Position): number {
        return this.textDocument.offsetAt(position);
    }

    /**
     * Returns the parse result of this {@link KsonDocument}
     */
    getAnalysisResult(): Analysis {
        if (!this.parseAnalysis) {
            throw new Error(`No parse result for : ${this.uri}`);
        }
        return this.parseAnalysis;
    }

    /**
     * Returns the full {@link Range} of this {@link KsonDocument}
     */
    getFullDocumentRange(): Range {
        const text = this.textDocument.getText();
        const lastChar = this.textDocument.offsetAt(this.textDocument.positionAt(text.length));
        const lastPosition = this.textDocument.positionAt(lastChar);

        return {
            start: {line: 0, character: 0},
            end: lastPosition
        };
    }
    
    /**
     * Get cached symbols with index, or undefined if not cached
     */
    getSymbolsWithIndex(): IndexedDocumentSymbols | undefined {
        return this.indexedDocumentSymbols;
    }
    
    /**
     * Set cached symbols with index
     */
    setSymbolsWithIndex(symbolsWithIndex: IndexedDocumentSymbols): void {
        this.indexedDocumentSymbols = symbolsWithIndex;
    }

    /**
     * Get the schema document for this document, if one is configured.
     */
    getSchemaDocument(): TextDocument | undefined {
        return this.schemaDocument;
    }

    /**
     * Extract the $schema field value from this document's parse analysis.
     *
     * @returns The $schema string value, or undefined if not present or not a string
     */
    getSchemaId(): string | undefined {
        return KsonDocument.extractSchemaId(this.parseAnalysis);
    }

    /**
     * Extract the $schema field value from a parsed KSON analysis result.
     *
     * @param analysis The KSON analysis result
     * @returns The $schema string value, or undefined if not present or not a string
     */
    static extractSchemaId(analysis: Analysis): string | undefined {
        const ksonValue = analysis.ksonValue;
        if (!ksonValue || ksonValue.type !== KsonValueType.OBJECT) {
            return undefined;
        }
        const obj = ksonValue as KsonValue.KsonObject;
        const schemaValue = obj.properties.asJsReadonlyMapView().get('$schema');
        if (!schemaValue || schemaValue.type !== KsonValueType.STRING) {
            return undefined;
        }
        return (schemaValue as KsonValue.KsonString).value;
    }
}