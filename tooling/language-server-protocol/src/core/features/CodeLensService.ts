import { CodeLens } from 'vscode-languageserver';
import { KsonDocument } from '../document/KsonDocument.js';
import { CommandType } from '../commands/CommandType.js';
import { createTypedCommand } from '../commands/CommandParameters.js';
import { FormattingStyle } from 'kson';
import { formattingStyleId } from '../formattingStyle.js';

/**
 * Service responsible for providing code lenses for Kson documents
 */
export class CodeLensService {
    
    /**
     * Get code lenses for a Kson document.
    */
    getCodeLenses(document: KsonDocument): CodeLens[] {
        const plainId = formattingStyleId(FormattingStyle.PLAIN);
        const formatCommand = createTypedCommand(
            CommandType.PLAIN_FORMAT,
            plainId,
            { documentUri: document.uri, formattingStyle: plainId }
        );

        const delimitedId = formattingStyleId(FormattingStyle.DELIMITED);
        const delimitedFormatCommand = createTypedCommand(
            CommandType.DELIMITED_FORMAT,
            delimitedId,
            { documentUri: document.uri, formattingStyle: delimitedId }
        );

        const compactId = formattingStyleId(FormattingStyle.COMPACT);
        const compactFormatCommand = createTypedCommand(
            CommandType.COMPACT_FORMAT,
            compactId,
            { documentUri: document.uri, formattingStyle: compactId }
        );

        const classicId = formattingStyleId(FormattingStyle.CLASSIC);
        const classicFormatCommand = createTypedCommand(
            CommandType.CLASSIC_FORMAT,
            classicId,
            { documentUri: document.uri, formattingStyle: classicId }
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
            },
            {
                range,
                command: classicFormatCommand
            },
            {
                range,
                command: compactFormatCommand
            }
        ];
    }
}