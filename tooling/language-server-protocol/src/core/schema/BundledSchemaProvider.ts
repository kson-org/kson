import {TextDocument} from 'vscode-languageserver-textdocument';
import {DocumentUri} from 'vscode-languageserver';
import {SchemaProvider} from './SchemaProvider.js';

/**
 * Configuration for a bundled schema.
 */
export interface BundledSchemaConfig {
    /** The file extension this schema applies to (e.g., 'schema.kson', 'kxt') */
    fileExtension: string;
    /** The pre-loaded schema content as a string */
    schemaContent: string;
}

/**
 * Configuration for a bundled metaschema.
 * Metaschemas are matched by the document's $schema field value rather than file extension.
 */
export interface BundledMetaSchemaConfig {
    /** The $id to match against a document's $schema field (e.g., "http://json-schema.org/draft-07/schema#") */
    schemaId: string;
    /** Name for URI generation (e.g., "draft-07") */
    name: string;
    /** The pre-loaded schema content as a string */
    schemaContent: string;
}

/**
 * Options for creating a BundledSchemaProvider.
 */
export interface BundledSchemaProviderOptions {
    /** Array of bundled schema configurations (matched by file extension) */
    schemas: BundledSchemaConfig[];
    /** Array of bundled metaschema configurations (matched by $schema content) */
    metaSchemas?: BundledMetaSchemaConfig[];
    /** Whether bundled schemas are enabled (default: true) */
    enabled?: boolean;
    /** Optional logger for warnings and errors */
    logger?: {
        info: (message: string) => void;
        warn: (message: string) => void;
        error: (message: string) => void;
    };
}

/**
 * Schema provider for bundled schemas that are shipped with the extension.
 * Works in both browser and Node.js environments since it doesn't require file system access.
 *
 * Bundled schemas are identified by file extension and use a special URI scheme:
 * bundled://schema/{fileExtension}.schema.kson
 *
 * Bundled metaschemas are identified by their $id and use:
 * bundled://metaschema/{name}.schema.kson
 *
 * The .schema.kson suffix allows VS Code to recognize the file as KSON and apply
 * syntax highlighting and other language features when navigating to definitions.
 */
export class BundledSchemaProvider implements SchemaProvider {
    private schemas: Map<string, TextDocument>;
    private metaSchemas: Map<string, TextDocument>;
    private enabled: boolean;
    private logger?: BundledSchemaProviderOptions['logger'];

    constructor(options: BundledSchemaProviderOptions) {
        const { schemas, metaSchemas = [], enabled = true, logger } = options;
        this.enabled = enabled;
        this.logger = logger;
        this.schemas = new Map();
        this.metaSchemas = new Map();

        // Create TextDocuments from the provided schema content
        for (const config of schemas) {
            try {
                // Include .schema.kson suffix so VS Code recognizes it as KSON for syntax highlighting
                const schemaUri = `bundled://schema/${config.fileExtension}.schema.kson`;
                const schemaDocument = TextDocument.create(
                    schemaUri,
                    'kson',
                    1,
                    config.schemaContent
                );
                this.schemas.set(config.fileExtension, schemaDocument);
                this.logger?.info(`Loaded bundled schema for extension: ${config.fileExtension}`);
            } catch (error) {
                this.logger?.error(`Failed to load bundled schema for ${config.fileExtension}: ${error}`);
            }
        }

        // Create TextDocuments from the provided metaschema content
        for (const config of metaSchemas) {
            try {
                const metaSchemaUri = `bundled://metaschema/${config.name}.schema.kson`;
                const metaSchemaDocument = TextDocument.create(
                    metaSchemaUri,
                    'kson',
                    1,
                    config.schemaContent
                );
                this.metaSchemas.set(config.schemaId, metaSchemaDocument);
                this.logger?.info(`Loaded bundled metaschema: ${config.name} (${config.schemaId})`);
            } catch (error) {
                this.logger?.error(`Failed to load bundled metaschema ${config.name}: ${error}`);
            }
        }

        this.logger?.info(`BundledSchemaProvider initialized with ${this.schemas.size} schemas and ${this.metaSchemas.size} metaschemas, enabled: ${enabled}`);
    }

    /**
     * Get the schema for a document based on its file extension.
     *
     * @param documentUri The URI of the KSON document
     * @returns TextDocument containing the schema, or undefined if no bundled schema exists for this extension
     */
    getSchemaForDocument(documentUri: DocumentUri): TextDocument | undefined {
        if (!this.enabled) {
            return undefined;
        }

        const matchedExtension = this.findMatchingExtension(documentUri);
        if (!matchedExtension) {
            return undefined;
        }

        return this.schemas.get(matchedExtension);
    }

    /**
     * Get a bundled metaschema by its schema ID.
     * Used for content-based schema resolution when a document declares $schema.
     *
     * @param schemaId The $id of the metaschema to look up
     * @returns TextDocument containing the metaschema, or undefined if no match
     */
    getMetaSchemaForId(schemaId: string): TextDocument | undefined {
        if (!this.enabled) {
            return undefined;
        }
        return this.metaSchemas.get(schemaId);
    }

    /**
     * Find a matching file extension from the available bundled schemas.
     * Checks if the URI ends with any registered extension (preceded by a dot).
     * If multiple extensions match, returns the longest one (most specific).
     *
     * For example, with extensions ['kson', 'orchestra.kson']:
     * - 'file:///test.kson' matches 'kson'
     * - 'file:///test.orchestra.kson' matches 'orchestra.kson' (longer/more specific)
     *
     * @param uri The URI to match against available extensions
     * @returns The matched file extension, or undefined if none match
     */
    private findMatchingExtension(uri: string): string | undefined {
        // Extract just the filename part (after last slash)
        const lastSlash = Math.max(uri.lastIndexOf('/'), uri.lastIndexOf('\\'));
        const filename = lastSlash >= 0 ? uri.substring(lastSlash + 1) : uri;

        // Find all extensions that match the end of the filename
        let bestMatch: string | undefined;
        for (const extension of this.schemas.keys()) {
            const suffix = '.' + extension;
            if (filename.endsWith(suffix)) {
                // Prefer longer matches (more specific extensions)
                if (!bestMatch || extension.length > bestMatch.length) {
                    bestMatch = extension;
                }
            }
        }
        return bestMatch;
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
     * Bundled schemas use the bundled:// scheme with .schema.kson suffix.
     *
     * @param fileUri The URI of the file to check
     * @returns True if the file is a bundled schema file
     */
    isSchemaFile(fileUri: DocumentUri): boolean {
        return (fileUri.startsWith('bundled://schema/') || fileUri.startsWith('bundled://metaschema/'))
            && fileUri.endsWith('.schema.kson');
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
     * Get the list of file extensions that have bundled schemas.
     */
    getAvailableFileExtensions(): string[] {
        return Array.from(this.schemas.keys());
    }

    /**
     * Check if a bundled schema exists for a given file extension.
     */
    hasBundledSchema(fileExtension: string): boolean {
        return this.schemas.has(fileExtension);
    }
}
