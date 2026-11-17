package org.kson.schema

import org.kson.parser.MessageSink
import org.kson.uri.UriReference
import org.kson.value.KsonObject
import org.kson.value.KsonString
import org.kson.value.KsonValue

/**
 * A schema keyword, or a group of keywords compiled together, such as `if`, `then` and `else`.  [names] are the
 * properties it reads.
 */
internal abstract class SchemaKeyword(val names: Set<String>) {
    /** Returns the subschemas [schema] has under [names].  Must not throw, even on malformed values. */
    open fun declaredSubschemas(schema: KsonObject): List<DeclaredSubschema> = emptyList()

    /**
     * Compiles this keyword's validator for the schema in [context], or returns null if it adds no validator.  It
     * compiles its subschemas only through [subschemas], which has one [CompilableSubschema] for each
     * [DeclaredSubschema] that [declaredSubschemas] returned.
     */
    abstract fun compile(context: KeywordContext, subschemas: List<CompilableSubschema>): JsonSchemaValidator?
}

/** A subschema [value] that a schema has under [keyword], and the part of the instance it [applies] to */
internal class DeclaredSubschema(val keyword: String, val value: KsonValue, val applies: Applies)

/**
 * A [declared] subschema that compiles when [compiled] is first read.  This lets its keyword decide which of its
 * subschemas compile, and in what order their errors are reported.
 */
internal class CompilableSubschema(private val declared: DeclaredSubschema, compile: () -> JsonSchema?) {
    val keyword: String get() = declared.keyword
    val applies: Applies get() = declared.applies
    val value: KsonValue get() = declared.value

    /** The compiled subschema, or null if it doesn't compile.  Compiling reports why. */
    val compiled: JsonSchema? by lazy(compile)
}

/**
 * The schema object a keyword compiles for, and the [messageSink] that compiling reports errors to.  [baseUri] is the
 * base URI inside [schema], which its `$ref` resolves against.
 */
internal class KeywordContext(
    val schema: KsonObject,
    val messageSink: MessageSink,
    private val baseUri: UriReference,
    val indexedDocuments: IndexedSchemaDocuments
) {
    val properties: Map<String, KsonValue> get() = schema.propertyLookup

    val title: String? get() = (properties["title"] as? KsonString)?.value

    /** Whether a `$ref` in [schema] doesn't resolve, which makes compiling [schema] return null */
    var isNoSchema = false
        private set

    /** Resolves [ref] against [baseUri] and returns its target, or null if there is none */
    fun resolveRef(ref: String): ResolvedRef? = indexedDocuments.resolveRef(ref, baseUri)

    /** Reports [unresolvedRef] and makes compiling [schema] return null */
    fun rejectSchema(unresolvedRef: UnresolvedRef) {
        SchemaParser.reportUnresolvedRef(unresolvedRef, messageSink)
        isNoSchema = true
    }
}
