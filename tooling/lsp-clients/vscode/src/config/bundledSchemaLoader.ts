import * as vscode from 'vscode';
import { getLanguageConfiguration, BundledSchemaMapping } from './languageConfig';

/**
 * Configuration for a bundled schema to be passed to the LSP server.
 */
export interface BundledSchemaConfig {
    /** The language ID this schema applies to */
    languageId: string;
    /** The pre-loaded schema content as a string */
    schemaContent: string;
}

/**
 * Load all bundled schemas defined in the language configuration.
 * Uses vscode.workspace.fs for cross-platform file access (works in both browser and Node.js).
 *
 * @param extensionUri The URI of the extension root
 * @param logger Optional logger for debugging
 * @returns Array of loaded bundled schema configurations
 */
export async function loadBundledSchemas(
    extensionUri: vscode.Uri,
    logger?: { info: (msg: string) => void; warn: (msg: string) => void; error: (msg: string) => void }
): Promise<BundledSchemaConfig[]> {
    const { bundledSchemas } = getLanguageConfiguration();
    const loadedSchemas: BundledSchemaConfig[] = [];

    for (const mapping of bundledSchemas) {
        try {
            const schemaContent = await loadSchemaFile(extensionUri, mapping, logger);
            if (schemaContent) {
                loadedSchemas.push({
                    languageId: mapping.languageId,
                    schemaContent
                });
            }
        } catch (error) {
            logger?.warn(`Failed to load bundled schema for ${mapping.languageId}: ${error}`);
        }
    }

    logger?.info(`Loaded ${loadedSchemas.length} bundled schemas`);
    return loadedSchemas;
}

/**
 * Load a single schema file from the extension.
 */
async function loadSchemaFile(
    extensionUri: vscode.Uri,
    mapping: BundledSchemaMapping,
    logger?: { info: (msg: string) => void; warn: (msg: string) => void; error: (msg: string) => void }
): Promise<string | undefined> {
    try {
        const schemaUri = vscode.Uri.joinPath(extensionUri, mapping.schemaPath);
        const schemaBytes = await vscode.workspace.fs.readFile(schemaUri);
        const schemaContent = new TextDecoder().decode(schemaBytes);
        logger?.info(`Loaded bundled schema for ${mapping.languageId} from ${mapping.schemaPath}`);
        return schemaContent;
    } catch (error) {
        logger?.warn(`Schema file not found for ${mapping.languageId}: ${mapping.schemaPath}`);
        return undefined;
    }
}

/**
 * Check if bundled schemas are enabled via VS Code settings.
 */
export function areBundledSchemasEnabled(): boolean {
    const config = vscode.workspace.getConfiguration('kson');
    return config.get<boolean>('enableBundledSchemas', true);
}
