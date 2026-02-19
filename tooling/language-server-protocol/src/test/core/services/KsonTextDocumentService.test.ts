import {TextDocument} from "vscode-languageserver-textdocument";
import {
    DiagnosticSeverity,
    DidOpenTextDocumentParams,
    TextEdit,
} from "vscode-languageserver";
import assert from "assert";
import {beforeEach, describe, it} from 'mocha';
import {ConnectionStub} from "../../ConnectionStub";
import {KsonDocumentsManager} from "../../../core/document/KsonDocumentsManager.js";
import {KsonTextDocumentService} from "../../../core/services/KsonTextDocumentService.js";
import {FullDocumentDiagnosticReport} from "vscode-languageserver-protocol/lib/common/protocol.diagnostic";
import {createCommandExecutor} from "../../../core/commands/createCommandExecutor.node.js";
import {ksonSettingsWithDefaults} from "../../../core/KsonSettings.js";
import {pos} from "../../TestHelpers";

describe('KsonTextDocumentService', () => {
    let connection: ConnectionStub;
    let service: KsonTextDocumentService;
    let documentsManager: KsonDocumentsManager;
    const TEST_URI = 'test://test.kson';

    beforeEach(() => {
        connection = new ConnectionStub();
        documentsManager = new KsonDocumentsManager();
        service = new KsonTextDocumentService(documentsManager, createCommandExecutor);

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

    describe('Formatting', () => {
        async function assertFormatting(content: string, expected: string) {
            openDocument(content);
            const result = await connection.requestFormatting(TEST_URI);
            assert.ok(result, "should not get a null or undefined result");

            const textEdits = result as TextEdit[];
            assert.strictEqual(textEdits.length, 1, "Should be one edit");
            assert.deepStrictEqual(textEdits[0].newText, expected);
        }

        it('should format a simple KSON string', async () => {
            await assertFormatting('name:"John"', 'name: John');
        });

        it('should format a KSON string with nested objects', async () => {
            const expected = [
                'person:',
                '  name: John',
                '  age: 30'
            ].join('\n');
            await assertFormatting('person:{name:"John",age:30}', expected);
        });

        it('should return empty array when document is not found', async () => {
            const result = await connection.requestFormatting('file:///nonexistent.kson');
            assert.deepStrictEqual(result, []);
        });
    });

    describe('Semantic Tokens', () => {
        it('should provide semantic tokens for a document', async () => {
            openDocument('name: "value"');
            const result = await connection.requestSemanticTokens(TEST_URI);

            assert.ok(result, "Result should not be null");
            assert.strictEqual((result as any).data.length, 25, "Should have 5 tokens at 5 ints each (key, operator, open quote, string content, close quote)");
        });

        it('should return empty data when document is not found', async () => {
            const result = await connection.requestSemanticTokens('file:///nonexistent.kson');
            assert.deepStrictEqual(result, {data: []});
        });
    });

    describe('Diagnostics', () => {
        it('should provide diagnostics for a document with errors', async () => {
            openDocument('name: "value" extra');
            const result = await connection.requestDiagnostics(TEST_URI) as FullDocumentDiagnosticReport;

            assert.strictEqual(result.items.length, 1, "Should have exactly 1 diagnostic for trailing token");
            assert.strictEqual(result.items[0].severity, DiagnosticSeverity.Error);
            assert.strictEqual(result.items[0].source, 'kson');
        });

        it('should return empty items when document is not found', async () => {
            const result = await connection.requestDiagnostics('file:///nonexistent.kson') as FullDocumentDiagnosticReport;
            assert.strictEqual(result.items.length, 0);
        });

        it('should return no diagnostics for valid document', async () => {
            openDocument('key: "value"');
            const result = await connection.requestDiagnostics(TEST_URI) as FullDocumentDiagnosticReport;
            assert.strictEqual(result.items.length, 0);
        });
    });

    describe('Code Lens', () => {
        it('should provide code lenses for a document', async () => {
            openDocument('key: value');
            const result = await connection.requestCodeLens(TEST_URI);

            assert.ok(result, "Should return code lenses");
            assert.strictEqual((result as any[]).length, 4, "Should have 4 formatting lenses");
        });

        it('should return empty array when document is not found', async () => {
            const result = await connection.requestCodeLens('file:///nonexistent.kson');
            assert.deepStrictEqual(result, []);
        });

        it('should return empty array when codeLens is disabled', async () => {
            openDocument('key: value');

            const config = ksonSettingsWithDefaults({kson: {codeLens: {enable: false}}});
            service.updateConfiguration(config);

            const result = await connection.requestCodeLens(TEST_URI);
            assert.deepStrictEqual(result, []);
        });
    });

    describe('Hover', () => {
        it('should return null when document is not found', async () => {
            const result = await connection.requestHover('file:///nonexistent.kson', pos(0, 0));
            assert.strictEqual(result, null);
        });

        it('should return null when no schema is configured', async () => {
            openDocument('key: value');
            const result = await connection.requestHover(TEST_URI, pos(0, 0));
            assert.strictEqual(result, null);
        });
    });

    describe('Completion', () => {
        it('should return null when document is not found', async () => {
            const result = await connection.requestCompletion('file:///nonexistent.kson', pos(0, 0));
            assert.strictEqual(result, null);
        });

        it('should return null when no schema is configured', async () => {
            openDocument('key: value');
            const result = await connection.requestCompletion(TEST_URI, pos(0, 0));
            assert.strictEqual(result, null);
        });
    });

    describe('Definition', () => {
        it('should return null when document is not found', async () => {
            const result = await connection.requestDefinition('file:///nonexistent.kson', pos(0, 0));
            assert.strictEqual(result, null);
        });
    });

    describe('Document Highlight', () => {
        it('should return empty array when document is not found', async () => {
            const result = await connection.requestDocumentHighlight('file:///nonexistent.kson', pos(0, 0));
            assert.deepStrictEqual(result, []);
        });

        it('should return highlights for a document', async () => {
            openDocument('{ name: "test", age: 30 }');
            const result = await connection.requestDocumentHighlight(TEST_URI, pos(0, 3));
            assert.ok(Array.isArray(result), "Should return an array");
            assert.strictEqual(result.length, 2, "Should highlight both sibling keys (name and age)");
        });
    });

    describe('Document Symbol', () => {
        it('should return empty array when document is not found', async () => {
            const result = await connection.requestDocumentSymbol('file:///nonexistent.kson');
            assert.deepStrictEqual(result, []);
        });

        it('should return symbols for a document', async () => {
            openDocument('{ name: "test", age: 30 }');
            const result = await connection.requestDocumentSymbol(TEST_URI) as any[];
            assert.ok(result, "Should return symbols");
            assert.strictEqual(result.length, 1, "Should have one root object symbol");
            assert.strictEqual(result[0].children.length, 2, "Root object should have name and age children");
        });
    });

    describe('Folding Ranges', () => {
        it('should return empty array when document is not found', async () => {
            const result = await connection.requestFoldingRanges('file:///nonexistent.kson');
            assert.deepStrictEqual(result, []);
        });

        it('should return folding ranges for multi-line object', async () => {
            openDocument('{\n  name: "test"\n  age: 30\n}');
            const result = await connection.requestFoldingRanges(TEST_URI) as any[];
            assert.strictEqual(result.length, 1, "Should have one folding range for the object");
            assert.strictEqual(result[0].startLine, 0);
            assert.strictEqual(result[0].endLine, 3);
        });
    });

    describe('Error Handling', () => {
        // Monkey-patch a service to throw, verify the handler catches and returns a safe default.
        function breakService(serviceName: string) {
            const svc = (service as any)[serviceName];
            for (const key of Object.getOwnPropertyNames(Object.getPrototypeOf(svc))) {
                if (typeof svc[key] === 'function' && key !== 'constructor') {
                    svc[key] = () => { throw new Error('test error'); };
                }
            }
        }

        it('should return {data: []} when semanticTokensService throws', async () => {
            openDocument('name: "value"');
            breakService('semanticTokensService');
            const result = await connection.requestSemanticTokens(TEST_URI);
            assert.deepStrictEqual(result, {data: []});
        });

        it('should return [] when formattingService throws', async () => {
            openDocument('name: "value"');
            breakService('formattingService');
            const result = await connection.requestFormatting(TEST_URI);
            assert.deepStrictEqual(result, []);
        });

        it('should return empty diagnostic report when diagnosticService throws', async () => {
            openDocument('name: "value"');
            breakService('diagnosticService');
            const result = await connection.requestDiagnostics(TEST_URI) as FullDocumentDiagnosticReport;
            assert.strictEqual(result.kind, 'full');
            assert.strictEqual(result.items.length, 0);
        });

        it('should return [] when codeLensService throws', async () => {
            openDocument('name: "value"');
            breakService('codeLensService');
            const result = await connection.requestCodeLens(TEST_URI);
            assert.deepStrictEqual(result, []);
        });

        it('should return [] when documentHighlightService throws', async () => {
            openDocument('{ name: "test" }');
            breakService('documentHighlightService');
            const result = await connection.requestDocumentHighlight(TEST_URI, pos(0, 3));
            assert.deepStrictEqual(result, []);
        });

        it('should return [] when documentSymbolService throws', async () => {
            openDocument('{ name: "test" }');
            breakService('documentSymbolService');
            const result = await connection.requestDocumentSymbol(TEST_URI);
            assert.deepStrictEqual(result, []);
        });

        it('should return null when hoverService throws', async () => {
            openDocument('name: "value"');
            breakService('hoverService');
            const result = await connection.requestHover(TEST_URI, pos(0, 0));
            assert.strictEqual(result, null);
        });

        it('should return null when completionService throws', async () => {
            openDocument('name: "value"');
            breakService('completionService');
            const result = await connection.requestCompletion(TEST_URI, pos(0, 0));
            assert.strictEqual(result, null);
        });

        it('should return null when definitionService throws', async () => {
            openDocument('name: "value"');
            breakService('definitionService');
            const result = await connection.requestDefinition(TEST_URI, pos(0, 0));
            assert.strictEqual(result, null);
        });

        it('should return [] when foldingRangeService throws', async () => {
            openDocument('{\n  name: "test"\n}');
            breakService('foldingRangeService');
            const result = await connection.requestFoldingRanges(TEST_URI);
            assert.deepStrictEqual(result, []);
        });

    });
});
