import { CodeLens, Command } from 'vscode-languageserver';
import { KsonDocument } from '../document/KsonDocument.js';
import { CommandType } from '../commands/CommandType.js';

/**
 * Service responsible for providing code lenses for Kson documents
 */
export class CodeLensService {
    
    /**
     * Get code lenses for a Kson document.
     * Returns two code lenses at the top of the file:
     * - "format" to format the document as Kson
     * - "to Json" to convert the document to JSON
     */
    getCodeLenses(document: KsonDocument): CodeLens[] {
        const formatCommand: Command = {
            title: 'plain',
            command: CommandType.PLAIN_FORMAT,
            arguments: [document.uri]
        };

        const delimitedFormatCommand: Command = {
            title: 'delimited',
            command: CommandType.DELIMITED_FORMAT,
            arguments: [document.uri]
        };

        // Place both lenses at the start of the document
        const range = {
            start: { line: 0, character: 0 },
            end: { line: 0, character: 0 }
        };

        return [
            {
                range,
                command: formatCommand
            },
            {
                range,
                command: delimitedFormatCommand
            }
        ];
    }
}