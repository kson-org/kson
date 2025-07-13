import {TextEdit, FormattingOptions} from 'vscode-languageserver';
import {KsonDocument} from '../document/KsonDocument.js';
import {FormatOptions, Kson} from 'kson';

/**
 * Service responsible for formatting Kson documents
 */
export class FormattingService {

    formatDocument(document: KsonDocument, options: FormattingOptions): TextEdit[] {
        const indentType: FormatOptions = options.insertSpaces
            ? new FormatOptions.Spaces(options.tabSize)
            // @ts-ignore
            : new FormatOptions.Tabs;

        const formattedKson = Kson.getInstance().format(
            document.textDocument.getText(),
            indentType
        );

        return [
            TextEdit.replace(document.getFullDocumentRange(), formattedKson)
        ];
    }
} 
