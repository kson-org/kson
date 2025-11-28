import {TextDocument} from 'vscode-languageserver-textdocument';
import {DocumentUri} from 'vscode-languageserver';
import { URI } from 'vscode-uri';
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
    private workspaceRoot: URI | null;

    /**
     * Creates a new FileSystemSchemaProvider.
     *
     * @param workspaceRootUri The workspace root URI (e.g., "file:///path/to/workspace")
     * @param logger Optional logger for warnings and errors
     */
    constructor(
        workspaceRootUri: URI | null,
        private logger?: {
            info: (message: string) => void;
            warn: (message: string) => void;
            error: (message: string) => void;
        }
    ) {
        this.workspaceRoot = workspaceRootUri
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

        const documentPath = URI.parse(documentUri).fsPath;
        const relativePath = this.getRelativePath(documentPath);

        // Normalize path separators for consistent matching
        const normalizedPath = relativePath.replace(/\\/g, '/');

        // First, check for exact path matches (higher priority - these are manually associated)
        // Exact matches don't contain wildcards and match the file path exactly
        for (const mapping of this.config.schemas) {
            const exactMatches = mapping.fileMatch.filter(pattern => !this.hasWildcard(pattern));
            for (const exactPath of exactMatches) {
                const normalizedPattern = exactPath.replace(/\\/g, '/');
                if (normalizedPath === normalizedPattern) {
                    return this.loadSchemaFile(mapping.schema);
                }
            }
        }

        // Then, check glob patterns (lower priority - these are general rules)
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

        const configPath = path.join(this.workspaceRoot.fsPath, SCHEMA_CONFIG_FILENAME);

        if (!fs.existsSync(configPath)) {
            this.logger?.info(`No ${SCHEMA_CONFIG_FILENAME} found in workspace root`);
            this.config = null;
            return;
        }

        try {
            const configContent = fs.readFileSync(configPath, 'utf-8');
            const ksonConfigResult = Kson.getInstance().toJson(configContent);
              const parsedConfig = ksonConfigResult instanceof Result.Success
                  ? JSON.parse(ksonConfigResult.output)
                  : (() => {
                      this.logger?.error(`Failed to parse ${SCHEMA_CONFIG_FILENAME}: ${(ksonConfigResult as Result.Failure).errors.asJsReadonlyArrayView().join(', ')}`);
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

        const absolutePath = path.join(this.workspaceRoot.fsPath, schemaPath);

        if (!fs.existsSync(absolutePath)) {
            this.logger?.warn(`Schema file not found: ${schemaPath}`);
            return undefined;
        }

        try {
            const schemaContent = fs.readFileSync(absolutePath, 'utf-8');

            // Check whether the schema is a valid KSON file
            let ksonSchema = Kson.getInstance().analyze(schemaContent)
            let schemaErrors = ksonSchema.errors.asJsReadonlyArrayView()
            if (schemaErrors.length != 0) {
                this.logger?.error(`Failed to convert KSON schema to JSON: ${schemaErrors.join(', ')}`);
                return undefined;
            }

            const schemaUri = URI.file(absolutePath).toString();
            return TextDocument.create(schemaUri, 'kson', 1, schemaContent);
        } catch (error) {
            this.logger?.error(`Failed to load schema file ${schemaPath}: ${error}`);
            return undefined;
        }
    }

    /**
     * Check if a pattern contains wildcard characters.
     * Wildcard characters are: *, ?, [, ]
     *
     * @param pattern The pattern to check
     * @returns True if the pattern contains wildcards
     */
    private hasWildcard(pattern: string): boolean {
        return /[*?\[\]]/.test(pattern);
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
        return path.relative(this.workspaceRoot.fsPath, absolutePath);
    }
}
