import {SchemaProvider} from './SchemaProvider.js';

/**
 * Browser-specific schema provider factory.
 * Returns undefined since browsers don't have file system access.
 */
export async function createSchemaProvider(
    workspaceRootUri: string | undefined,
    logger: { info: (msg: string) => void; warn: (msg: string) => void; error: (msg: string) => void }
): Promise<SchemaProvider | undefined> {
    logger.info('Running in browser environment, schema provider disabled');
    return undefined;
}