import { SchemaConfigManager } from './SchemaConfigManager.js';
import { SCHEMA_CONFIG_FILENAME } from '../schema/SchemaConfig.js';

export interface RemoveSchemaParams {
    documentUri: string;
    workspaceRoot: string | null;
}

export interface RemoveSchemaResult {
    success: boolean;
    message: string;
}

/**
 * Removes a schema association from a document by updating the .kson-schema.kson configuration file.
 *
 * This command:
 * 1. Loads the .kson-schema.kson file
 * 2. Removes the schema mapping for the specified document
 * 3. Saves the updated configuration back to disk (or deletes the file if empty)
 */
export class RemoveSchemaCommand {
    /**
     * Execute the remove schema command.
     *
     * @param params Parameters containing document URI and workspace root
     * @returns Result indicating success or failure with a message
     */
    static execute(params: RemoveSchemaParams): RemoveSchemaResult {
        const {documentUri, workspaceRoot} = params;

        if (!workspaceRoot) {
            return {
                success: false,
                message: 'No workspace root available. Cannot modify schema configuration.'
            };
        }

        try {
            const configManager = SchemaConfigManager.tryLoad(workspaceRoot);

            if (!configManager) {
                return {
                    success: false,
                    message: `No schema configuration file found at ${SCHEMA_CONFIG_FILENAME}`
                };
            }

            const removed = configManager.removeSchema(documentUri);

            if (!removed) {
                return {
                    success: false,
                    message: `No schema association found for document`
                };
            }

            configManager.save();

            return {
                success: true,
                message: `Removed schema association from document`
            };
        } catch (error) {
            return {
                success: false,
                message: `Failed to remove schema association: ${error}`
            };
        }
    }
}