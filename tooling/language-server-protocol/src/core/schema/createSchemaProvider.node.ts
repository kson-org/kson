import {SchemaProvider} from './SchemaProvider.js';
import {FileSystemSchemaProvider} from './FileSystemSchemaProvider';
import {URI} from "vscode-uri";

/**
 * Node.js-specific schema provider factory.
 * Creates {@link FileSystemSchemaProvider} with file system access.
 */
export async function createSchemaProvider(
    workspaceRootUri: URI | undefined,
    logger: { info: (msg: string) => void; warn: (msg: string) => void; error: (msg: string) => void }
): Promise<SchemaProvider | undefined> {
    try {
        logger.info('Running in Node.js environment, using FileSystemSchemaProvider');
        return new FileSystemSchemaProvider(workspaceRootUri || null, logger);
    } catch (error) {
        logger.error(`Failed to create FileSystemSchemaProvider: ${error}`);
        return undefined;
    }
}