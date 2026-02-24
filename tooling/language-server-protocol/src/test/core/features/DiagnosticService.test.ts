import {
    Diagnostic,
    DiagnosticSeverity
} from 'vscode-languageserver';
import {describe, it} from 'mocha';
import assert from "assert";
import {DiagnosticService} from "../../../core/features/DiagnosticService";
import {RelatedFullDocumentDiagnosticReport} from "vscode-languageserver-protocol/lib/common/protocol.diagnostic";
import {createKsonDocument} from '../../TestHelpers.js';


describe('KSON Diagnostics', () => {
    const diagnosticService = new DiagnosticService();

    function getDiagnostics(content: string, schemaContent?: string): Diagnostic[] {
        const ksonDocument = createKsonDocument(content, schemaContent);
        const report = diagnosticService.createDocumentDiagnosticReport(ksonDocument);
        return (report as RelatedFullDocumentDiagnosticReport).items;
    }

    function assertDiagnosticCount(content: string, expectedCount: number, schemaContent?: string): Diagnostic[] {
        const diagnostics = getDiagnostics(content, schemaContent);
        assert.strictEqual(diagnostics.length, expectedCount,
            `Expected ${expectedCount} diagnostics, got ${diagnostics.length}: ${diagnostics.map(d => d.message).join('; ')}`);
        return diagnostics;
    }

    describe('parse errors (no schema)', () => {
        it('should report error for empty document', () => {
            const diagnostics = assertDiagnosticCount('', 1);
            assert.strictEqual(diagnostics[0].severity, DiagnosticSeverity.Error);
            assert.strictEqual(diagnostics[0].source, 'kson');
        });

        it('should report no diagnostics for valid document', () => {
            assertDiagnosticCount('key: "value"', 0);
        });

        it('should report no diagnostics for valid object', () => {
            assertDiagnosticCount('{ "name": "test", "age": 30 }', 0);
        });

        it('should report no diagnostics for valid array', () => {
            assertDiagnosticCount('[1, 2, 3]', 0);
        });

        it('should report error for extra tokens after value', () => {
            const diagnostics = assertDiagnosticCount('key: "value" extraValue', 1);
            assert.strictEqual(diagnostics[0].severity, DiagnosticSeverity.Error);
        });

        it('should report error for unclosed curly brace', () => {
            const diagnostics = getDiagnostics('{ "name": "test"');
            assert.ok(diagnostics.length > 0, 'Should have at least one diagnostic for unclosed brace');
            assert.ok(diagnostics.some(d => d.severity === DiagnosticSeverity.Error));
        });

        it('should report both errors and warnings', () => {
            const content = [
                '- {list_item: false false}',
                '    - deceptive_indent_list_item'
            ].join('\n');
            const diagnostics = getDiagnostics(content);
            assert.strictEqual(diagnostics.length, 2);
            assert.strictEqual(diagnostics[0].severity, DiagnosticSeverity.Error);
            assert.strictEqual(diagnostics[1].severity, DiagnosticSeverity.Warning);
        });

        it('should set source to kson on all diagnostics', () => {
            const diagnostics = getDiagnostics('');
            for (const d of diagnostics) {
                assert.strictEqual(d.source, 'kson');
            }
        });

        it('should include range information on diagnostics', () => {
            const diagnostics = getDiagnostics('');
            assert.ok(diagnostics.length > 0);
            const range = diagnostics[0].range;
            assert.ok(range.start !== undefined, 'Range should have start');
            assert.ok(range.end !== undefined, 'Range should have end');
            assert.ok(typeof range.start.line === 'number');
            assert.ok(typeof range.start.character === 'number');
        });
    });

    describe('schema validation', () => {
        it('should report schema validation error for type mismatch', () => {
            const schema = `{
                type: object
                properties: {
                    age: { type: number }
                }
            }`;
            const diagnostics = getDiagnostics('{ age: "not a number" }', schema);
            assert.ok(diagnostics.length > 0, 'Should report type mismatch');
        });

        it('should report schema validation error for missing required property', () => {
            const schema = `{
                type: object
                properties: {
                    name: { type: string }
                }
                required: ["name"]
            }`;
            const diagnostics = getDiagnostics('{ age: 30 }', schema);
            assert.ok(diagnostics.length > 0, 'Should report missing required property');
        });

        it('should report no schema errors for valid document matching schema', () => {
            const schema = `{
                type: object
                properties: {
                    name: { type: string }
                    age: { type: number }
                }
            }`;
            assertDiagnosticCount('{ name: "Alice", age: 30 }', 0, schema);
        });
    });

    describe('null/undefined document', () => {
        it('should return empty report for null document', () => {
            const report = diagnosticService.createDocumentDiagnosticReport(null);
            const items = (report as RelatedFullDocumentDiagnosticReport).items;
            assert.strictEqual(items.length, 0);
        });
    });

    describe('schema parse failure', () => {
        it('should handle invalid schema gracefully and still return parse errors', () => {
            // Invalid schema content that can't be parsed as a valid JSON Schema
            const invalidSchema = '{ this is not valid : : : }}}';
            // Document with a parse error
            const diagnostics = getDiagnostics('key: "value" extra', invalidSchema);
            // Should still return at least the document parse errors, not crash
            assert.ok(diagnostics.length > 0, 'Should return document parse errors even when schema is invalid');
            for (const d of diagnostics) {
                assert.strictEqual(d.source, 'kson');
            }
        });

        it('should return no errors for valid document when schema fails to parse', () => {
            const invalidSchema = '{ broken schema {{{{';
            const diagnostics = getDiagnostics('key: "value"', invalidSchema);
            // Valid document + broken schema: should get 0 diagnostics since
            // document is valid and schema parse failure returns no schema messages
            assert.strictEqual(diagnostics.length, 0);
        });
    });

    describe('document without schema', () => {
        it('should return only parse errors when no schema is configured', () => {
            const diagnostics = getDiagnostics('key: "value" extra');
            assert.ok(diagnostics.length > 0);
            // All diagnostics should come from parse errors only
            for (const d of diagnostics) {
                assert.strictEqual(d.source, 'kson');
            }
        });
    });

    describe('schema cache correctness', () => {
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
});
