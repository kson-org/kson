import {TextDocument} from 'vscode-languageserver-textdocument';
import {afterEach, beforeEach, describe, it} from 'mocha';
import assert from "assert";
import {ConnectionStub} from "../../ConnectionStub";
import {KsonTextDocumentService} from "../../../core/services/KsonTextDocumentService";
import {KsonDocumentsManager} from "../../../core/document/KsonDocumentsManager";
import {
    DidOpenTextDocumentParams,
    ExecuteCommandParams,
    ApplyWorkspaceEditParams,
    TextEdit,
    ApplyWorkspaceEditResult, Range,
    CompletionList
} from "vscode-languageserver";
import {CommandType, toWireCommandId} from "../../../core/commands/CommandType";
import {FormattingStyle} from "kson";
import {FormattingStyleId, formattingStyleId} from "../../../core/formattingStyle";
import {RemoteWorkspace} from "vscode-languageserver/lib/common/server";
import {createCommandExecutor} from "../../../core/commands/createCommandExecutor.node.js";
import {SchemaProvider} from "../../../core/schema/SchemaProvider";
import {FileSystemSchemaProvider} from "../../../core/schema/FileSystemSchemaProvider";
import {pos} from "../../TestHelpers.js";
import {URI} from "vscode-uri";
import * as fs from "fs";
import * as os from "os";
import * as path from "path";

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

/**
 * Captures the client-facing traffic a schema change produces: the
 * `kson/schemaConfigurationChanged` notification, the diagnostic refresh
 * request, and the window messages the schema commands report with.
 */
class SchemaConnectionStub extends ConnectionStub {
    readonly notifications: string[] = [];
    readonly requests: string[] = [];

    constructor() {
        super();
        this.window = {
            showInformationMessage: () => Promise.resolve(undefined),
            showErrorMessage: () => Promise.resolve(undefined)
        } as any;
    }

    override sendNotification(method: unknown): Promise<void> {
        this.notifications.push(String(method));
        return Promise.resolve();
    }

    override sendRequest<R>(method: unknown): Promise<R> {
        this.requests.push(String(method));
        return Promise.resolve(undefined as R);
    }
}

const TEST_DISTRIBUTION_ID = 'test-ns';

function createTestSetup(
    connection: ConnectionStub = new ConnectionStub(),
    workspaceRoot: string | null = null,
    schemaProvider?: SchemaProvider
) {
    const documentsManager = new KsonDocumentsManager(schemaProvider);
    const service = new KsonTextDocumentService(documentsManager, createCommandExecutor, workspaceRoot, TEST_DISTRIBUTION_ID);
    
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

function buildCommandParams(command: CommandType, uri: string, style: FormattingStyleId): ExecuteCommandParams {
    return {
        command: toWireCommandId(command, TEST_DISTRIBUTION_ID),
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
        const commandParams = buildCommandParams(CommandType.PLAIN_FORMAT, TEST_URI, formattingStyleId(FormattingStyle.PLAIN));
        
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
        const commandParams = buildCommandParams(CommandType.DELIMITED_FORMAT, TEST_URI, formattingStyleId(FormattingStyle.DELIMITED));
        
        await executeAndAssertCommand(content, expected, commandParams);
    });

    it('should execute compact formatting', async () => {
        const content = '{"x" : 1, "y" : 2}';
        const expected = buildWorkspaceEdit(TEST_URI, Range.create(0, 0, 0, 18), 'x:1 y:2');
        const commandParams = buildCommandParams(CommandType.COMPACT_FORMAT, TEST_URI, formattingStyleId(FormattingStyle.COMPACT));
        
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
        const commandParams = buildCommandParams(CommandType.CLASSIC_FORMAT, TEST_URI, formattingStyleId(FormattingStyle.CLASSIC));

        await executeAndAssertCommand(content, expected, commandParams);
    });

});

/**
 * The schema commands write `.kson-schema.kson` themselves, so the server has to
 * pick the change up on its own.
 */
describe('KSON Command Executor schema association', () => {
    const SCHEMA_FILENAME = 'status.schema.kson';
    const SCHEMA_CONTENT = [
        '{',
        '    type: object',
        '    properties: {',
        '        status: {',
        '            type: string',
        '            enum: ["active", "inactive", "pending"]',
        '        }',
        '    }',
        '}'
    ].join('\n');
    const DOCUMENT_CONTENT = '{\n    status: "ac"\n}';
    // Caret between `ac` and the closing quote: still authoring, so enum completions apply.
    const VALUE_POSITION = pos(1, 15);
    const SCHEMA_CHANGED_NOTIFICATION = 'kson/schemaConfigurationChanged';

    let workspaceRoot: string;
    let documentUri: string;
    let connection: SchemaConnectionStub;
    let documentsManager: KsonDocumentsManager;

    beforeEach(() => {
        workspaceRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'kson-test-'));
        fs.writeFileSync(path.join(workspaceRoot, SCHEMA_FILENAME), SCHEMA_CONTENT, 'utf-8');
        documentUri = URI.file(path.join(workspaceRoot, 'settings.kson')).toString();

        connection = new SchemaConnectionStub();
        documentsManager = createTestSetup(
            connection,
            workspaceRoot,
            new FileSystemSchemaProvider(URI.file(workspaceRoot))
        ).documentsManager;

        connection.didOpenHandler(createDidOpenParams(documentUri, DOCUMENT_CONTENT));
    });

    afterEach(() => {
        fs.rmSync(workspaceRoot, {recursive: true, force: true});
    });

    function executeSchemaCommand(command: CommandType, args: object = {}): Promise<any> {
        return connection.executeCommandHandler(
            {
                command: toWireCommandId(command, TEST_DISTRIBUTION_ID),
                arguments: [{documentUri, ...args}]
            },
            {} as any, {} as any, undefined
        );
    }

    async function completionLabels(): Promise<string[] | undefined> {
        // The handler's declared type allows a ResponseError; the completion path never returns one.
        const completions = await connection.requestCompletion(documentUri, VALUE_POSITION) as CompletionList | null;
        return completions?.items.map(item => item.label);
    }

    it('should apply an associated schema', async () => {
        const result = await executeSchemaCommand(CommandType.ASSOCIATE_SCHEMA, {schemaPath: SCHEMA_FILENAME});

        assert.strictEqual(result.success, true, result.message);
        assert.ok(
            documentsManager.get(documentUri)?.getSchemaDocument(),
            'the open document should carry the schema it was just associated with'
        );
        assert.deepStrictEqual(await completionLabels(), ['active', 'inactive', 'pending']);
        assert.deepStrictEqual(connection.notifications, [SCHEMA_CHANGED_NOTIFICATION]);
        assert.deepStrictEqual(connection.requests, ['workspace/diagnostic/refresh']);
    });

    it('should drop the schema when the association is removed', async () => {
        await executeSchemaCommand(CommandType.ASSOCIATE_SCHEMA, {schemaPath: SCHEMA_FILENAME});

        const result = await executeSchemaCommand(CommandType.REMOVE_SCHEMA);

        assert.strictEqual(result.success, true, result.message);
        assert.strictEqual(documentsManager.get(documentUri)?.getSchemaDocument(), undefined);
        assert.strictEqual(await completionLabels(), undefined);
        assert.deepStrictEqual(
            connection.notifications,
            [SCHEMA_CHANGED_NOTIFICATION, SCHEMA_CHANGED_NOTIFICATION]
        );
    });
});
