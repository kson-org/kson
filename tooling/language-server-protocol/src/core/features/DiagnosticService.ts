import {
    Diagnostic,
    DiagnosticSeverity,
    Range,
    DocumentDiagnosticReport,
    RelatedFullDocumentDiagnosticReport
} from 'vscode-languageserver';
import {LoggedMessage} from 'kson';
import {KsonDocument} from '../document/KsonDocument';

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
        return this.loggedMessagesToDiagnostics(document.getParseResult().messages.asJsReadonlyArrayView());
    }

    /**
     * Convert multiple Kson {@link messages} to language server {@link Diagnostic}s.
     * @param messages
     * @private
     */
    private loggedMessagesToDiagnostics(messages: readonly LoggedMessage[]): Diagnostic[] {
        return messages.map(msg => this.loggedMessageToDiagnostic(msg));
    }

    /**
     * Convert Kson {@link LoggedMessage} type to a to language server {@link Diagnostic} type.
     */
    private loggedMessageToDiagnostic(loggedMessage: LoggedMessage): Diagnostic {
        return {
            range: this.locationToRange(loggedMessage.location || {
                firstLine: 0,
                firstColumn: 0,
                lastLine: 0,
                lastColumn: 0
            }),
            severity: DiagnosticSeverity.Error,
            source: 'kson',
            message: loggedMessage.message.toString(),
            code: loggedMessage.message.type.name
        };
    }

    /**
     * Convert Kson Location to LSP Range.
     */
    private locationToRange(location: any): Range {
        return {
            start: {
                line: location.firstLine || 0,
                character: location.firstColumn || 0
            },
            end: {
                line: location.lastLine || 0,
                character: location.lastColumn || 0
            }
        };
    }
} 