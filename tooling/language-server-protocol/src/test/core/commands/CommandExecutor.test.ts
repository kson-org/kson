import {TextDocument} from 'vscode-languageserver-textdocument';
import {beforeEach, describe, it} from 'mocha';
import assert from "assert";
import {ConnectionStub} from "../../ConnectionStub";
import {KsonTextDocumentService} from "../../../core/services/KsonTextDocumentService";
import {KsonDocumentsManager} from "../../../core/document/KsonDocumentsManager";
import {DidOpenTextDocumentParams, ExecuteCommandParams} from "vscode-languageserver";
import {CommandType} from "../../../core/commands/CommandType";
import {FormattingStyle} from "kson";

describe('KSON Command Executor', () => {
    let connection: ConnectionStub;
    let service: KsonTextDocumentService;
    let documentsManager: KsonDocumentsManager;
    const TEST_URI = 'test://test.kson';

    beforeEach(() => {
        connection = new ConnectionStub();
        documentsManager = new KsonDocumentsManager();
        service = new KsonTextDocumentService(documentsManager);

        // Set the documents manager in the connection stub so it can apply edits
        connection.setDocumentsManager(documentsManager);

        documentsManager.listen(connection);
        service.connect(connection)
    });

    function openDocument(content: string) {
        const document = TextDocument.create(TEST_URI, 'kson', 1, content);
        const params: DidOpenTextDocumentParams = {
            textDocument: {
                uri: document.uri,
                languageId: document.languageId,
                version: document.version,
                text: document.getText()
            }
        };
        connection.didOpenHandler(params);
        return document;
    }

    async function executeAndAssertCommand(unformatted: string, expected: string, commandParameters: ExecuteCommandParams): Promise<void> {
        openDocument(unformatted);

        await connection.executeCommandHandler(commandParameters, {} as any, {} as any, undefined)
        assert.strictEqual(documentsManager.get(TEST_URI)?.getText(), expected, 'should have a matching formatted document');
    }

    it('should execute plain formatting', async () => {
        const content = '{"x" : 1 }';
        const expected = [
            'x: 1'
        ].join('\n');
        await executeAndAssertCommand(content, expected, {
            command: CommandType.PLAIN_FORMAT,
            arguments: [{ documentUri: TEST_URI, formattingStyle: FormattingStyle.PLAIN }]
        });
    });

    it('should execute delimited formatting', async () => {
        const content = '{"x" : 1 }';
        const expected = [
            '{',
            '  x: 1',
            '}'
        ].join('\n');
        await executeAndAssertCommand(content, expected, {
            command: CommandType.DELIMITED_FORMAT,
            arguments: [{ documentUri: TEST_URI, formattingStyle: FormattingStyle.DELIMITED  }]
        });
    });

});
