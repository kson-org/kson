package org.kson.tooling

import org.kson.Kson
import org.kson.Message
import org.kson.MessageSeverity
import org.kson.SchemaResult
import org.kson.SchemaValidator
import org.kson.parser.messages.MessageType
import org.kson.parser.messages.MessageSeverity as InternalMessageSeverity
import org.kson.validation.SourceContext

/**
 * Validates a KSON document and returns [DiagnosticMessage]s.
 *
 * If a schema is provided and parses, validation includes any schema violations in addition to the document's own
 * diagnostics. If the schema has problems of its own, it cannot validate anything: a [MessageType.SCHEMA_UNUSABLE]
 * is reported on the document.
 */
internal object DiagnosticBuilder {

    fun build(content: String, schemaContent: String?, sourceContext: SourceContext): List<DiagnosticMessage> {
        if (schemaContent == null) {
            return documentDiagnostics(content, sourceContext)
        }

        return when (val result = Kson.parseSchema(schemaContent)) {
            is SchemaResult.Success ->
                documentDiagnostics(content, sourceContext, result.schemaValidator)
            is SchemaResult.Failure ->
                listOf(schemaUnusableDiagnostic(result.errors)) + documentDiagnostics(content, sourceContext)
        }
    }

    private fun documentDiagnostics(
        content: String,
        sourceContext: SourceContext,
        schemaValidator: SchemaValidator? = null
    ): List<DiagnosticMessage> =
        Kson.analyze(content, sourceContext.filepath, schemaValidator).errors.map { toDiagnosticMessage(it) }

    private fun schemaUnusableDiagnostic(schemaProblems: List<Message>): DiagnosticMessage {
        val firstProblem = schemaProblems.first()
        val message = MessageType.SCHEMA_UNUSABLE.create(
            firstProblem.start.render1Based(),
            firstProblem.message
        )
        return DiagnosticMessage(
            message = message.toString(),
            severity = toDiagnosticSeverity(MessageType.SCHEMA_UNUSABLE.severity),
            // anchor the external schema problem to the beginning of this document
            range = Range(0, 0, 0, 0)
        )
    }

    private fun toDiagnosticMessage(logged: Message): DiagnosticMessage {
        return DiagnosticMessage(
            message = logged.message,
            severity = toDiagnosticSeverity(logged.severity),
            range = Range(
                logged.start.line,
                logged.start.column,
                logged.end.line,
                logged.end.column
            )
        )
    }

    private fun toDiagnosticSeverity(severity: MessageSeverity): DiagnosticSeverity {
        return when (severity) {
            MessageSeverity.ERROR -> DiagnosticSeverity.ERROR
            MessageSeverity.WARNING -> DiagnosticSeverity.WARNING
        }
    }

    private fun toDiagnosticSeverity(severity: InternalMessageSeverity): DiagnosticSeverity {
        return when (severity) {
            InternalMessageSeverity.ERROR -> DiagnosticSeverity.ERROR
            InternalMessageSeverity.WARNING -> DiagnosticSeverity.WARNING
        }
    }
}
