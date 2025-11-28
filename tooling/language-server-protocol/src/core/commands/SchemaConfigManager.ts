import * as fs from 'fs';
import * as path from 'path';
import {Kson, Result} from 'kson';
import {SchemaConfig, isValidSchemaConfig, SCHEMA_CONFIG_FILENAME} from '../schema/SchemaConfig.js';
import { URI } from 'vscode-uri';

/**
 * Manages schema configuration operations for a workspace.
 * Provides a clean abstraction over .kson-schema.kson file operations.
 */
export class SchemaConfigManager {
    private config: SchemaConfig;

    private constructor(
        private readonly workspaceRoot: string,
        config: SchemaConfig
    ) {
        this.config = config;
    }

    /**
     * Load the schema configuration from the workspace.
     * Creates an empty configuration if the file doesn't exist.
     */
    static load(workspaceRoot: string): SchemaConfigManager {
        const configPath = path.join(workspaceRoot, SCHEMA_CONFIG_FILENAME);

        if (!fs.existsSync(configPath)) {
            return new SchemaConfigManager(workspaceRoot, { schemas: [] });
        }

        const config = this.parseConfigFile(configPath);
        return new SchemaConfigManager(workspaceRoot, config);
    }

    /**
     * Try to load the schema configuration from the workspace.
     * Returns null if the file doesn't exist.
     */
    static tryLoad(workspaceRoot: string): SchemaConfigManager | null {
        const configPath = path.join(workspaceRoot, SCHEMA_CONFIG_FILENAME);

        if (!fs.existsSync(configPath)) {
            return null;
        }

        const config = this.parseConfigFile(configPath);
        return new SchemaConfigManager(workspaceRoot, config);
    }

    /**
     * Parse a schema configuration file.
     */
    private static parseConfigFile(configPath: string): SchemaConfig {
        try {
            const configContent = fs.readFileSync(configPath, 'utf-8');
            const ksonConfigResult = Kson.getInstance().toJson(configContent);

            const parsedConfig = ksonConfigResult instanceof Result.Success
                ? JSON.parse(ksonConfigResult.output)
                : (() => {
                    throw new Error(`Failed to parse ${SCHEMA_CONFIG_FILENAME}: ${(ksonConfigResult as Result.Failure).errors.asJsReadonlyArrayView().join(', ')}`);
                })();

            if (!isValidSchemaConfig(parsedConfig)) {
                throw new Error(`Invalid ${SCHEMA_CONFIG_FILENAME} format`);
            }

            return parsedConfig;
        } catch (error) {
            throw new Error(`Failed to read ${SCHEMA_CONFIG_FILENAME}: ${error}`);
        }
    }

    /**
     * Convert document URI to normalized relative path.
     * Uses forward slashes for cross-platform compatibility in config files.
     */
    private documentUriToNormalizedPath(documentUri: string): string {
        const documentPath = URI.parse(documentUri).fsPath;
        const relativePath = path.relative(this.workspaceRoot, documentPath);
        // Normalize to forward slashes for consistency in config file across platforms
        // (path.relative returns platform-specific separators)
        return relativePath.replace(/\\/g, '/');
    }

    /**
     * Associate a schema with a document.
     * Updates existing association if present, otherwise creates a new one.
     */
    associateSchema(documentUri: string, schemaPath: string): void {
        const normalizedPath = this.documentUriToNormalizedPath(documentUri);

        const existingMapping = this.config.schemas.find(mapping =>
            mapping.fileMatch.length === 1 &&
            mapping.fileMatch[0] === normalizedPath
        );

        if (existingMapping) {
            existingMapping.schema = schemaPath;
        } else {
            this.config.schemas.push({
                fileMatch: [normalizedPath],
                schema: schemaPath
            });
        }
    }

    /**
     * Remove schema association for a document.
     * Returns true if an association was removed, false if none existed.
     */
    removeSchema(documentUri: string): boolean {
        const normalizedPath = this.documentUriToNormalizedPath(documentUri);

        const mappingIndex = this.config.schemas.findIndex(mapping =>
            mapping.fileMatch.length === 1 &&
            mapping.fileMatch[0] === normalizedPath
        );

        if (mappingIndex === -1) {
            return false;
        }

        this.config.schemas.splice(mappingIndex, 1);
        return true;
    }

    /**
     * Check if the configuration is empty (no schema associations).
     */
    isEmpty(): boolean {
        return this.config.schemas.length === 0;
    }

    /**
     * Save the configuration to disk.
     * Deletes the file if the configuration is empty.
     */
    save(): void {
        const configPath = path.join(this.workspaceRoot, SCHEMA_CONFIG_FILENAME);

        if (this.isEmpty()) {
            if (fs.existsSync(configPath)) {
                fs.unlinkSync(configPath);
            }
        } else {
            const jsonString = JSON.stringify(this.config, null, 2);
            fs.writeFileSync(configPath, Kson.getInstance().format(jsonString), 'utf-8');
        }
    }
}