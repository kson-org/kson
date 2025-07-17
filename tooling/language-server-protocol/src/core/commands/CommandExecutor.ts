import {
    Connection,
    ExecuteCommandParams,
    ApplyWorkspaceEditParams
} from 'vscode-languageserver';
import {KsonDocumentsManager} from '../document/KsonDocumentsManager.js';
import {FormattingService} from '../features/FormattingService.js';
import {CommandType} from './CommandType.js';
import {CommandParameters, isValidCommand} from './CommandParameters.js';
import {FormatOptions} from 'kson';
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

        if (!isValidCommand(command)) {
            this.connection.console.warn(`Unknown command: ${command}`);
            return;
        }

        /**
         * To provide type safety the length of the arguments should always be a list of length 1. This single element
         * is then parsed to {@link CommandParameters}. For a new command you are expected to create a new
         * {@link CommandParameters} with the arguments that are expected.
         */
        if (!args || args.length === 0) {
            this.connection.console.warn(`No arguments provided for command: ${command}`);
            return;
        }
        const commandArgs = args[0] as CommandParameters[typeof command];

        const document = this.documentManager.get(commandArgs.documentUri);
        if (!document) {
            this.connection.window.showErrorMessage('Document not found');
            return;
        }

        switch (command) {
            case CommandType.PLAIN_FORMAT:
            case CommandType.DELIMITED_FORMAT: {
                const indentType = this.getConfiguration().kson.formatOptions.indentType;

                return this.executeFormat(commandArgs.documentUri, document, new FormatOptions(
                    indentType,
                    commandArgs.formattingStyle,
                ));
            }
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