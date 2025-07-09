import { 
    BrowserMessageReader, 
    BrowserMessageWriter,
} from 'vscode-languageserver/browser';
import { createConnection } from 'vscode-languageserver/browser';
import { startKsonServer } from 'kson-language-server';

/**
 * Creates browser-specific message reader and writer.
 * This is shared between VS Code Web and Monaco Editor.
 */
function createBrowserMessageTransports(context: Window & typeof globalThis) {
    const messageReader = new BrowserMessageReader(context);
    const messageWriter = new BrowserMessageWriter(context);
    
    return { messageReader, messageWriter };
}

/**
 * Sets up console logging to use a connection.
 * Shared utility for redirecting console output to LSP connection.
 */
function setupConnectionLogging(connection: { console: { log: Function, error: Function } }) {
    console.log = connection.console.log.bind(connection.console);
    console.error = connection.console.error.bind(connection.console);
}

export function createAndStartBrowserWorker(){

    const { messageReader, messageWriter } = createBrowserMessageTransports(self);
    const connection = createConnection(messageReader, messageWriter);

    setupConnectionLogging(connection);

    startKsonServer(connection);
}