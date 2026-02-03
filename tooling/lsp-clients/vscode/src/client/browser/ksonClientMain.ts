import * as vscode from 'vscode';
import { LanguageClient } from 'vscode-languageclient/browser';
import { createClientOptions } from '../../config/clientOptions';
import { initializeLanguageConfig } from '../../config/languageConfig';
import { loadBundledSchemas, areBundledSchemasEnabled } from '../../config/bundledSchemaLoader';
import {deactivate} from '../common/deactivate';

/**
 * Browser-specific activation function for the KSON extension.
 * This handles VS Code Web and GitHub.dev environments where we run in a browser.
 */
export async function activate(context: vscode.ExtensionContext) {
    // Initialize language configuration from package.json
    initializeLanguageConfig(context.extension.packageJSON);

    // Create log output channel
    const logOutputChannel = vscode.window.createOutputChannel('Kson Language Server', { log: true });
    context.subscriptions.push(logOutputChannel);

    try {
        // Create a web worker that runs the server
        const serverModule = vscode.Uri.joinPath(context.extensionUri, 'dist', 'browserServer.js');
        const worker = new Worker(serverModule.toString(true));

        // Load bundled schemas (works in browser via vscode.workspace.fs)
        const bundledSchemas = await loadBundledSchemas(context.extensionUri, {
            info: (msg) => logOutputChannel.info(msg),
            warn: (msg) => logOutputChannel.warn(msg),
            error: (msg) => logOutputChannel.error(msg)
        });

        // Create the language client options with bundled schemas
        const clientOptions = createClientOptions(logOutputChannel, {
            bundledSchemas,
            enableBundledSchemas: areBundledSchemasEnabled()
        });

        // In test environments, we need to support the vscode-test-web scheme
        // This is only needed for the test runner, not in production
        if (context.extensionMode === vscode.ExtensionMode.Test) {
            // Add test scheme for all configured language IDs
            const testSelectors = (clientOptions.documentSelector || []).map((selector: any) => ({
                ...selector,
                scheme: 'vscode-test-web'
            }));
            clientOptions.documentSelector = [
                ...(clientOptions.documentSelector || []),
                ...testSelectors
            ];
        }

        let languageClient = new LanguageClient(
            'kson-browser',
            'KSON Language Server (Browser)',
            clientOptions,
            worker
        );

        // Start the client and language server
        await languageClient.start();

        logOutputChannel.info('KSON Browser extension activated successfully');
        if (bundledSchemas.length > 0) {
            logOutputChannel.info(`Loaded ${bundledSchemas.length} bundled schemas for browser environment`);
        } else {
            logOutputChannel.info('Note: No bundled schemas configured. User-defined schemas require file system access.');
        }
        console.log('KSON Language Server (Browser) started');

    } catch (error) {
        const message = error instanceof Error ? error.message : String(error);
        logOutputChannel.error(`Failed to activate KSON Browser extension: ${message}`);
        vscode.window.showErrorMessage('Failed to activate KSON language support.');
    }
}

deactivate().catch(error => {
    console.error('Deactivation failed:', error);
});