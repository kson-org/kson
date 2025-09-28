import {TextDocument} from 'vscode-languageserver-textdocument';
import {beforeEach, describe, it} from 'mocha';
import assert from "assert";
import {ConnectionStub} from "../../ConnectionStub";
import {KsonTextDocumentService} from "../../../core/services/KsonTextDocumentService";
import {KsonDocumentsManager} from "../../../core/document/KsonDocumentsManager";
import {
    DidOpenTextDocumentParams,
    ExecuteCommandParams,
    ApplyWorkspaceEditParams,
    TextEdit,
    ApplyWorkspaceEditResult, Range
} from "vscode-languageserver";
import {CommandType} from "../../../core/commands/CommandType";
import {FormattingStyle} from "kson";
import {RemoteWorkspace} from "vscode-languageserver/lib/common/server";

class WorkspaceConnectionStub extends ConnectionStub {
    private capturedEdits: ApplyWorkspaceEditParams | undefined;
    workspace: RemoteWorkspace;

    constructor() {
        super();
        this.workspace = {
            applyEdit: async (params: ApplyWorkspaceEditParams): Promise<ApplyWorkspaceEditResult> => {
                this.capturedEdits = params;
                return { applied: true };
            }
        } as RemoteWorkspace;
    }

    getCapturedEdits(): ApplyWorkspaceEditParams | undefined {
        return this.capturedEdits;
    }
}

function createTestSetup() {
    const connection = new ConnectionStub();
    const documentsManager = new KsonDocumentsManager();
    const service = new KsonTextDocumentService(documentsManager);
    
    documentsManager.listen(connection);
    service.connect(connection);
    
    return { connection, documentsManager, service };
}

function createDidOpenParams(uri: string, content: string): DidOpenTextDocumentParams {
    const document = TextDocument.create(uri, 'kson', 1, content);
    return {
        textDocument: {
            uri: document.uri,
            languageId: document.languageId,
            version: document.version,
            text: document.getText()
        }
    };
}

// Test data builders
function buildWorkspaceEdit(uri: string, replaceRange: Range, newText: string): ApplyWorkspaceEditParams {
    return {
        edit: {
            changes: {
                [uri]: [
                    TextEdit.replace(
                        replaceRange,
                        newText
                    )
                ]
            }
        }
    };
}

function buildCommandParams(command: CommandType, uri: string, style: FormattingStyle): ExecuteCommandParams {
    return {
        command,
        arguments: [{ documentUri: uri, formattingStyle: style }]
    };
}

describe('KSON Command Executor', () => {
    let connection: ConnectionStub;
    let service: KsonTextDocumentService;
    const TEST_URI = 'test://test.kson';

    beforeEach(() => {
        const setup = createTestSetup();
        connection = setup.connection;
        service = setup.service;
    });

    function openDocument(content: string): TextDocument {
        const params = createDidOpenParams(TEST_URI, content);
        connection.didOpenHandler(params);
        return TextDocument.create(TEST_URI, 'kson', 1, content);
    }

    async function executeAndAssertCommand(
        content: string, 
        expected: ApplyWorkspaceEditParams, 
        commandParams: ExecuteCommandParams
    ): Promise<void> {
        openDocument(content);
        
        const workspaceConnection = new WorkspaceConnectionStub();
        service.connect(workspaceConnection);

        await workspaceConnection.executeCommandHandler(commandParams, {} as any, {} as any, undefined);
        
        const capturedEdits = workspaceConnection.getCapturedEdits();
        assert.deepStrictEqual(
            capturedEdits,
            expected,
            'should have a matching workspace edit'
        );
    }

    it('should execute plain formatting', async () => {
        const content = '{"x" : 1 }';
        const expected = buildWorkspaceEdit(TEST_URI,
            Range.create(0, 0, 0, 10)
            , 'x: 1');
        const commandParams = buildCommandParams(CommandType.PLAIN_FORMAT, TEST_URI, FormattingStyle.PLAIN);
        
        await executeAndAssertCommand(content, expected, commandParams);
    });

    it('should execute delimited formatting', async () => {
        const content = '{"x" : 1 }';
        const expectedContent = [
            '{',
            '  x: 1',
            '}'
        ].join('\n');
        const expected = buildWorkspaceEdit(TEST_URI, Range.create(0, 0, 0, 10), expectedContent);
        const commandParams = buildCommandParams(CommandType.DELIMITED_FORMAT, TEST_URI, FormattingStyle.DELIMITED);
        
        await executeAndAssertCommand(content, expected, commandParams);
    });

    it('should execute compact formatting', async () => {
        const content = '{"x" : 1, "y" : 2}';
        const expected = buildWorkspaceEdit(TEST_URI, Range.create(0, 0, 0, 18), 'x:1 y:2');
        const commandParams = buildCommandParams(CommandType.COMPACT_FORMAT, TEST_URI, FormattingStyle.COMPACT);
        
        await executeAndAssertCommand(content, expected, commandParams);
    });

    it('should execute classic formatting', async () => {
        const content = '{"x" : 1, "y" : 2}';
        const expectedContent = [
            '{',
            '  "x": 1,',
            '  "y": 2',
            '}'
        ].join('\n');
        const expected = buildWorkspaceEdit(TEST_URI, Range.create(0, 0, 0, 18), expectedContent);
        const commandParams = buildCommandParams(CommandType.CLASSIC_FORMAT, TEST_URI, FormattingStyle.CLASSIC);

        await executeAndAssertCommand(content, expected, commandParams);
    });

});
