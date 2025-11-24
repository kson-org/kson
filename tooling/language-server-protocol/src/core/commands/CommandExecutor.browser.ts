import {CommandExecutorBase} from './CommandExecutor.base.js';
import {CommandParameters} from './CommandParameters.js';
import {CommandType} from './CommandType.js';

/**
 * Browser implementation of CommandExecutor without file system support
 */
export class CommandExecutor extends CommandExecutorBase {
    /**
     * Execute the associate schema command (not supported in browser)
     */
    protected async executeAssociateSchema(_commandArgs: CommandParameters[CommandType.ASSOCIATE_SCHEMA]): Promise<any> {
        const errorMessage = 'Associate Schema command is not supported in browser environments (requires file system access)';
        this.connection.window.showErrorMessage(errorMessage);

        return {
            success: false,
            message: errorMessage
        };
    }

    /**
     * Execute the remove schema command (not supported in browser)
     */
    protected async executeRemoveSchema(_commandArgs: CommandParameters[CommandType.REMOVE_SCHEMA]): Promise<any> {
        const errorMessage = 'Remove Schema command is not supported in browser environments (requires file system access)';
        this.connection.window.showErrorMessage(errorMessage);

        return {
            success: false,
            message: errorMessage
        };
    }
}