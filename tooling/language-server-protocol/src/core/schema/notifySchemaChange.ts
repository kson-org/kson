import {Connection} from 'vscode-languageserver';
import {KsonDocumentsManager} from '../document/KsonDocumentsManager.js';

export function notifySchemaChange(connection: Connection, documentManager: KsonDocumentsManager): void {
    documentManager.refreshDocumentSchemas();
    connection.sendNotification('kson/schemaConfigurationChanged');
    connection.sendRequest('workspace/diagnostic/refresh');
}
