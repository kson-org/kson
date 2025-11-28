import { CommandType } from './CommandType.js';
import { Command } from 'vscode-languageserver';
import { FormattingStyle } from 'kson';

/**
 * Type-safe mapping of command types to their expected parameter structures
 */
export interface CommandParameters {
    [CommandType.PLAIN_FORMAT ]: {
        documentUri: string;
        formattingStyle: FormattingStyle;
    };
    [CommandType.COMPACT_FORMAT ]: {
        documentUri: string;
        formattingStyle: FormattingStyle;
    };
    [CommandType.DELIMITED_FORMAT]: {
        documentUri: string;
        formattingStyle: FormattingStyle;
    };
    [CommandType.CLASSIC_FORMAT]: {
        documentUri: string;
        formattingStyle: FormattingStyle;
    };
    [CommandType.ASSOCIATE_SCHEMA]: {
        documentUri: string;
        schemaPath: string;
    };
    [CommandType.REMOVE_SCHEMA]: {
        documentUri: string;
    };
}

/**
 * Type guard to check if a string is a valid CommandType
 */
export function isValidCommand(command: string): command is CommandType {
    return Object.values(CommandType).includes(command as CommandType);
}

/**
 * Helper function to create a type-safe command
 */
export function createTypedCommand<T extends CommandType>(
    type: T,
    title: string,
    params: CommandParameters[T]
): Command {
    return {
        title,
        command: type,
        arguments: [params]
    };
}