import { createConnection } from 'vscode-languageserver/node.js';
import { startKsonServer } from 'kson-language-server/node';

// Create connection for Node.js environment
const connection = createConnection();

// Set up console logging to use the connection
console.log = connection.console.log.bind(connection.console);
console.error = connection.console.error.bind(connection.console);

startKsonServer(connection);