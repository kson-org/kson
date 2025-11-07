import * as vscode from 'vscode';
import * as path from 'path';
import {deactivate} from '../common/deactivate';
import {
    ServerOptions,
    TransportKind,
    LanguageClientOptions,
    LanguageClient
} from 'vscode-languageclient/node';
import {
    createClientOptions
} from '../../config/clientOptions';
import {StatusBarManager} from '../common/StatusBarManager';

/**
 * Node.js-specific activation function for the KSON extension.
 * This handles desktop VS Code environments where we can spawn child processes.
 */
export async function activate(context: vscode.ExtensionContext) {
    // Create log output channel
    const logOutputChannel = vscode.window.createOutputChannel('Kson Language Server', {log: true});
    context.subscriptions.push(logOutputChannel);

    try {
        const serverModule = path.join(__dirname, 'server.js');

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
        let languageClient = new LanguageClient("kson", serverOptions, clientOptions, false)

        await languageClient.start();
        console.log('Kson Language Server started');

        // Create status bar manager
        const statusBarManager = new StatusBarManager(languageClient);
        context.subscriptions.push(statusBarManager);

        // Listen for schema configuration changes from the server
        context.subscriptions.push(
            languageClient.onNotification('kson/schemaConfigurationChanged', async () => {
                // Refresh status bar for current editor when schema config changes
                const editor = vscode.window.activeTextEditor;
                if (editor && editor.document.languageId === 'kson') {
                    await statusBarManager.updateForDocument(editor.document);
                }
            })
        );

        // Register the schema selection command
        context.subscriptions.push(
            vscode.commands.registerCommand('kson.selectSchema', async () => {
                const editor = vscode.window.activeTextEditor;
                if (!editor || editor.document.languageId !== 'kson') {
                    vscode.window.showWarningMessage('Please open a KSON file first.');
                    return;
                }

                // Show QuickPick with options
                const options = [
                    { label: '$(file) Select schema file...', description: 'Choose a schema file from your workspace', action: 'file' },
                    { label: '$(close) Remove schema association', description: 'Unassociate any schema from this file', action: 'remove' }
                ];

                const selection = await vscode.window.showQuickPick(options, {
                    placeHolder: 'Select how to associate a schema'
                });

                if (!selection) {
                    return;
                }

                let schemaPath: string | undefined;

                if (selection.action === 'file') {
                    // Find KSON files in workspace
                    const files = await vscode.workspace.findFiles('**/*.[k|j]son', '**/node_modules/**');
                    if (files.length === 0) {
                        vscode.window.showInformationMessage('No .kson or .json files found in workspace.');
                        return;
                    }

                    const fileItems = files.map(f => ({
                        label: path.basename(f.fsPath),
                        description: vscode.workspace.asRelativePath(f),
                        filePath: vscode.workspace.asRelativePath(f)
                    }));

                    const schemaFile = await vscode.window.showQuickPick(fileItems, {
                        placeHolder: 'Select a schema file'
                    });

                    if (schemaFile) {
                        schemaPath = schemaFile.filePath;
                    }
                } else if (selection.action === 'remove') {
                    vscode.window.showInformationMessage('Schema removal not yet implemented. You can manually edit .kson-schema.kson');
                    return;
                }

                if (schemaPath) {
                    try {
                        // Execute the associate schema command via LSP
                        await languageClient.sendRequest('workspace/executeCommand', {
                            command: 'kson.associateSchema',
                            arguments: [{
                                documentUri: editor.document.uri.toString(),
                                schemaPath: schemaPath
                            }]
                        });

                        // Refresh status bar
                        await statusBarManager.updateForDocument(editor.document);
                    } catch (error) {
                        vscode.window.showErrorMessage(`Failed to associate schema: ${error}`);
                    }
                }
            })
        );

        // Update status bar when active editor changes
        context.subscriptions.push(
            vscode.window.onDidChangeActiveTextEditor(async editor => {
                if (editor && editor.document.languageId === 'kson') {
                    await statusBarManager.updateForDocument(editor.document);
                } else {
                    statusBarManager.hide();
                }
            })
        );

        // Update status bar when document is saved (schema config might have changed)
        context.subscriptions.push(
            vscode.workspace.onDidSaveTextDocument(async doc => {
                if (doc.fileName.endsWith('.kson-schema.kson')) {
                    // Schema config changed, refresh status bar for current editor
                    const editor = vscode.window.activeTextEditor;
                    if (editor && editor.document.languageId === 'kson') {
                        await statusBarManager.updateForDocument(editor.document);
                    }
                }
            })
        );

        // Initialize status bar for currently active editor
        if (vscode.window.activeTextEditor?.document.languageId === 'kson') {
            await statusBarManager.updateForDocument(vscode.window.activeTextEditor.document);
        }

        logOutputChannel.info('KSON Node.js extension activated successfully');
    } catch (error) {
        const message = error instanceof Error ? error.message : String(error);
        logOutputChannel.error(`Failed to activate KSON Node.js extension: ${message}`);
        vscode.window.showErrorMessage('Failed to activate KSON language support.');
    }
}

deactivate().catch(error => {
    console.error('Deactivation failed:', error);
});