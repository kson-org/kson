package org.kson.tooling

import org.kson.parser.Coordinates
import org.kson.parser.behavior.StringUnquoted
import org.kson.schema.SchemaIdLookup
import org.kson.tooling.navigation.KsonValuePathBuilder
import org.kson.tooling.navigation.NavigatedSchema
import org.kson.tooling.navigation.SchemaNavigator
import org.kson.tooling.navigation.allowedAlongTrails
import org.kson.value.KsonList
import org.kson.value.KsonObject
import org.kson.value.KsonString
import org.kson.value.KsonValue

/** Gives property completions a [SnippetEdit] that opens the value, where the schema pins its type. */
internal object PropertySnippetBuilder {

    fun addSnippetEdits(
        completions: List<CompletionItem>,
        document: ToolingDocument,
        caret: Coordinates,
        idLookup: SchemaIdLookup
    ): List<CompletionItem> {
        if (completions.none { it.kind == CompletionKind.PROPERTY }) return completions
        val site = KsonValuePathBuilder(document, caret).newKeySite() ?: return completions
        // Not narrowed: adding the property may change which branches the document fits
        val navigator = SchemaNavigator(idLookup, carryDeadEnds = true, narrow = false)
        return completions.map { item ->
            val openedValue = item
                .takeIf { it.kind == CompletionKind.PROPERTY && StringUnquoted.isUnquotable(it.label) }
                ?.let { openedValue(navigator.navigate(site.objectPointer.child(it.label), document.rootAstNode)) }
                ?: return@map item
            val snippet = SnippetEdit(site.replaceRange, "${item.label}: $openedValue")
            CompletionItem(item.label, item.detail, item.documentation, item.kind, snippet)
        }
    }

    private fun openedValue(navigated: List<NavigatedSchema>): String? {
        // An unresolved `$ref` gives no snippet rather than being read as `false`, which would let an `anyOf`'s
        // other branches pin a type validation doesn't
        if (navigated.any { it.endsAtUnresolvedRef }) return null
        return when (navigated.allowedAlongTrails(::declaredTypes)?.singleOrNull()) {
            "object" -> "{\$0}"
            "array" -> "[\$0]"
            "string" -> "'\$0'"
            else -> null
        }
    }

    private fun declaredTypes(schema: KsonValue): Set<String>? {
        val names = when (val type = (schema as? KsonObject)?.propertyLookup?.get("type")) {
            is KsonString -> listOf(type.value)
            is KsonList -> type.elements.mapNotNull { (it as? KsonString)?.value }
            else -> return null
        }
        // `integer` is a `number`, so intersecting `[integer, string]` with `[number, string]` keeps both, rather
        // than leaving `string` to open quotes
        return names.map { if (it == "integer") "number" else it }.toSet()
    }
}
