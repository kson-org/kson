import {TextDocument} from 'vscode-languageserver-textdocument';
import {Kson} from 'kson';
import {KsonDocument} from "./KsonDocument.js";
import {DocumentUri, TextDocuments, TextDocumentContentChangeEvent} from "vscode-languageserver";
import {SchemaProvider, NoOpSchemaProvider} from "../schema/SchemaProvider.js";

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

                // Try to get schema from provider
                let schemaDocument = provider.getSchemaForDocument(uri);

                const parseResult = Kson.getInstance().analyze(content, uri);
                return new KsonDocument(textDocument, parseResult, schemaDocument);
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
                return new KsonDocument(
                    textDocument,
                    parseResult,
                    provider.getSchemaForDocument(ksonDocument.uri)
                );
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
            const updatedSchema = this.schemaProvider.getSchemaForDocument(doc.uri);

            // Create new document instance with updated schema
            const updatedDoc = new KsonDocument(textDocument, parseResult, updatedSchema);

            // Replace in the internal document cache
            // Access the protected _syncedDocuments property from parent class
            (this as any)._syncedDocuments.set(doc.uri, updatedDoc);
        }
    }
}
