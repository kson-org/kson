import {TextEdit, FormattingOptions} from 'vscode-languageserver';
import {KsonDocument} from '../document/KsonDocument.js';
import {KsonCore, IndentType, CompileTarget, KsonFormatterConfig} from 'kson';

/**
 * Service responsible for formatting Kson documents
 */
export class FormattingService {

    formatDocument(document: KsonDocument, options: FormattingOptions): TextEdit[] {
        const indentType = options.insertSpaces
            ? new IndentType.Space(options.tabSize)
            : new IndentType.Tab();
        const formatterConfig = new KsonFormatterConfig(indentType);
        const compileConfig = new CompileTarget.Kson(
            true,
            formatterConfig
        );

        const parseResult = KsonCore.getInstance().parseToKson(
            document.textDocument.getText(),
            compileConfig
        )

        if (!parseResult.kson) {
            return [];
        }

        return [
            TextEdit.replace(document.getFullDocumentRange(), parseResult.kson)
        ];
    }
} 
