import {TextEdit, FormattingOptions} from 'vscode-languageserver';
import {KsonDocument} from '../document/KsonDocument.js';
import {FormatOptions, IndentType, Kson} from 'kson';

/**
 * Service responsible for formatting Kson documents
 */
export class FormattingService {

    formatDocument(document: KsonDocument, options: FormattingOptions): TextEdit[] {
        const indentType: FormatOptions = options.insertSpaces
            ? new FormatOptions(new IndentType.Spaces(options.tabSize))
            : new FormatOptions(IndentType.Tabs);

        const formattedKson = Kson.getInstance().format(
            document.textDocument.getText(),
            indentType
        );

        return [
            TextEdit.replace(document.getFullDocumentRange(), formattedKson)
        ];
    }
} 
