import {
    Diagnostic,
    DiagnosticSeverity,
    DocumentDiagnosticReport,
    RelatedFullDocumentDiagnosticReport
} from 'vscode-languageserver';
import {KsonDocument} from '../document/KsonDocument';
import {isKsonSchemaDocument} from '../document/KsonSchemaDocument';
import {KsonTooling, DiagnosticMessage, DiagnosticSeverity as KtSeverity} from 'kson-tooling';

/**
 * Service responsible for providing diagnostic information for Kson documents.
 * Delegates to Kotlin's KsonTooling for validation.
 */
export class DiagnosticService {

    createDocumentDiagnosticReport(document: KsonDocument | null | undefined): DocumentDiagnosticReport {
        const diagnostics = document ? this.getDiagnostics(document) : [];
        return {
            kind: 'full',
            items: diagnostics
        } as RelatedFullDocumentDiagnosticReport;
    }

    private getDiagnostics(document: KsonDocument): Diagnostic[] {
        const toolingDoc = document.getToolingDocument();
        const schemaToolingDoc = isKsonSchemaDocument(document)
            ? document.getMetaSchemaToolingDocument()
            : document.getSchemaToolingDocument();

        const messages = KsonTooling.getInstance()
            .validateDocument(toolingDoc, schemaToolingDoc ?? null)
            .asJsReadonlyArrayView();

        return messages.map(msg => toDiagnostic(msg));
    }
}

function toDiagnostic(msg: DiagnosticMessage): Diagnostic {
    return {
        range: {
            start: {line: msg.range.startLine, character: msg.range.startColumn},
            end: {line: msg.range.endLine, character: msg.range.endColumn}
        },
        severity: msg.severity === KtSeverity.ERROR
            ? DiagnosticSeverity.Error
            : DiagnosticSeverity.Warning,
        source: 'kson',
        message: msg.message
    };
}
