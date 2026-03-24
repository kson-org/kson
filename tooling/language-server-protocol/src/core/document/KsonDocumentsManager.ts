import {TextDocument} from 'vscode-languageserver-textdocument';
import {KsonTooling, ToolingDocument} from 'kson-tooling';
import {KsonDocument} from "./KsonDocument.js";
import {KsonSchemaDocument} from "./KsonSchemaDocument.js";
import {DocumentUri, TextDocuments, TextDocumentContentChangeEvent} from "vscode-languageserver";
import {SchemaProvider, NoOpSchemaProvider} from "../schema/SchemaProvider.js";

/**
 * Resolve the appropriate KsonDocument type for a given document.
 *
 * Parses the content once into a {@link ToolingDocument}, then uses the
 * parsed value tree to check for $schema if URI-based resolution fails.
 * The single parse is shared with the returned document for all tooling
 * operations.
 */
function resolveDocument(
    provider: SchemaProvider,
    textDocument: TextDocument,
    toolingDocument: ToolingDocument,
): KsonDocument {
    const schema = provider.getSchemaForDocument(textDocument.uri);
    if (schema) {
        return new KsonDocument(textDocument, toolingDocument, schema);
    }

    const schemaId = toolingDocument.schemaId ?? undefined;
    if (schemaId) {
        const metaSchema = provider.getMetaSchemaForId(schemaId);
        if (metaSchema) {
            return new KsonSchemaDocument(textDocument, toolingDocument, metaSchema);
        }
    }

    return new KsonDocument(textDocument, toolingDocument);
}

/**
 * Document management for the Kson Language Server.
 * The {@link KsonDocumentsManager} keeps track of all {@link KsonDocument}'s that
 * we are watching. It extends {@link TextDocuments} to handle {@link KsonDocument}
 * instances.
 */
export class KsonDocumentsManager extends TextDocuments<KsonDocument> {
    private schemaProvider: SchemaProvider;

    /**
     * Typed reference to the parent's internal document map.
     * Stored once during construction so we fail fast if the internal
     * property name ever changes in a library upgrade.
     */
    private readonly syncedDocuments: Map<string, KsonDocument>;

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
                const toolingDocument = KsonTooling.getInstance().parse(content);
                return resolveDocument(provider, textDocument, toolingDocument);
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
                const toolingDocument = KsonTooling.getInstance().parse(textDocument.getText());
                // Reuse the existing schema/metaschema — it only changes when schema
                // config changes, which triggers refreshDocumentSchemas() separately.
                if (ksonDocument instanceof KsonSchemaDocument) {
                    return new KsonSchemaDocument(textDocument, toolingDocument, ksonDocument.getMetaSchemaDocument());
                }
                return new KsonDocument(textDocument, toolingDocument, ksonDocument.getSchemaDocument());
            }
        });

        // Assign the schema provider after super() is called
        this.schemaProvider = provider;

        // Grab typed reference to parent's internal document map.
        // Fails fast during construction if the property is renamed.
        const internal = (this as any)._syncedDocuments as Map<string, KsonDocument> | undefined;
        if (!internal || typeof internal.set !== 'function') {
            throw new Error(
                'TextDocuments internal API changed: _syncedDocuments not found. ' +
                'Update KsonDocumentsManager to match the new vscode-languageserver version.'
            );
        }
        this.syncedDocuments = internal;
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

        // For each document, create a new KsonDocument instance with the updated schema.
        // Reuses the existing ToolingDocument since the content hasn't changed.
        for (const doc of allDocs) {
            const updatedDoc = resolveDocument(
                this.schemaProvider, doc.textDocument, doc.getToolingDocument()
            );

            // Replace in the internal document cache
            this.syncedDocuments.set(doc.uri, updatedDoc);
        }
    }
}
