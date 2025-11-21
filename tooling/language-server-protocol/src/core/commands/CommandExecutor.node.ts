import {CommandExecutorBase} from './CommandExecutor.base.js';
import {CommandParameters} from './CommandParameters.js';
import {CommandType} from './CommandType.js';
import {AssociateSchemaCommand} from './AssociateSchemaCommand.js';

/**
 * Node.js implementation of CommandExecutor with file system support
 */
export class CommandExecutor extends CommandExecutorBase {
    /**
     * Execute the associate schema command (Node.js only - requires file system access)
     */
    protected async executeAssociateSchema(commandArgs: CommandParameters[CommandType.ASSOCIATE_SCHEMA]): Promise<any> {
        const result = AssociateSchemaCommand.execute({
            documentUri: commandArgs.documentUri,
            schemaPath: commandArgs.schemaPath,
            workspaceRoot: this.workspaceRoot
        });

        if (result.success) {
            this.connection.window.showInformationMessage(result.message);
        } else {
            this.connection.window.showErrorMessage(result.message);
        }

        return result;
    }
}