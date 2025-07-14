import {TextEdit} from 'vscode-languageserver';
import {KsonDocument} from '../document/KsonDocument.js';
import {FormatOptions, Kson} from 'kson';

/**
 * Service responsible for formatting Kson documents
 */
export class FormattingService {

    formatDocument(document: KsonDocument, formatOptions: FormatOptions): TextEdit[] {
        const formattedKson = Kson.getInstance().format(document.getText(), formatOptions);

        return [
            TextEdit.replace(document.getFullDocumentRange(), formattedKson)
        ];
    }
} 