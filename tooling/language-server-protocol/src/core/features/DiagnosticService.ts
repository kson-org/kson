import {
    Diagnostic,
    DiagnosticSeverity,
    Range,
    DocumentDiagnosticReport,
    RelatedFullDocumentDiagnosticReport
} from 'vscode-languageserver';
import {KsonDocument} from '../document/KsonDocument';
import {Message} from 'kson';

/**
 * Service responsible for providing diagnostic information for Kson documents.
 */
export class DiagnosticService {

    createDocumentDiagnosticReport(document: KsonDocument): DocumentDiagnosticReport {
        const diagnostics = document ? this.getDiagnostics(document) : [];
        return {
            kind: 'full',
            items: diagnostics
        } as RelatedFullDocumentDiagnosticReport;
    }

    private getDiagnostics(document: KsonDocument): Diagnostic[] {
        return this.loggedMessagesToDiagnostics(document.getAnalysisResult().errors.asJsReadonlyArrayView());
    }

    /**
     * Convert multiple Kson {@link messages} to language server {@link Diagnostic}s.
     * @param messages
     * @private
     */
    private loggedMessagesToDiagnostics(messages: readonly Message[]): Diagnostic[] {
        return messages.map(msg => this.loggedMessageToDiagnostic(msg));
    }

    /**
     * Convert Kson {@link LoggedMessage} type to a to language server {@link Diagnostic} type.
     */
    private loggedMessageToDiagnostic(loggedMessage: Message): Diagnostic {
        return {
            range: this.locationToRange(loggedMessage),
            severity: DiagnosticSeverity.Error,
            source: 'kson',
            message: loggedMessage.message.toString(),
        };
    }

    /**
     * Convert Kson Location to LSP Range.
     */
    private locationToRange(message: Message): Range {
        return {
            start: {
                line: message.start.line,
                character: message.start.column
            },
            end: {
                line: message.end.line,
                character: message.end.column
            }
        };
    }
}