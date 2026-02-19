import {TextDocument} from 'vscode-languageserver-textdocument';
import {
    Diagnostic,
    DiagnosticSeverity
} from 'vscode-languageserver';
import {KsonDocument} from '../../../core/document/KsonDocument.js';
import {Kson} from 'kson';
import {describe, it} from 'mocha';
import assert from "assert";
import {DiagnosticService} from "../../../core/features/DiagnosticService";
import {RelatedFullDocumentDiagnosticReport} from "vscode-languageserver-protocol/lib/common/protocol.diagnostic";


describe('KSON Diagnostics', () => {
    const diagnosticService = new DiagnosticService();

    function createDoc(content: string, schemaContent?: string): KsonDocument {
        const uri = 'test://test.kson';
        const textDoc = TextDocument.create(uri, 'kson', 0, content);
        const schemaDoc = schemaContent
            ? TextDocument.create('test://schema.kson', 'kson', 0, schemaContent)
            : undefined;
        return new KsonDocument(textDoc, Kson.getInstance().analyze(content), schemaDoc);
    }

    function getDiagnostics(content: string, schemaContent?: string): Diagnostic[] {
        const report = diagnosticService.createDocumentDiagnosticReport(createDoc(content, schemaContent));
        return (report as RelatedFullDocumentDiagnosticReport).items;
    }

    function assertDiagnostic(unformatted: string, expected: Diagnostic[]): void {
        const uri = 'test://test.kson';
        const document = TextDocument.create(uri, 'kson', 0, unformatted);
        const ksonDocument: KsonDocument = new KsonDocument(
            document,
            Kson.getInstance().analyze(unformatted),
        );

        const diagnosticReport = diagnosticService.createDocumentDiagnosticReport(ksonDocument);

        // We simplify diagnosticReport to a list of Diagnostic without message.
        const getItems = (report: RelatedFullDocumentDiagnosticReport): Diagnostic[] => (
            report.items.map(diagnostic =>
                Diagnostic.create(
                    diagnostic.range
                    , diagnostic.message
                    , diagnostic.severity
                    , diagnostic.code
                    , diagnostic.source
                )
            )
        );

        const simplifiedDiagnostics = getItems(diagnosticReport as RelatedFullDocumentDiagnosticReport);

        assert.deepStrictEqual(simplifiedDiagnostics, expected, 'Diagnostic mismatch');
    }

    it('should have a diagnostic error for empty document', () => {
        const content = '';
        const expected = [
                Diagnostic.create(
                    {
                        start: {line: 0, character: 0},
                        end: {line: 0, character: 0}
                    },
                    "Unable to parse a blank file.  A Kson document must describe a value.",
                    DiagnosticSeverity.Error,
                    undefined,
                    "kson"
                ),
            ];

        assertDiagnostic(content, expected);
    });

    // ---- Schema cache correctness ----

    it('should produce identical diagnostics on repeated calls with same schema', () => {
        const schema = `{
            type: object
            properties: {
                age: { type: number }
            }
        }`;
        const first = getDiagnostics('{ age: "not a number" }', schema);
        const second = getDiagnostics('{ age: "not a number" }', schema);

        assert.ok(first.length > 0, 'Should have schema validation errors');
        assert.deepStrictEqual(first, second, 'Repeated calls should produce identical results');
    });

    it('should produce correct diagnostics when schema changes between calls', () => {
        const numberSchema = `{
            type: object
            properties: {
                age: { type: number }
            }
        }`;
        const stringSchema = `{
            type: object
            properties: {
                age: { type: string }
            }
        }`;

        const withNumberSchema = getDiagnostics('{ age: "hello" }', numberSchema);
        assert.ok(withNumberSchema.length > 0, 'String value should fail number schema');

        const withStringSchema = getDiagnostics('{ age: "hello" }', stringSchema);
        assert.strictEqual(withStringSchema.length, 0, 'String value should pass string schema');
    });
});
