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
import {KsonDocument} from "../document/KsonDocument.js";

/**
 * Base class for command execution in the Kson Language Server.
 * Platform-specific implementations extend this to handle environment-specific commands.
 */
export abstract class CommandExecutorBase {
    constructor(
        protected connection: Connection,
        protected documentManager: KsonDocumentsManager,
        protected formattingService: FormattingService,
        protected getConfiguration: () => Required<KsonSettings>,
        protected workspaceRoot: string | null = null
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
            case CommandType.DELIMITED_FORMAT:
            case CommandType.COMPACT_FORMAT:
            case CommandType.CLASSIC_FORMAT: {
                const indentType = this.getConfiguration().kson.formatOptions.indentType;

                return this.executeFormat(commandArgs.documentUri, document, new FormatOptions(
                    indentType,
                    (commandArgs as CommandParameters[CommandType.PLAIN_FORMAT]).formattingStyle,
                ));
            }
            case CommandType.ASSOCIATE_SCHEMA: {
                const schemaArgs = commandArgs as CommandParameters[CommandType.ASSOCIATE_SCHEMA];
                return this.executeAssociateSchema(schemaArgs);
            }
        }
    }

    /**
     * Execute the associate schema command.
     * Platform-specific implementations must provide this method.
     */
    protected abstract executeAssociateSchema(commandArgs: CommandParameters[CommandType.ASSOCIATE_SCHEMA]): Promise<any>;

    /**
     * Execute the format command
     */
    protected async executeFormat(documentUri: string, document: KsonDocument, formattingOptions: FormatOptions): Promise<void> {
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