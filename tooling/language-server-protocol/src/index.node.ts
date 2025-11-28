// Node.js-specific exports
import { Connection } from 'vscode-languageserver';
import { startKsonServer as startKsonServerCore } from './startKsonServer.js';
import { createSchemaProvider } from './core/schema/createSchemaProvider.node.js';

/**
 * Starts the Kson Language Server for Node.js environments.
 * Includes file system-based schema provider support.
 */
export function startKsonServer(connection: Connection): void {
    startKsonServerCore(connection, createSchemaProvider);
}