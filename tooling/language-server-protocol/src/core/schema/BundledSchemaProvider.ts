import {TextDocument} from 'vscode-languageserver-textdocument';
import {DocumentUri} from 'vscode-languageserver';
import {SchemaProvider} from './SchemaProvider.js';

/**
 * Configuration for a bundled schema.
 */
export interface BundledSchemaConfig {
    /** The language ID this schema applies to (e.g., 'kxt', 'kson-config') */
    languageId: string;
    /** The pre-loaded schema content as a string */
    schemaContent: string;
}

/**
 * Schema provider for bundled schemas that are shipped with the extension.
 * Works in both browser and Node.js environments since it doesn't require file system access.
 *
 * Bundled schemas are identified by language ID and use a special URI scheme:
 * bundled://schema/{languageId}
 */
export class BundledSchemaProvider implements SchemaProvider {
    private schemas: Map<string, TextDocument>;
    private enabled: boolean;

    /**
     * Creates a new BundledSchemaProvider.
     *
     * @param schemas Array of bundled schema configurations
     * @param enabled Whether bundled schemas are enabled (default: true)
     * @param logger Optional logger for warnings and errors
     */
    constructor(
        schemas: BundledSchemaConfig[],
        enabled: boolean = true,
        private logger?: {
            info: (message: string) => void;
            warn: (message: string) => void;
            error: (message: string) => void;
        }
    ) {
        this.enabled = enabled;
        this.schemas = new Map();

        // Create TextDocuments from the provided schema content
        for (const config of schemas) {
            try {
                const schemaUri = `bundled://schema/${config.languageId}`;
                const schemaDocument = TextDocument.create(
                    schemaUri,
                    'kson',
                    1,
                    config.schemaContent
                );
                this.schemas.set(config.languageId, schemaDocument);
                this.logger?.info(`Loaded bundled schema for language: ${config.languageId}`);
            } catch (error) {
                this.logger?.error(`Failed to load bundled schema for ${config.languageId}: ${error}`);
            }
        }

        this.logger?.info(`BundledSchemaProvider initialized with ${this.schemas.size} schemas, enabled: ${enabled}`);
    }

    /**
     * Get the schema for a document based on its language ID.
     *
     * @param _documentUri The URI of the KSON document (unused by bundled provider)
     * @param languageId The language ID to look up the schema for
     * @returns TextDocument containing the schema, or undefined if no bundled schema exists for this language
     */
    getSchemaForDocument(_documentUri: DocumentUri, languageId?: string): TextDocument | undefined {
        if (!this.enabled || !languageId) {
            return undefined;
        }

        return this.schemas.get(languageId);
    }

    /**
     * Reload the schema configuration.
     * For bundled schemas, this is a no-op since schemas are provided at construction time.
     */
    reload(): void {
        // No-op: bundled schemas are immutable and provided at construction time
    }

    /**
     * Check if a given file URI is a schema file.
     * Bundled schemas use the bundled:// scheme.
     *
     * @param fileUri The URI of the file to check
     * @returns True if the file is a bundled schema file
     */
    isSchemaFile(fileUri: DocumentUri): boolean {
        return fileUri.startsWith('bundled://schema/');
    }

    /**
     * Enable or disable bundled schema support.
     *
     * @param enabled Whether bundled schemas should be enabled
     */
    setEnabled(enabled: boolean): void {
        this.enabled = enabled;
        this.logger?.info(`BundledSchemaProvider ${enabled ? 'enabled' : 'disabled'}`);
    }

    /**
     * Check if bundled schemas are currently enabled.
     */
    isEnabled(): boolean {
        return this.enabled;
    }

    /**
     * Get the list of language IDs that have bundled schemas.
     */
    getAvailableLanguageIds(): string[] {
        return Array.from(this.schemas.keys());
    }

    /**
     * Check if a bundled schema exists for a given language ID.
     */
    hasBundledSchema(languageId: string): boolean {
        return this.schemas.has(languageId);
    }
}
