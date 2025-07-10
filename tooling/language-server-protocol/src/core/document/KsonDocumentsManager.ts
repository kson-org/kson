import {TextDocument} from 'vscode-languageserver-textdocument';
import {KsonCore} from 'kson';
import {KsonDocument} from "./KsonDocument.js";
import {DocumentUri, TextDocuments, TextDocumentContentChangeEvent} from "vscode-languageserver";

/**
 * Document management for the Kson Language Server.
 * The {@link KsonDocumentsManager} keeps track of all {@link KsonDocument}'s that
 * we are watching. It extends {@link TextDocuments} to handle {@link KsonDocument}
 * instances.
 */
export class KsonDocumentsManager extends TextDocuments<KsonDocument> {
    constructor() {
        super({
            create: (
                uri: DocumentUri,
                languageId: string,
                version: number,
                content: string
            ): KsonDocument => {
                const textDocument = TextDocument.create(uri, languageId, version, content);
                const parseResult = KsonCore.getInstance().parseToAst(content);
                return new KsonDocument(textDocument, parseResult);
            },
            update: (
                ksonDocument: KsonDocument,
                changes: TextDocumentContentChangeEvent[],
                version: number,
            ): KsonDocument => {
                const textDocument = TextDocument.update(
                    ksonDocument.textDocument,
                    changes,
                    version
                );
                const parseResult = KsonCore.getInstance().parseToAst(textDocument.getText());
                return new KsonDocument(
                    textDocument,
                    parseResult
                );
            }
        });
    }
}
