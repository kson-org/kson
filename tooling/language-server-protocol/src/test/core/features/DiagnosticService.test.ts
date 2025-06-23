import {TextDocument} from 'vscode-languageserver-textdocument';
import {
    Diagnostic,
    DiagnosticSeverity
} from 'vscode-languageserver';
import {KsonDocument} from '../../../core/document/KsonDocument.js';
import {Kson, MessageType} from 'kson';
import {describe, it} from 'mocha';
import assert from "assert";
import {DiagnosticService} from "../../../core/features/DiagnosticService";
import {RelatedFullDocumentDiagnosticReport} from "vscode-languageserver-protocol/lib/common/protocol.diagnostic";


describe('KSON Diagnostics', () => {
    const diagnosticService = new DiagnosticService();

    function assertDiagnostic(unformatted: string, expected: Diagnostic[]): void {
        const uri = 'test://test.kson';
        const document = TextDocument.create(uri, 'kson', 0, unformatted);
        const ksonDocument: KsonDocument = new KsonDocument(
            document,
            Kson.getInstance().parseToAst(unformatted),
        );

        const diagnosticReport = diagnosticService.createDocumentDiagnosticReport(ksonDocument);

        // We simplify diagnosticReport to a list of Diagnostic without message.
        // that does not contain the message
        const getDiagnostics = (report: RelatedFullDocumentDiagnosticReport): Diagnostic[] => (
            report.items.map(diagnostic =>
                Diagnostic.create(
                    diagnostic.range
                    , ""
                    , diagnostic.severity
                    , diagnostic.code
                    , diagnostic.source
                )
            )
        );

        const simplifiedDiagnostics = getDiagnostics(diagnosticReport as RelatedFullDocumentDiagnosticReport);

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
                    "",
                    DiagnosticSeverity.Error,
                    MessageType.BLANK_SOURCE.name,
                    "kson"
                ),
            ];

        assertDiagnostic(content, expected);
    });
});


