import {
    Diagnostic,
    DiagnosticSeverity,
    Range,
    DocumentDiagnosticReport,
    RelatedFullDocumentDiagnosticReport
} from 'vscode-languageserver';
import {KsonDocument} from '../document/KsonDocument';
import {isKsonSchemaDocument} from '../document/KsonSchemaDocument';
import {Message, Kson, SchemaResult} from 'kson';

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
        // Schema validation already includes parse errors, so use it exclusively when
        // available to avoid duplicate diagnostics.
        const messages = this.getSchemaValidationMessages(document)
            ?? document.getAnalysisResult().errors.asJsReadonlyArrayView();
        return this.loggedMessagesToDiagnostics(messages);
    }

    private getSchemaValidationMessages(document: KsonDocument): readonly Message[] | null {
        const schema = isKsonSchemaDocument(document)
            ? document.getMetaSchemaDocument()
            : document.getSchemaDocument();
        if (!schema) return null;

        const parsedSchema = Kson.getInstance().parseSchema(schema.getText());
        if (!(parsedSchema instanceof SchemaResult.Success)) return null;

        return parsedSchema.schemaValidator.validate(document.getText(), document.uri).asJsReadonlyArrayView();
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
     * Convert Kson {@link Message} type to a to language server {@link Diagnostic} type.
     */
    private loggedMessageToDiagnostic(loggedMessage: Message): Diagnostic {
        let diagnosticSeverity: DiagnosticSeverity;
        switch (loggedMessage.severity.name) {
            case 'ERROR':
                diagnosticSeverity = DiagnosticSeverity.Error;
                break;
            case 'WARNING':
                diagnosticSeverity = DiagnosticSeverity.Warning;
                break;
            default:
                // Default to error if unknown severity
                diagnosticSeverity = DiagnosticSeverity.Error;
                break;
        }
        
        return {
            range: this.locationToRange(loggedMessage),
            severity: diagnosticSeverity,
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