import {TextDocument} from 'vscode-languageserver-textdocument';
import {DocumentUri} from 'vscode-languageserver';

/**
 * Interface for providing schemas in different environments (Node.js, Browser).
 * This abstraction allows the schema system to work without direct file system access.
 */
export interface SchemaProvider {
    /**
     * Get the schema document for a given KSON document URI.
     *
     * @param documentUri The URI of the KSON document
     * @returns TextDocument containing the schema, or undefined if no schema is available
     */
    getSchemaForDocument(documentUri: DocumentUri): TextDocument | undefined;

    /**
     * Reload the schema configuration.
     * Should be called when configuration changes are detected.
     */
    reload(): void;

    /**
     * Check if a given file URI is a schema file referenced in the configuration.
     *
     * @param fileUri The URI of the file to check
     * @returns True if the file is a schema file
     */
    isSchemaFile(fileUri: DocumentUri): boolean;
}

/**
 * A no-op schema provider that returns undefined for all requests.
 * Used as a fallback when no schema configuration is available.
 */
export class NoOpSchemaProvider implements SchemaProvider {
    getSchemaForDocument(_documentUri: DocumentUri): TextDocument | undefined {
        return undefined;
    }

    reload(): void {
        // No-op
    }

    isSchemaFile(_fileUri: DocumentUri): boolean {
        return false;
    }
}
