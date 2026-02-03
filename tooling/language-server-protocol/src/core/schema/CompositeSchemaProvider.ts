import {TextDocument} from 'vscode-languageserver-textdocument';
import {DocumentUri} from 'vscode-languageserver';
import {SchemaProvider} from './SchemaProvider.js';

/**
 * A composite schema provider that chains multiple providers together.
 * Providers are queried in order, and the first non-undefined result is returned.
 *
 * This enables the priority system where user-configured schemas (via .kson-schema.kson)
 * take precedence over bundled schemas.
 *
 * Typical usage:
 *   CompositeSchemaProvider([FileSystemSchemaProvider, BundledSchemaProvider])
 */
export class CompositeSchemaProvider implements SchemaProvider {
    /**
     * Creates a new CompositeSchemaProvider.
     *
     * @param providers Array of schema providers to chain, in priority order (first takes precedence)
     * @param logger Optional logger for debugging
     */
    constructor(
        private providers: SchemaProvider[],
        private logger?: {
            info: (message: string) => void;
            warn: (message: string) => void;
            error: (message: string) => void;
        }
    ) {
        this.logger?.info(`CompositeSchemaProvider initialized with ${providers.length} providers`);
    }

    /**
     * Get the schema for a document by querying all providers in order.
     * Returns the first non-undefined result.
     *
     * @param documentUri The URI of the KSON document
     * @param languageId Optional language ID for bundled schema lookup
     * @returns TextDocument containing the schema, or undefined if no provider has a schema
     */
    getSchemaForDocument(documentUri: DocumentUri, languageId?: string): TextDocument | undefined {
        for (const provider of this.providers) {
            const schema = provider.getSchemaForDocument(documentUri, languageId);
            if (schema) {
                return schema;
            }
        }
        return undefined;
    }

    /**
     * Reload all providers' configurations.
     */
    reload(): void {
        for (const provider of this.providers) {
            provider.reload();
        }
    }

    /**
     * Check if a given file URI is a schema file in any provider.
     *
     * @param fileUri The URI of the file to check
     * @returns True if any provider considers this a schema file
     */
    isSchemaFile(fileUri: DocumentUri): boolean {
        return this.providers.some(provider => provider.isSchemaFile(fileUri));
    }

    /**
     * Get the list of providers.
     */
    getProviders(): ReadonlyArray<SchemaProvider> {
        return this.providers;
    }
}
