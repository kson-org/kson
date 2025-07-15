import {Analysis} from 'kson';
import {DocumentUri, TextDocuments, Range, Position} from "vscode-languageserver";
import {TextDocument} from "vscode-languageserver-textdocument";

/**
 * Kson Document Entry.
 * This class wraps a standard {@link TextDocument} and adds KSON-specific information,
 * like the {@link parseAnalysis}. It implements the {@link TextDocument} so the {@link KsonDocumentsManager} can
 * implement the standard {@link TextDocuments} manager.
 */
export class KsonDocument implements TextDocument {
    public readonly textDocument: TextDocument;
    private readonly parseAnalysis: Analysis;

    constructor(textDocument: TextDocument, parseAnalysis:Analysis) {
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
}