package tooling

import org.kson.Kson
import org.kson.Message
import org.kson.MessageSeverity
import org.kson.SchemaResult
import org.kson.validation.SourceContext

/**
 * Validates a KSON document and returns [DiagnosticMessage]s.
 *
 * If a schema is provided and valid, validation includes both parse errors and schema violations.
 * If no schema is provided or the schema fails to parse, only document parse errors are returned.
 */
internal object DiagnosticBuilder {

    fun build(content: String, schemaContent: String?, sourceContext: SourceContext): List<DiagnosticMessage> {
        if (schemaContent == null) {
            return Kson.analyze(content, sourceContext.filepath).errors.map { toDiagnosticMessage(it) }
        }

        val diagnosticMessages = when (val result = Kson.parseSchema(schemaContent)) {
            is SchemaResult.Success -> result.schemaValidator.validate(content, filepath = sourceContext.filepath)
            is SchemaResult.Failure -> Kson.analyze(content, sourceContext.filepath).errors
        }

        return diagnosticMessages.map { toDiagnosticMessage(it) }
    }

    private fun toDiagnosticMessage(logged: Message): DiagnosticMessage {
        val severity = when (logged.severity) {
            MessageSeverity.ERROR -> DiagnosticSeverity.ERROR
            MessageSeverity.WARNING -> DiagnosticSeverity.WARNING
        }
        return DiagnosticMessage(
            message = logged.message,
            severity = severity,
            range = Range(
                logged.start.line,
                logged.start.column,
                logged.end.line,
                logged.end.column
            )
        )
    }
}
