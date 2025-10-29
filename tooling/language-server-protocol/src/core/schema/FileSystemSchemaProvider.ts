import {TextDocument} from 'vscode-languageserver-textdocument';
import {DocumentUri} from 'vscode-languageserver';
import {SchemaConfig, isValidSchemaConfig, SCHEMA_CONFIG_FILENAME} from './SchemaConfig.js';
import {SchemaProvider} from './SchemaProvider.js';
import { Kson, Result} from 'kson'
import * as fs from 'fs';
import * as path from 'path';
import minimatch from 'minimatch';

/**
 * Node.js-specific schema provider that reads from the file system.
 * This provider loads .kson-schema.json from the workspace root and resolves schemas for documents.
 */
export class FileSystemSchemaProvider implements SchemaProvider {
    private config: SchemaConfig | null = null;
    private workspaceRoot: string | null;

    /**
     * Creates a new FileSystemSchemaProvider.
     *
     * @param workspaceRootUri The workspace root URI (e.g., "file:///path/to/workspace")
     * @param logger Optional logger for warnings and errors
     */
    constructor(
        workspaceRootUri: string | null,
        private logger?: {
            info: (message: string) => void;
            warn: (message: string) => void;
            error: (message: string) => void;
        }
    ) {
        this.workspaceRoot = workspaceRootUri ? this.uriToPath(workspaceRootUri) : null;
        this.loadConfiguration();
    }

    /**
     * Reload the schema configuration from disk.
     * Should be called when .kson-schema.json changes.
     */
    reload(): void {
        this.loadConfiguration();
    }

    /**
     * Get the schema for a given document URI.
     *
     * @param documentUri The URI of the KSON document
     * @returns TextDocument containing the schema, or undefined if no schema is configured
     */
    getSchemaForDocument(documentUri: DocumentUri): TextDocument | undefined {
        if (!this.config || !this.workspaceRoot) {
            return undefined;
        }

        const documentPath = this.uriToPath(documentUri);
        const relativePath = this.getRelativePath(documentPath);

        // Find the first matching schema mapping
        for (const mapping of this.config.schemas) {
            if (this.matchesAnyPattern(relativePath, mapping.fileMatch)) {
                return this.loadSchemaFile(mapping.schema);
            }
        }

        return undefined;
    }

    /**
     * Load and parse the {@link SCHEMA_CONFIG_FILENAME} configuration file.
     */
    private loadConfiguration(): void {
        if (!this.workspaceRoot) {
            this.logger?.info('No workspace root available, schema configuration disabled');
            return;
        }

        const configPath = path.join(this.workspaceRoot, SCHEMA_CONFIG_FILENAME);

        if (!fs.existsSync(configPath)) {
            this.logger?.info(`No ${SCHEMA_CONFIG_FILENAME} found in workspace root`);
            this.config = null;
            return;
        }

        try {
            const configContent = fs.readFileSync(configPath, 'utf-8');
            const ksonConfigResult = Kson.getInstance().toJson(configContent, false);
              const parsedConfig = ksonConfigResult instanceof Result.Success
                  ? JSON.parse(ksonConfigResult.output)
                  : (() => {
                      this.logger?.error(`Failed to parse ${SCHEMA_CONFIG_FILENAME}: ${(ksonConfigResult as Result.Failure).errors.join(', ')}`);
                      this.config = null;
                      return null;
                  })();

              if (!parsedConfig) return;

            if (!isValidSchemaConfig(parsedConfig)) {
                this.logger?.error(`Invalid ${SCHEMA_CONFIG_FILENAME} format`);
                this.config = null;
                return;
            }

            this.config = parsedConfig;
            this.logger?.info(`Loaded schema configuration with ${this.config.schemas.length} mappings`);
        } catch (error) {
            this.logger?.error(`Failed to load ${SCHEMA_CONFIG_FILENAME}: ${error}`);
            this.config = null;
        }
    }

    /**
     * Load a schema file from a workspace-relative path.
     * If the schema file is in KSON format (.kson), it will be converted to JSON.
     *
     * @param schemaPath Workspace-relative path to the schema file
     * @returns TextDocument containing the schema in JSON format, or undefined if file not found
     */
    private loadSchemaFile(schemaPath: string): TextDocument | undefined {
        if (!this.workspaceRoot) {
            return undefined;
        }

        const absolutePath = path.join(this.workspaceRoot, schemaPath);

        if (!fs.existsSync(absolutePath)) {
            this.logger?.warn(`Schema file not found: ${schemaPath}`);
            return undefined;
        }

        try {
            const schemaContent = fs.readFileSync(absolutePath, 'utf-8');

            // If the schema file is in KSON format, convert it to JSON
            let jsonSchemaContent = schemaContent;
            if (schemaPath.endsWith('.kson')) {
                const ksonResult = Kson.getInstance().toJson(schemaContent, false);

                if (ksonResult instanceof Result.Success) {
                    jsonSchemaContent = ksonResult.output;
                } else {
                    this.logger?.error(`Failed to convert KSON schema to JSON: ${(ksonResult as Result.Failure).errors.join(', ')}`);
                    return undefined;
                }
            }

            const schemaUri = this.pathToUri(absolutePath);
            return TextDocument.create(schemaUri, 'json', 1, jsonSchemaContent);
        } catch (error) {
            this.logger?.error(`Failed to load schema file ${schemaPath}: ${error}`);
            return undefined;
        }
    }

    /**
     * Check if a file path matches any of the given glob patterns.
     * Supports common glob patterns: *, **, ?, and character classes.
     *
     * @param filePath The file path to check (relative to workspace)
     * @param patterns Array of glob patterns
     * @returns True if the path matches any pattern
     */
    private matchesAnyPattern(filePath: string, patterns: string[]): boolean {
        return patterns.some(pattern => {
            // Use forward slashes for consistent matching across platforms
            const normalizedPath = filePath.replace(/\\/g, '/');
            const normalizedPattern = pattern.replace(/\\/g, '/');
            return minimatch(normalizedPath, normalizedPattern);
        });
    }

    /**
     * Get the relative path from workspace root to a file.
     *
     * @param absolutePath Absolute file path
     * @returns Workspace-relative path
     */
    private getRelativePath(absolutePath: string): string {
        if (!this.workspaceRoot) {
            return absolutePath;
        }
        return path.relative(this.workspaceRoot, absolutePath);
    }

    /**
     * Convert a file:// URI to a file system path.
     *
     * @param uri The URI to convert
     * @returns File system path
     */
    private uriToPath(uri: string): string {
        // Simple implementation - handle file:// URIs
        if (uri.startsWith('file://')) {
            // Decode URI components and remove file:// prefix
            let filePath = decodeURIComponent(uri.substring(7));

            // On Windows, file:///c:/path becomes /c:/path, need to remove leading slash
            if (process.platform === 'win32' && /^\/[a-z]:/i.test(filePath)) {
                filePath = filePath.substring(1);
            }

            return filePath;
        }
        return uri;
    }

    /**
     * Convert a file system path to a file:// URI.
     *
     * @param filePath The file path to convert
     * @returns file:// URI
     */
    private pathToUri(filePath: string): string {
        // Normalize path separators to forward slashes
        const normalized = filePath.replace(/\\/g, '/');

        // On Windows, add extra slash for drive letter
        if (process.platform === 'win32' && /^[a-z]:/i.test(normalized)) {
            return `file:///${normalized}`;
        }

        return `file://${normalized}`;
    }
}
