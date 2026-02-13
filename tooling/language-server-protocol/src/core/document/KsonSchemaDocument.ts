import {Analysis} from 'kson';
import {TextDocument} from 'vscode-languageserver-textdocument';
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

    constructor(textDocument: TextDocument, parseAnalysis: Analysis, metaSchemaDocument?: TextDocument) {
        super(textDocument, parseAnalysis);
        this.metaSchemaDocument = metaSchemaDocument;
    }

    /**
     * Get the metaschema document for this schema document, if one is configured.
     */
    getMetaSchemaDocument(): TextDocument | undefined {
        return this.metaSchemaDocument;
    }
}

/**
 * Type guard to check if a KsonDocument is a KsonSchemaDocument.
 */
export function isKsonSchemaDocument(doc: KsonDocument): doc is KsonSchemaDocument {
    return doc instanceof KsonSchemaDocument;
}
