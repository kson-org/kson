package org.kson

import org.kson.parser.LoggedMessage
import org.kson.parser.messages.MessageSeverity
import org.kson.validation.SourceContext

/**
 * Validates a KSON document and returns [DiagnosticMessage]s.
 *
 * If a schema is provided, validation includes both parse errors and schema violations.
 * If no schema is provided (or the schema fails to parse), only parse errors are returned.
 */
internal object DiagnosticBuilder {

    fun build(content: String, schemaContent: String?, sourceContext: SourceContext): List<DiagnosticMessage> {
        val schemaMessages = validateWithSchema(content, schemaContent, sourceContext)
        if (schemaMessages != null) return schemaMessages

        val parseResult = KsonCore.parseToAst(content, CoreCompileConfig(sourceContext = sourceContext))
        return parseResult.messages.map { toDiagnosticMessage(it) }
    }

    /**
     * Attempt schema validation. Returns null if no schema provided or schema fails to parse.
     */
    private fun validateWithSchema(content: String, schemaContent: String?, sourceContext: SourceContext): List<DiagnosticMessage>? {
        if (schemaContent == null) return null

        val schemaResult = KsonCore.parseSchema(schemaContent)
        val jsonSchema = schemaResult.jsonSchema ?: return null

        // Schema validation already includes parse errors, so we use it exclusively
        val parseResult = KsonCore.parseToAst(content, CoreCompileConfig(schemaJson = jsonSchema, sourceContext = sourceContext))
        return parseResult.messages.map { toDiagnosticMessage(it) }
    }

    private fun toDiagnosticMessage(logged: LoggedMessage): DiagnosticMessage {
        val severity = when (logged.message.type.severity) {
            MessageSeverity.ERROR -> DiagnosticSeverity.ERROR
            MessageSeverity.WARNING -> DiagnosticSeverity.WARNING
        }
        return DiagnosticMessage(
            message = logged.message.toString(),
            severity = severity,
            range = Range(
                logged.location.start.line,
                logged.location.start.column,
                logged.location.end.line,
                logged.location.end.column
            )
        )
    }
}
