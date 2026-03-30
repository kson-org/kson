import {DocumentUri, TextDocuments, Range, Position} from "vscode-languageserver";
import {TextDocument} from "vscode-languageserver-textdocument";
import {KsonTooling, ToolingDocument} from 'kson-tooling';

/**
 * Kson Document Entry.
 * This class wraps a standard {@link TextDocument} with its pre-parsed
 * {@link ToolingDocument}, so every tooling operation on the same document
 * version shares a single parse. It implements {@link TextDocument} so the
 * {@link KsonDocumentsManager} can implement the standard {@link TextDocuments} manager.
 */
export class KsonDocument implements TextDocument {
    public readonly textDocument: TextDocument;
    private schemaDocument?: TextDocument;
    private readonly _toolingDocument: ToolingDocument;
    private _schemaToolingDocument: ToolingDocument | null = null;

    constructor(textDocument: TextDocument, toolingDocument: ToolingDocument, schemaDocument?: TextDocument) {
        this.textDocument = textDocument;
        this._toolingDocument = toolingDocument;
        this.schemaDocument = schemaDocument;
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
     * Returns the {@link ToolingDocument} for use with tooling operations.
     * Created eagerly during construction, so all tooling calls on the same
     * version share a single parse.
     */
    getToolingDocument(): ToolingDocument {
        return this._toolingDocument;
    }

    /**
     * Returns a lazily-created {@link ToolingDocument} for the schema associated
     * with this document. Cached for the lifetime of this document instance.
     */
    getSchemaToolingDocument(): ToolingDocument | undefined {
        const schema = this.getSchemaDocument();
        if (!schema) return undefined;
        if (!this._schemaToolingDocument) {
            this._schemaToolingDocument = KsonTooling.getInstance().parse(schema.getText(), schema.uri);
        }
        return this._schemaToolingDocument;
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
     * Get the schema document for this document, if one is configured.
     */
    getSchemaDocument(): TextDocument | undefined {
        return this.schemaDocument;
    }

    /**
     * Extract the $schema field value from this document's parsed value tree.
     *
     * @returns The $schema string value, or undefined if not present or not a string
     */
    getSchemaId(): string | undefined {
        return this._toolingDocument.schemaId ?? undefined;
    }
}
