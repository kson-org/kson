import * as vscode from 'vscode';
import { getLanguageConfiguration, BundledSchemaMapping } from './languageConfig';

import type { BundledSchemaConfig, BundledMetaSchemaConfig } from 'kson-language-server';
export type { BundledSchemaConfig, BundledMetaSchemaConfig };

/**
 * Path to the metaschema file relative to the extension root.
 * Currently uses JSON Schema Draft 7 as the schema for .schema.kson files.
 */
const METASCHEMA_PATH = './dist/extension/schemas/metaschema.draft7.kson';

/**
 * Load all bundled schemas from the language configuration.
 * Does NOT include the metaschema — use {@link loadBundledMetaSchemas} for that.
 *
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
    const loadedSchemas: BundledSchemaConfig[] = [];

    // Load bundled schemas from language configuration
    const { bundledSchemas } = getLanguageConfiguration();
    for (const mapping of bundledSchemas) {
        try {
            const schemaContent = await loadSchemaFile(extensionUri, mapping, logger);
            if (schemaContent) {
                loadedSchemas.push({
                    fileExtension: mapping.fileExtension,
                    schemaContent
                });
            }
        } catch (error) {
            logger?.warn(`Failed to load bundled schema for ${mapping.fileExtension}: ${error}`);
        }
    }

    logger?.info(`Loaded ${loadedSchemas.length} bundled schemas`);
    return loadedSchemas;
}

/**
 * Load bundled metaschemas.
 * Metaschemas are matched by a document's $schema field content rather than by file extension.
 *
 * @param extensionUri The URI of the extension root
 * @param logger Optional logger for debugging
 * @returns Array of loaded bundled metaschema configurations
 */
export async function loadBundledMetaSchemas(
    extensionUri: vscode.Uri,
    logger?: { info: (msg: string) => void; warn: (msg: string) => void; error: (msg: string) => void }
): Promise<BundledMetaSchemaConfig[]> {
    const loadedMetaSchemas: BundledMetaSchemaConfig[] = [];

    const metaschema = await loadMetaSchemaFile(extensionUri, logger);
    if (metaschema) {
        loadedMetaSchemas.push(metaschema);
    }

    logger?.info(`Loaded ${loadedMetaSchemas.length} bundled metaschemas`);
    return loadedMetaSchemas;
}

/**
 * Load the metaschema file (JSON Schema Draft 7).
 */
async function loadMetaSchemaFile(
    extensionUri: vscode.Uri,
    logger?: { info: (msg: string) => void; warn: (msg: string) => void; error: (msg: string) => void }
): Promise<BundledMetaSchemaConfig | undefined> {
    try {
        const schemaUri = vscode.Uri.joinPath(extensionUri, METASCHEMA_PATH);
        const schemaBytes = await vscode.workspace.fs.readFile(schemaUri);
        const schemaContent = new TextDecoder().decode(schemaBytes);
        logger?.info(`Loaded metaschema from ${METASCHEMA_PATH}`);
        return {
            schemaId: 'http://json-schema.org/draft-07/schema#',
            name: 'draft-07',
            schemaContent
        };
    } catch (error) {
        logger?.warn(`Metaschema not found at ${METASCHEMA_PATH}: ${error}`);
        return undefined;
    }
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
        logger?.info(`Loaded bundled schema for .${mapping.fileExtension} from ${mapping.schemaPath}`);
        return schemaContent;
    } catch (error) {
        logger?.warn(`Schema file not found for .${mapping.fileExtension}: ${mapping.schemaPath}`);
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
