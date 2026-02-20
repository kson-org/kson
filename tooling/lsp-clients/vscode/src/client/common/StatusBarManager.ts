import * as vscode from 'vscode';
import {BaseLanguageClient} from 'vscode-languageclient';
import * as path from 'path';
import { isKsonLanguage } from '../../config/languageConfig';

/**
 * Response from the LSP server for schema information
 */
interface SchemaInfo {
    schemaUri?: string;
    schemaPath?: string;
    hasSchema: boolean;
}

/**
 * Manages the status bar item that displays schema information for KSON documents.
 * The status bar shows the current schema and allows users to click to associate/change schemas.
 */
export class StatusBarManager {
    private statusBarItem: vscode.StatusBarItem;
    private currentDocument: vscode.TextDocument | undefined;

    constructor(private client: BaseLanguageClient) {
        // Create status bar item on the right side
        this.statusBarItem = vscode.window.createStatusBarItem(
            vscode.StatusBarAlignment.Right,
            100
        );
        this.statusBarItem.command = 'kson.selectSchema';
    }

    /**
     * Update the status bar for the given document.
     * Queries the LSP server for schema information and updates the display.
     */
    async updateForDocument(document: vscode.TextDocument): Promise<void> {
        if (!isKsonLanguage(document.languageId)) {
            this.hide();
            return;
        }

        this.currentDocument = document;

        try {
            const schemaInfo = await this.client.sendRequest<SchemaInfo>(
                'kson/getDocumentSchema',
                {uri: document.uri.toString()}
            );

            if (schemaInfo.hasSchema && schemaInfo.schemaPath) {
                const isBundled = schemaInfo.schemaUri?.startsWith('bundled://') ?? false;

                // Extract just the filename for display
                const schemaFileName = isBundled
                    ? this.extractBundledSchemaName(schemaInfo.schemaPath)
                    : path.basename(schemaInfo.schemaPath);

                // Show bundled indicator
                const bundledSuffix = isBundled ? ' (bundled)' : '';
                const icon = isBundled ? '$(package)' : '$(file-code)';

                this.statusBarItem.text = `${icon} Schema: ${schemaFileName}${bundledSuffix}`;
                this.statusBarItem.tooltip = isBundled
                    ? `Bundled schema for this language\nClick to override with custom schema`
                    : `Schema: ${schemaInfo.schemaPath}\nClick to change schema`;
            } else {
                this.statusBarItem.text = '$(warning) No Schema';
                this.statusBarItem.tooltip = 'No schema associated. Click to select a schema';
            }

            this.show();
        } catch (error) {
            console.error('Failed to get schema info:', error);
            this.statusBarItem.text = '$(warning) No Schema';
            this.statusBarItem.tooltip = 'Click to select a schema';
            this.show();
        }
    }

    /**
     * Show the status bar item
     */
    show(): void {
        this.statusBarItem.show();
    }

    /**
     * Hide the status bar item
     */
    hide(): void {
        this.statusBarItem.hide();
    }

    /**
     * Extract a display name from a bundled schema URI.
     * Bundled schema URIs have the format: bundled://schema/{fileExtension}.schema.kson
     */
    private extractBundledSchemaName(schemaPath: string): string {
        if (schemaPath.startsWith('bundled://schema/')) {
            return schemaPath
                .replace('bundled://schema/', '')
                .replace(/\.schema\.kson$/, '');
        }
        return path.basename(schemaPath);
    }

    /**
     * Clean up resources
     */
    dispose(): void {
        this.statusBarItem.dispose();
    }
}
