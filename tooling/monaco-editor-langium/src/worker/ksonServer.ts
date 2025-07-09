import { createConnection, BrowserMessageReader, BrowserMessageWriter } from 'vscode-languageserver/browser';
import { startKsonServer } from 'kson-language-server';

// Create browser-specific message reader and writer
const messageReader = new BrowserMessageReader(self);
const messageWriter = new BrowserMessageWriter(self);

// Create connection for browser environment (Web Worker)
const connection = createConnection(messageReader, messageWriter);

// Set up console logging to use the connection
console.log = connection.console.log.bind(connection.console);
console.error = connection.console.error.bind(connection.console);

// Start the language server
startKsonServer(connection);