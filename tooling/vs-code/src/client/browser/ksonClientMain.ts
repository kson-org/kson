import * as vscode from 'vscode';
import { LanguageClient } from 'vscode-languageclient/browser';
import { createClientOptions } from '../../config/clientOptions';

let languageClient: LanguageClient | undefined;

/**
 * Browser-specific activation function for the KSON extension.
 * This handles VS Code Web and GitHub.dev environments where we run in a browser.
 */
export async function activate(context: vscode.ExtensionContext) {
    // Create log output channel
    const logOutputChannel = vscode.window.createOutputChannel('Kson Language Server', { log: true });
    context.subscriptions.push(logOutputChannel);

    try {
        // Create a web worker that runs the server
        const serverModule = vscode.Uri.joinPath(context.extensionUri, 'dist', 'browserServer.js');
        const worker = new Worker(serverModule.toString(true));

        // Create the language client options
        const clientOptions = createClientOptions(logOutputChannel);

        // In test environments, we need to support the vscode-test-web scheme
        // This is only needed for the test runner, not in production
        if (context.extensionMode === vscode.ExtensionMode.Test) {
            clientOptions.documentSelector = [
                ...(clientOptions.documentSelector || []),
                { scheme: 'vscode-test-web', language: 'kson' }
            ];
        }

        languageClient = new LanguageClient(
            'kson-browser',
            'KSON Language Server (Browser)',
            clientOptions,
            worker
        );

        // Start the client and language server
        await languageClient.start();

        logOutputChannel.info('KSON Browser extension activated successfully');
        console.log('KSON Language Server (Browser) started');

    } catch (error) {
        const message = error instanceof Error ? error.message : String(error);
        logOutputChannel.error(`Failed to activate KSON Browser extension: ${message}`);
        vscode.window.showErrorMessage('Failed to activate KSON language support.');
    }
}

/**
 * Deactivation function for browser environment.
 */
export async function deactivate(): Promise<void> {
    if (languageClient) {
        await languageClient.stop();
        languageClient = undefined;
    }
}