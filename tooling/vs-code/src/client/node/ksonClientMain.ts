import * as vscode from 'vscode';
import {
    ServerOptions,
    TransportKind,
    LanguageClientOptions,
    LanguageClient
} from 'vscode-languageclient/node';
import {
    createClientOptions
} from '../../config/clientOptions';

/**
 * Async disposable interface for clean resource management.
 */
export interface AsyncDisposable {
    dispose(): Promise<void>;
}

let client: AsyncDisposable | undefined;

/**
 * Node.js-specific activation function for the KSON extension.
 * This handles desktop VS Code environments where we can spawn child processes.
 */
export async function activate(context: vscode.ExtensionContext) {
    // Create log output channel
    const logOutputChannel = vscode.window.createOutputChannel('Kson Language Server', {log: true});
    context.subscriptions.push(logOutputChannel);

    try {
        const serverModule = context.asAbsolutePath('./dist/server.js');

        // Debug options for development
        const debugPort = process.env.KSON_DEBUG_PORT || '6009';
        const debugOptions = {
            execArgv: ['--nolazy', '--inspect=' + debugPort]
        };

        // Create server options for Node.js environment
        const serverOptions: ServerOptions = {
            run: {module: serverModule, transport: TransportKind.ipc},
            debug: {module: serverModule, transport: TransportKind.ipc, options: debugOptions}
        };
        const clientOptions: LanguageClientOptions = createClientOptions(logOutputChannel)
        const languageClient: LanguageClient = new LanguageClient("kson", serverOptions, clientOptions, false)

        await languageClient.start();
        console.log('Kson Language Server started');

        logOutputChannel.info('KSON Node.js extension activated successfully');
    } catch (error) {
        const message = error instanceof Error ? error.message : String(error);
        logOutputChannel.error(`Failed to activate KSON Node.js extension: ${message}`);
        vscode.window.showErrorMessage('Failed to activate KSON language support.');
    }
}

/**
 * Deactivation function for Node.js environment.
 */
export async function deactivate(): Promise<void> {
    if (client) {
        await client.dispose();
        client = undefined;
    }
} 