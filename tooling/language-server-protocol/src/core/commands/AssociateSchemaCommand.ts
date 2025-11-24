import * as fs from 'fs';
import * as path from 'path';
import {Kson, Result} from 'kson';
import {SchemaConfig, isValidSchemaConfig, SCHEMA_CONFIG_FILENAME} from '../schema/SchemaConfig.js';
import { URI } from 'vscode-uri'

export interface AssociateSchemaParams {
    documentUri: string;
    schemaPath: string;
    workspaceRoot: string | null;
}

export interface AssociateSchemaResult {
    success: boolean;
    message: string;
}

/**
 * Associates a schema with a document by updating the .kson-schema.kson configuration file.
 *
 * This command:
 * 1. Reads or creates the .kson-schema.kson file
 * 2. Adds or updates a schema mapping for the document
 * 3. Uses exact file path matching (no wildcards) for precise association
 * 4. Writes the updated configuration back to disk
 */
export class AssociateSchemaCommand {
    /**
     * Execute the associate schema command.
     *
     * @param params Parameters containing document URI, schema path, and workspace root
     * @returns Result indicating success or failure with a message
     */
    static execute(params: AssociateSchemaParams): AssociateSchemaResult {
        const {documentUri, schemaPath, workspaceRoot} = params;

        if (!workspaceRoot) {
            return {
                success: false,
                message: 'No workspace root available. Cannot create schema configuration.'
            };
        }

        try {
            // Convert document URI to relative path
            const documentPath = URI.parse(documentUri).fsPath
            const relativePath = path.relative(workspaceRoot, documentPath);

            // Read or create configuration
            const config = this.readOrCreateConfig(workspaceRoot);

            // Update configuration with new mapping
            this.updateConfig(config, relativePath, schemaPath);

            // Write configuration back to disk
            this.writeConfig(workspaceRoot, config);

            return {
                success: true,
                message: `Associated schema "${schemaPath}" with "${relativePath}"`
            };
        } catch (error) {
            return {
                success: false,
                message: `Failed to associate schema: ${error}`
            };
        }
    }

    /**
     * Read existing configuration or create a new one.
     */
    private static readOrCreateConfig(workspaceRoot: string): SchemaConfig {
        const configPath = path.join(workspaceRoot, SCHEMA_CONFIG_FILENAME);

        if (!fs.existsSync(configPath)) {
            // Return empty configuration
            return {schemas: []};
        }

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
     * Update configuration with new schema mapping.
     * Uses exact file path matching for precise association.
     * If a mapping already exists for this file, it updates the schema path.
     */
    private static updateConfig(config: SchemaConfig, relativePath: string, schemaPath: string): void {
        // Normalize path separators to forward slashes for consistency
        const normalizedPath = relativePath.replace(/\\/g, '/');

        // Check if there's already an exact match mapping for this file
        const existingMapping = config.schemas.find(mapping =>
            mapping.fileMatch.length === 1 &&
            mapping.fileMatch[0] === normalizedPath
        );

        if (existingMapping) {
            // Update existing mapping
            existingMapping.schema = schemaPath;
        } else {
            // Add new mapping with exact file path (no wildcards)
            config.schemas.push({
                fileMatch: [normalizedPath],
                schema: schemaPath
            });
        }
    }

    /**
     * Write configuration to disk in KSON format.
     * The .kson-schema.kson file is actually JSON format (KSON is a superset of JSON).
     */
    private static writeConfig(workspaceRoot: string, config: SchemaConfig): void {
        const configPath = path.join(workspaceRoot, SCHEMA_CONFIG_FILENAME);

        // Write as KSON
        const jsonString = JSON.stringify(config, null, 2);
        fs.writeFileSync(configPath, Kson.getInstance().format(jsonString), 'utf-8');
    }
}
