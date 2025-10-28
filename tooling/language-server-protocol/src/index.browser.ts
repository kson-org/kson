// Browser-specific exports
import { Connection } from 'vscode-languageserver';
import { startKsonServer as startKsonServerCore } from './startKsonServer.js';
import { createSchemaProvider } from './core/schema/createSchemaProvider.browser.js';

/**
 * Starts the Kson Language Server for browser environments.
 * No schema provider support (no file system access).
 */
export function startKsonServer(connection: Connection): void {
    startKsonServerCore(connection, createSchemaProvider);
}