import {TextDocument} from 'vscode-languageserver-textdocument';
import {KsonTooling, ToolingDocument} from 'kson-tooling';
import {KsonDocument} from './KsonDocument.js';

/**
 * A KsonDocument that represents a schema file.
 *
 * Schema documents have a metaschema (resolved via their $schema field)
 * rather than a regular schema association. This distinction prevents
 * metaschema resolution from being attempted on regular KSON documents.
 */
export class KsonSchemaDocument extends KsonDocument {
    private metaSchemaDocument?: TextDocument;
    private _metaSchemaToolingDocument: ToolingDocument | null = null;

    constructor(textDocument: TextDocument, toolingDocument: ToolingDocument, metaSchemaDocument?: TextDocument) {
        super(textDocument, toolingDocument);
        this.metaSchemaDocument = metaSchemaDocument;
    }

    /**
     * Get the metaschema document for this schema document, if one is configured.
     */
    getMetaSchemaDocument(): TextDocument | undefined {
        return this.metaSchemaDocument;
    }

    /**
     * Returns a lazily-created {@link ToolingDocument} for the metaschema
     * associated with this schema document. Cached for the lifetime of this
     * document instance.
     */
    getMetaSchemaToolingDocument(): ToolingDocument | undefined {
        if (!this.metaSchemaDocument) return undefined;
        if (!this._metaSchemaToolingDocument) {
            this._metaSchemaToolingDocument = KsonTooling.getInstance().parse(this.metaSchemaDocument.getText(), this.metaSchemaDocument.uri);
        }
        return this._metaSchemaToolingDocument;
    }
}

/**
 * Type guard to check if a KsonDocument is a KsonSchemaDocument.
 */
export function isKsonSchemaDocument(doc: KsonDocument): doc is KsonSchemaDocument {
    return doc instanceof KsonSchemaDocument;
}
