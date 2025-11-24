import { SchemaConfigManager } from './SchemaConfigManager.js';

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
 * 1. Loads or creates the .kson-schema.kson file
 * 2. Adds or updates a schema mapping for the document
 * 3. Uses exact file path matching (no wildcards) for precise association
 * 4. Saves the updated configuration back to disk
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
            const configManager = SchemaConfigManager.load(workspaceRoot);
            configManager.associateSchema(documentUri, schemaPath);
            configManager.save();

            return {
                success: true,
                message: `Associated schema "${schemaPath}" with document`
            };
        } catch (error) {
            return {
                success: false,
                message: `Failed to associate schema: ${error}`
            };
        }
    }
}
