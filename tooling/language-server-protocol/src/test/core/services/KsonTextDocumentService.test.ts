import {TextDocument} from "vscode-languageserver-textdocument";
import {
    CompletionList,
    CompletionParams,
    Diagnostic,
    DiagnosticSeverity,
    DidOpenTextDocumentParams,
    DocumentFormattingParams,
    ResponseError,
    SemanticTokensParams,
    TextEdit,
    HoverParams,
    DocumentHighlightParams,
    DocumentSymbolParams,
    CodeLensParams,
    DefinitionParams,
    DocumentDiagnosticParams,
} from "vscode-languageserver";
import assert from "assert";
import {beforeEach, describe, it} from 'mocha';
import {ConnectionStub} from "../../ConnectionStub";
import {KsonDocumentsManager} from "../../../core/document/KsonDocumentsManager.js";
import {KsonTextDocumentService} from "../../../core/services/KsonTextDocumentService.js";
import {FullDocumentDiagnosticReport} from "vscode-languageserver-protocol/lib/common/protocol.diagnostic";
import {createCommandExecutor} from "../../../core/commands/createCommandExecutor.node.js";

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
            const params: DocumentFormattingParams = {
                textDocument: {uri: TEST_URI},
                options: {
                    tabSize: 2,
                    insertSpaces: true
                }
            };
            const result = await connection.formattingHandler(params, {} as any, {} as any, undefined);
            assert.ok(result, "should not get a null or undefined result");

            let textEdits: TextEdit[];
            if (result instanceof ResponseError) {
                assert.fail(`Should not have received a ResponseError. Message: ${result.message}`);
            } else {
                textEdits = result as TextEdit[];
            }

            assert.strictEqual(textEdits.length, 1, "Should be one edit");
            const formattedText = textEdits[0].newText;
            assert.deepStrictEqual(formattedText, expected);
        }

        it('should format a simple KSON string', async () => {
            const content = 'name:"John"';
            const expected = 'name: John';
            await assertFormatting(content, expected);
        });

        it('should format a KSON string with nested objects', async () => {
            const content = 'person:{name:"John",age:30}';
            const expected = [
                'person:',
                '  name: John',
                '  age: 30'
            ].join('\n');
            await assertFormatting(content, expected);
        });

        it('should return empty array when document is not found', async () => {
            const params: DocumentFormattingParams = {
                textDocument: {uri: 'file:///nonexistent.kson'},
                options: { tabSize: 2, insertSpaces: true }
            };
            const result = await connection.formattingHandler(params, {} as any, {} as any, undefined);
            assert.deepStrictEqual(result, []);
        });
    });

    describe('Semantic Tokens', () => {
        it('should provide semantic tokens for a document', async () => {
            const content = 'name: "value"';
            openDocument(content);

            const params: SemanticTokensParams = {
                textDocument: {uri: TEST_URI}
            };

            const result = await connection.semanticTokensHandler(params, {} as any, {} as any, undefined);

            assert.ok(result, "Result should not be null");
            assert.ok((result as any).data.length > 0, "Should have semantic token data");
        });

        it('should return empty data when document is not found', async () => {
            const params: SemanticTokensParams = {
                textDocument: {uri: 'file:///nonexistent.kson'}
            };

            const result = await connection.semanticTokensHandler(params, {} as any, {} as any, undefined);
            assert.deepStrictEqual(result, {data: []});
        });
    });

    describe('Diagnostics', () => {
        it('should provide diagnostics for a document with errors', async () => {
            const content = 'name: "value" extra';
            openDocument(content);

            const params: DocumentDiagnosticParams = {
                textDocument: {uri: TEST_URI}
            };
            const result = await connection.diagnosticsHandler(params, {} as any, {} as any, undefined);

            let resultReport: FullDocumentDiagnosticReport;
            if (result instanceof ResponseError) {
                assert.fail(`Should not have received a ResponseError. Message: ${result.message}`);
            } else {
                resultReport = result as FullDocumentDiagnosticReport;
            }

            assert.ok(resultReport.items.length > 0, "Should have diagnostic items");
            assert.strictEqual(resultReport.items[0].severity, DiagnosticSeverity.Error);
        });

        it('should return empty items when document is not found', async () => {
            const params: DocumentDiagnosticParams = {
                textDocument: {uri: 'file:///nonexistent.kson'}
            };

            const result = await connection.diagnosticsHandler(params, {} as any, {} as any, undefined);

            let resultReport: FullDocumentDiagnosticReport;
            if (result instanceof ResponseError) {
                assert.fail(`Should not have received a ResponseError. Message: ${result.message}`);
            } else {
                resultReport = result as FullDocumentDiagnosticReport;
            }

            assert.strictEqual(resultReport.items.length, 0);
        });

        it('should return no diagnostics for valid document', async () => {
            openDocument('key: "value"');

            const params: DocumentDiagnosticParams = {
                textDocument: {uri: TEST_URI}
            };
            const result = await connection.diagnosticsHandler(params, {} as any, {} as any, undefined);

            const resultReport = result as FullDocumentDiagnosticReport;
            assert.strictEqual(resultReport.items.length, 0);
        });
    });

    describe('Code Lens', () => {
        it('should provide code lenses for a document', async () => {
            openDocument('key: value');

            const params: CodeLensParams = {
                textDocument: {uri: TEST_URI}
            };
            const result = await connection.codeLensHandler(params, {} as any, {} as any, undefined);

            assert.ok(result, "Should return code lenses");
            assert.strictEqual((result as any[]).length, 4, "Should have 4 formatting lenses");
        });

        it('should return empty array when document is not found', async () => {
            const params: CodeLensParams = {
                textDocument: {uri: 'file:///nonexistent.kson'}
            };
            const result = await connection.codeLensHandler(params, {} as any, {} as any, undefined);
            assert.deepStrictEqual(result, []);
        });
    });

    describe('Hover', () => {
        it('should return null when document is not found', async () => {
            const params: HoverParams = {
                textDocument: {uri: 'file:///nonexistent.kson'},
                position: {line: 0, character: 0}
            };
            const result = await connection.hoverHandler(params, {} as any, {} as any, undefined);
            assert.strictEqual(result, null);
        });

        it('should return null when no schema is configured', async () => {
            openDocument('key: value');

            const params: HoverParams = {
                textDocument: {uri: TEST_URI},
                position: {line: 0, character: 0}
            };
            const result = await connection.hoverHandler(params, {} as any, {} as any, undefined);
            assert.strictEqual(result, null);
        });
    });

    describe('Completion', () => {
        it('should return null when document is not found', async () => {
            const params: CompletionParams = {
                textDocument: {uri: 'file:///nonexistent.kson'},
                position: {line: 0, character: 0}
            };
            const result = await connection.completionHandler(params, {} as any, {} as any, undefined);
            assert.strictEqual(result, null);
        });

        it('should return null when no schema is configured', async () => {
            openDocument('key: value');

            const params: CompletionParams = {
                textDocument: {uri: TEST_URI},
                position: {line: 0, character: 0}
            };
            const result = await connection.completionHandler(params, {} as any, {} as any, undefined);
            assert.strictEqual(result, null);
        });
    });

    describe('Definition', () => {
        it('should return null when document is not found', async () => {
            const params: DefinitionParams = {
                textDocument: {uri: 'file:///nonexistent.kson'},
                position: {line: 0, character: 0}
            };
            const result = await connection.onDefinitionHandler(params, {} as any, {} as any, undefined);
            assert.strictEqual(result, null);
        });
    });

    describe('Document Highlight', () => {
        it('should return empty array when document is not found', async () => {
            const params: DocumentHighlightParams = {
                textDocument: {uri: 'file:///nonexistent.kson'},
                position: {line: 0, character: 0}
            };
            const result = await connection.documentHighlightHandler(params, {} as any, {} as any, undefined);
            assert.deepStrictEqual(result, []);
        });

        it('should return highlights for a document', async () => {
            openDocument('{ name: "test", age: 30 }');

            const params: DocumentHighlightParams = {
                textDocument: {uri: TEST_URI},
                position: {line: 0, character: 3}
            };
            const result = await connection.documentHighlightHandler(params, {} as any, {} as any, undefined);
            // Should return highlights without throwing
            assert.ok(Array.isArray(result), "Should return an array");
        });
    });

    describe('Document Symbol', () => {
        it('should return empty array when document is not found', async () => {
            const params: DocumentSymbolParams = {
                textDocument: {uri: 'file:///nonexistent.kson'}
            };
            const result = await connection.documentSymbolHandler(params, {} as any, {} as any, undefined);
            assert.deepStrictEqual(result, []);
        });

        it('should return symbols for a document', async () => {
            openDocument('{ name: "test", age: 30 }');

            const params: DocumentSymbolParams = {
                textDocument: {uri: TEST_URI}
            };
            const result = await connection.documentSymbolHandler(params, {} as any, {} as any, undefined);
            assert.ok(result, "Should return symbols");
            assert.ok((result as any[]).length > 0, "Should have at least one symbol");
        });
    });
});
