import { CodeLens } from 'vscode-languageserver';
import { KsonDocument } from '../document/KsonDocument.js';
import { CommandType } from '../commands/CommandType.js';
import { createTypedCommand } from '../commands/CommandParameters.js';
import { FormattingStyle } from 'kson';

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
        const formatCommand = createTypedCommand(
            CommandType.PLAIN_FORMAT,
            'plain',
            { documentUri: document.uri, formattingStyle: FormattingStyle.PLAIN}
        );

        const delimitedFormatCommand = createTypedCommand(
            CommandType.DELIMITED_FORMAT,
            'delimited',
            { documentUri: document.uri, formattingStyle: FormattingStyle.DELIMITED }
        );

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