import {
    Connection,
    ExecuteCommandParams,
    ApplyWorkspaceEditParams
} from 'vscode-languageserver';
import {KsonDocumentsManager} from '../document/KsonDocumentsManager.js';
import {FormattingService} from '../features/FormattingService.js';
import {CommandType} from './CommandType.js';
import {FormatOptions, FormattingStyle} from 'kson';
import type {KsonSettings} from '../KsonSettings.js';
import {KsonDocument} from "../document/KsonDocument";

/**
 * Service responsible for executing commands in the Kson Language Server
 */
export class CommandExecutor {
    constructor(
        private connection: Connection,
        private documentManager: KsonDocumentsManager,
        private formattingService: FormattingService,
        private getConfiguration: () => Required<KsonSettings>
    ) {
    }

    /**
     * Execute a command based on the provided parameters
     */
    async execute(params: ExecuteCommandParams): Promise<any> {
        const {command, arguments: args} = params;

        if (!args || args.length === 0) {
            return;
        }

        const documentUri = args[0] as string;
        const document = this.documentManager.get(documentUri);

        if (!document) {
            this.connection.window.showErrorMessage('Document not found');
            return;
        }

        switch (command) {
            case CommandType.PLAIN_FORMAT:
            case CommandType.DELIMITED_FORMAT:
                const baseFormattingOptions = this.getConfiguration().kson.formatOptions;
                const formattingStyle = command === CommandType.PLAIN_FORMAT ? FormattingStyle.PLAIN : FormattingStyle.DELIMITED;

                return this.executeFormat(documentUri, document, new FormatOptions(
                    baseFormattingOptions.indentType,
                    formattingStyle
                ));

            default:
                this.connection.console.warn(`Unknown command: ${command}`);
                return;
        }
    }

    /**
     * Execute the format command
     */
    private async executeFormat(documentUri: string, document: KsonDocument, formattingOptions: FormatOptions): Promise<void> {
        try {
            const edits = this.formattingService.formatDocument(document, formattingOptions);

            const workspaceEdit: ApplyWorkspaceEditParams = {
                edit: {
                    changes: {
                        [documentUri]: edits
                    }
                }
            };

            await this.connection.workspace.applyEdit(workspaceEdit);
        } catch (error) {
            this.connection.window.showErrorMessage(`Failed to format document: ${error}`);
        }
    }
}