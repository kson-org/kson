import {TextDocument} from 'vscode-languageserver-textdocument';
import {Kson} from 'kson';
import {KsonDocument} from "./KsonDocument.js";
import {KsonSchemaDocument} from "./KsonSchemaDocument.js";
import {DocumentUri, TextDocuments, TextDocumentContentChangeEvent} from "vscode-languageserver";
import {SchemaProvider, NoOpSchemaProvider} from "../schema/SchemaProvider.js";

/**
 * Resolve the appropriate KsonDocument type for a given document.
 *
 * First tries URI-based schema resolution. If that fails, tries content-based
 * metaschema resolution via $schema — but only returns a KsonSchemaDocument
 * in that case, encoding the domain rule that only schema files have metaschemas.
 */
function resolveDocument(
    provider: SchemaProvider,
    textDocument: TextDocument,
    parseResult: ReturnType<ReturnType<typeof Kson.getInstance>['analyze']>
): KsonDocument {
    const schema = provider.getSchemaForDocument(textDocument.uri);
    if (schema) {
        return new KsonDocument(textDocument, parseResult, schema);
    }

    const schemaId = KsonDocument.extractSchemaId(parseResult);
    if (schemaId) {
        const metaSchema = provider.getMetaSchemaForId(schemaId);
        if (metaSchema) {
            return new KsonSchemaDocument(textDocument, parseResult, metaSchema);
        }
    }

    return new KsonDocument(textDocument, parseResult);
}

/**
 * Document management for the Kson Language Server.
 * The {@link KsonDocumentsManager} keeps track of all {@link KsonDocument}'s that
 * we are watching. It extends {@link TextDocuments} to handle {@link KsonDocument}
 * instances.
 */
export class KsonDocumentsManager extends TextDocuments<KsonDocument> {
    private schemaProvider: SchemaProvider;

    constructor(schemaProvider?: SchemaProvider) {
        // Use provided schema provider or default to no-op
        const provider = schemaProvider ?? new NoOpSchemaProvider();

        super({
            create: (
                uri: DocumentUri,
                languageId: string,
                version: number,
                content: string
            ): KsonDocument => {
                const textDocument = TextDocument.create(uri, languageId, version, content);
                const parseResult = Kson.getInstance().analyze(content, uri);
                return resolveDocument(provider, textDocument, parseResult);
            },
            update: (
                ksonDocument: KsonDocument,
                changes: TextDocumentContentChangeEvent[],
                version: number,
            ): KsonDocument => {
                const textDocument = TextDocument.update(
                    ksonDocument.textDocument,
                    changes,
                    version
                );
                const parseResult = Kson.getInstance().analyze(textDocument.getText(), ksonDocument.uri);
                return resolveDocument(provider, textDocument, parseResult);
            }
        });

        // Assign the schema provider after super() is called
        this.schemaProvider = provider;
    }

    /**
     * Get the schema provider used by this document manager.
     * @returns The schema provider instance
     */
    getSchemaProvider(): SchemaProvider {
        return this.schemaProvider;
    }

    /**
     * Reload the schema configuration.
     * Should be called when schema configuration changes.
     */
    reloadSchemaConfiguration(): void {
        this.schemaProvider.reload();
    }

    /**
     * Refresh all documents with updated schemas.
     * This forces all cached documents to re-fetch their schemas from the provider.
     */
    refreshDocumentSchemas(): void {
        // Get all currently open documents
        const allDocs = this.all();

        // For each document, create a new KsonDocument instance with the updated schema
        for (const doc of allDocs) {
            const textDocument = doc.textDocument;
            const parseResult = doc.getAnalysisResult();

            const updatedDoc = resolveDocument(this.schemaProvider, textDocument, parseResult);

            // Replace in the internal document cache
            // Access the protected _syncedDocuments property from parent class
            (this as any)._syncedDocuments.set(doc.uri, updatedDoc);
        }
    }
}
