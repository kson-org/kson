package org.kson.tooling.navigation

import org.kson.parser.Location
import org.kson.parser.MessageSink
import org.kson.schema.ResolvedRef
import org.kson.schema.SchemaIdLookup
import org.kson.schema.SchemaIdLookup.Companion.resolveUri
import org.kson.schema.SchemaParser
import org.kson.validation.SourceContext
import org.kson.validation.ValidationMode
import org.kson.value.KsonList
import org.kson.value.KsonObject
import org.kson.value.KsonString
import org.kson.value.KsonValue
import org.kson.value.navigation.json_pointer.JsonPointer
import org.kson.walker.KsonValueWalker
import org.kson.walker.navigateWithJsonPointer

/**
 * Describes how a schema was reached during navigation, recording the combinator or
 * structural step that produced it.
 */
enum class SchemaResolutionType {
    /** Schema found via direct property lookup in "properties" */
    DIRECT_PROPERTY,
    /** Schema found via pattern matching in "patternProperties" */
    PATTERN_PROPERTY,
    /** Schema from "additionalProperties" fallback */
    ADDITIONAL_PROPERTY,
    /** Schema from "items" or "additionalItems" for array elements */
    ARRAY_ITEMS,
    /** Schema from "allOf" combinator - all branches must be valid */
    ALL_OF,
    /** Schema from "anyOf" combinator - at least one branch must be valid */
    ANY_OF,
    /** Schema from "oneOf" combinator - exactly one branch must be valid */
    ONE_OF,
    /** Schema from "then" branch of an if/then conditional */
    IF_THEN,
    /** Schema from "else" branch of an if/then/else conditional */
    IF_ELSE,
    /** Root schema or schema resolved via $ref */
    ROOT;

    /**
     * True if this resolution type was produced by a branching construct (combinator
     * or conditional).  Stepping into a branch-marked ref preserves the marker so the
     * downstream leaf still gets filtered by the branch's semantics.
     *
     * Exhaustive by design: adding a new enum entry forces a compile error here so
     * the branch-vs-structural classification is an explicit decision, not a default.
     */
    val isBranchMarker: Boolean
        get() = when (this) {
            ONE_OF, ANY_OF, ALL_OF, IF_THEN, IF_ELSE -> true
            DIRECT_PROPERTY, PATTERN_PROPERTY, ADDITIONAL_PROPERTY,
            ARRAY_ITEMS, ROOT -> false
        }
}

/** A schema node found by navigation, annotated with how it was reached. */
data class NavigatedSchema(
    val resolvedValue: KsonValue,
    val resolvedValueBaseUri: String,
    val resolutionType: SchemaResolutionType
)

/**
 * Navigates a [JsonPointer] through a schema, returning all sub-schemas at the
 * target location fully flattened (combinators exploded, conditionals narrowed by
 * strict isValid against the document).
 *
 * The navigator is built on two primitives:
 * - [stepInto]: structural per-token descent through a single schema, no combinator
 *   awareness.
 * - [flatten]: doc-aware decomposition of a schema's top-level branches.
 *
 * Every level applies `flatten` before stepping, including the root and the target,
 * so no post-navigation expansion pass is needed.
 *
 * Mirrors the shape of `TreeNavigator` in the walker package (see
 * [org.kson.walker.navigateWithJsonPointer]) — one entry point, small internal
 * helpers — so the pattern is recognizable.
 */
internal class SchemaNavigator(
    private val idLookup: SchemaIdLookup,
    private val incompleteRegion: Location? = null
) {

    /**
     * Navigate schema by document path tokens.
     *
     * This function translates document paths to schema paths by inserting schema-specific wrappers:
     * - For object properties: navigates through "properties" wrapper
     * - For array indices: navigates to "items" schema (all array elements share the same schema)
     * - Falls back to "additionalProperties" or "patternProperties" when specific property not found
     * - Resolves `$ref` references to their target schemas
     * - Handles combinators (allOf, anyOf, oneOf) and conditionals (if/then/else), flattening
     *   them at every level so callers receive fully decomposed branches
     *
     * Base URI tracking is handled internally to ensure correct `$ref` resolution.
     *
     * Returns a list because a single document path can match multiple schema locations:
     * - Property defined in multiple combinator branches
     * - Multiple patternProperties matching
     * - Both `then` and `else` branches active when the `if` can't be evaluated
     *
     * Example:
     * ```kotlin
     * // Document path: ["users", "0", "name"]
     * // Schema navigation: properties/users → items → properties/name
     * val idLookup = SchemaIdLookup(schemaRoot)
     * val schemaRefs = SchemaNavigator(idLookup).navigate(JsonPointer.fromTokens(listOf("users", "0", "name")))
     * ```
     *
     * Narrowing treats [documentValue] as authoritative: a branch is dropped where the document
     * contradicts it at that level.  A caller editing a value in place (e.g. completion) passes the
     * span of that half-authored value as [incompleteRegion]; validation errors located inside it are
     * forgiven, so an incomplete value never disqualifies a branch.
     *
     * @param documentPointer Pointer through the document (from [org.kson.walker.navigateToLocationWithPointer])
     * @param documentValue The document being navigated against (drives branch narrowing)
     * @return List of [NavigatedSchema] containing all sub-schemas at that location (empty if not found)
     */
    fun navigate(
        documentPointer: JsonPointer,
        documentValue: KsonValue? = null
    ): List<NavigatedSchema> {
        val rootBaseUri = idLookup.rootBaseUri
        val rootResolved = idLookup.resolveRefIfPresent(idLookup.schemaRootValue, rootBaseUri)
        val rootRef = NavigatedSchema(
            rootResolved.resolvedValue,
            rootResolved.resolvedValueBaseUri,
            SchemaResolutionType.ROOT
        )

        val tokens = documentPointer.tokens
        var current = flatten(rootRef, documentValue)
        var currentDocValue = documentValue

        for (token in tokens) {
            val stepped = current.flatMap { stepInto(it, token) }
            currentDocValue = currentDocValue?.let { docVal ->
                KsonValueWalker.navigateWithJsonPointer(docVal, JsonPointer.fromTokens(listOf(token)))
            }
            current = stepped.flatMap { flatten(it, currentDocValue) }
            if (current.isEmpty()) break
        }

        return current
    }

    /**
     * Structural step by one pointer token.  Looks at properties / patternProperties /
     * additionalProperties (for names) or items / additionalItems (for integer indices).
     * No combinator / conditional logic — [flatten] handles branching.
     *
     * Applies `$id` on [ref] to the base URI before property lookup, and resolves `$ref`
     * on the stepped-into schema.
     *
     * Branch context inheritance: if [ref]'s resolutionType is a branch marker
     * (ONE_OF / ANY_OF / ALL_OF / IF_THEN / IF_ELSE), stepped results keep that marker
     * so downstream value merging still treats them as conditional.  Otherwise the
     * stepped result's resolutionType reflects how the step resolved the token
     * (DIRECT_PROPERTY / PATTERN_PROPERTY / ADDITIONAL_PROPERTY / ARRAY_ITEMS).
     */
    private fun stepInto(ref: NavigatedSchema, token: String): List<NavigatedSchema> {
        val schemaObj = ref.resolvedValue as? KsonObject ?: return emptyList()

        // Apply $id on the current node to the base URI before any lookup from this node.
        var updatedBaseUri = ref.resolvedValueBaseUri
        schemaObj.propertyLookup[$$"$id"]?.let { idValue ->
            if (idValue is KsonString) {
                updatedBaseUri = resolveUri(idValue.value, updatedBaseUri).toString()
            }
        }

        val stepped = mutableListOf<Pair<KsonValue, SchemaResolutionType>>()
        val isArrayIndex = token.toIntOrNull() != null

        if (isArrayIndex) {
            schemaObj.propertyLookup["items"]?.let {
                stepped.add(it to SchemaResolutionType.ARRAY_ITEMS)
            }
            schemaObj.propertyLookup["additionalItems"]?.let {
                stepped.add(it to SchemaResolutionType.ARRAY_ITEMS)
            }
        } else {
            val properties = schemaObj.propertyLookup["properties"] as? KsonObject
            properties?.propertyMap?.get(token)?.let {
                stepped.add(it.propValue to SchemaResolutionType.DIRECT_PROPERTY)
            }

            val patternProperties = schemaObj.propertyLookup["patternProperties"] as? KsonObject
            addPatternPropertyMatches(patternProperties, token, stepped)

            if (stepped.isEmpty()) {
                schemaObj.propertyLookup["additionalProperties"]?.let {
                    stepped.add(it to SchemaResolutionType.ADDITIONAL_PROPERTY)
                }
            }
        }

        val inheritedType = ref.resolutionType.takeIf { it.isBranchMarker }
        return stepped.map { (value, stepType) ->
            val resolved = idLookup.resolveRefIfPresent(value, updatedBaseUri)
            NavigatedSchema(
                resolved.resolvedValue,
                resolved.resolvedValueBaseUri,
                inheritedType ?: stepType
            )
        }
    }

    /**
     * Adds each [patternProperties] entry whose regex matches [token] to [stepped].
     * Invalid regex patterns are skipped — Throwable also catches JavaScript SyntaxError
     * and other platform-specific errors.
     */
    private fun addPatternPropertyMatches(
        patternProperties: KsonObject?,
        token: String,
        stepped: MutableList<Pair<KsonValue, SchemaResolutionType>>
    ) {
        patternProperties?.propertyMap?.forEach { (pattern, property) ->
            try {
                if (Regex(pattern).containsMatchIn(token)) {
                    stepped.add(property.propValue to SchemaResolutionType.PATTERN_PROPERTY)
                }
            } catch (_: Throwable) {
                // Invalid regex pattern, skip it
            }
        }
    }

    /**
     * Flatten a schema's top-level branches, narrowing each against [docVal] — the
     * document value at this level (the object/array/scalar that contains these
     * combinators).  Handles:
     *   - oneOf / anyOf: a branch is emitted only when it is compatible with [docVal]
     *     (soft validation — see [isCompatibleWithDocument]).  Because narrowing happens
     *     where the combinator lives, sibling discriminators (e.g. a `const` on a property
     *     other than the one being navigated to) are visible and contradicted branches are
     *     dropped right here, with full ancestor context.  When [docVal] is null, all
     *     branches are emitted (nothing to narrow against).
     *   - allOf: unconditional expansion, every branch emitted (all must hold).
     *   - if / then / else: see [evaluateIf].  A matching `if` emits `then`; a
     *     contradicted `if` emits `else`; an undecidable `if` (no document, unparseable
     *     condition, or failing only because a required discriminator isn't present yet)
     *     emits both branches.
     *
     * Recurses into each branch so nested combinators/conditionals are fully flattened,
     * each narrowed against the same [docVal].
     *
     * The parent [ref] is preserved at the head of the result so its title, description,
     * and constraints remain available.
     */
    private fun flatten(
        ref: NavigatedSchema,
        docVal: KsonValue?,
        inProgress: MutableList<Pair<String, KsonValue>> = mutableListOf()
    ): List<NavigatedSchema> {
        val schemaObj = ref.resolvedValue as? KsonObject ?: return listOf(ref)

        // Cycle guard: a oneOf/anyOf/allOf branch can be a `$ref` back to a node already
        // being flattened on this path (a legal recursive grammar where one alternative is
        // "the whole thing again").  Combinator expansion doesn't consume a pointer token the
        // way properties/items steps do, so without this guard flatten re-enters the same node
        // forever.  Emit the node bare and stop descending when we'd re-enter it.
        if (inProgress.any { it.first == ref.resolvedValueBaseUri && it.second === schemaObj }) {
            return listOf(ref)
        }
        inProgress.add(ref.resolvedValueBaseUri to schemaObj)

        val results = mutableListOf<NavigatedSchema>()
        var addedBranches = false

        fun addBranch(branch: KsonValue, resolutionType: SchemaResolutionType) {
            val resolved = idLookup.resolveRefIfPresent(branch, ref.resolvedValueBaseUri)
            val branchRef = NavigatedSchema(
                resolved.resolvedValue,
                resolved.resolvedValueBaseUri,
                resolutionType
            )
            results.addAll(flatten(branchRef, docVal, inProgress))
            addedBranches = true
        }

        // oneOf/anyOf alternatives are narrowed against the document at this level: a
        // branch is dropped when it contradicts the document (e.g. a discriminating
        // sibling property).  This is the single, doc-aware narrowing point — no
        // post-navigation sibling/leaf filtering pass is needed.
        fun addNarrowedBranch(branch: KsonValue, resolutionType: SchemaResolutionType) {
            val resolved = idLookup.resolveRefIfPresent(branch, ref.resolvedValueBaseUri)
            if (docVal != null && !isCompatibleWithDocument(resolved, docVal)) {
                // narrowing still happened even if every branch was dropped
                addedBranches = true
                return
            }
            val branchRef = NavigatedSchema(
                resolved.resolvedValue,
                resolved.resolvedValueBaseUri,
                resolutionType
            )
            results.addAll(flatten(branchRef, docVal, inProgress))
            addedBranches = true
        }

        (schemaObj.propertyLookup["oneOf"] as? KsonList)?.elements?.forEach { branch ->
            addNarrowedBranch(branch, SchemaResolutionType.ONE_OF)
        }

        (schemaObj.propertyLookup["anyOf"] as? KsonList)?.elements?.forEach { branch ->
            addNarrowedBranch(branch, SchemaResolutionType.ANY_OF)
        }

        (schemaObj.propertyLookup["allOf"] as? KsonList)?.elements?.forEach { branch ->
            addBranch(branch, SchemaResolutionType.ALL_OF)
        }

        conditionalBranches(schemaObj, ref.resolvedValueBaseUri, docVal).forEach { (branch, resolutionType) ->
            addBranch(branch, resolutionType)
        }

        if (addedBranches) {
            results.add(0, ref)
        } else {
            results.add(ref)
        }

        inProgress.removeAt(inProgress.size - 1)
        return results
    }

    /**
     * The `then`/`else` branches an `if` conditional contributes, per [evaluateIf] against [docVal]:
     * a matching `if` yields `then`; a contradicted `if` yields `else`; an undecidable `if` yields both.
     * Empty when there is no `if`.  Ordering (`then` before `else`) matches the flattened result order.
     */
    private fun conditionalBranches(
        schemaObj: KsonObject,
        baseUri: String,
        docVal: KsonValue?
    ): List<Pair<KsonValue, SchemaResolutionType>> {
        val ifCondition = schemaObj.propertyLookup["if"] ?: return emptyList()
        val thenBranch = schemaObj.propertyLookup["then"]
        val elseBranch = schemaObj.propertyLookup["else"]
        return when (evaluateIf(ifCondition, baseUri, docVal)) {
            IfState.MATCH -> listOfNotNull(thenBranch?.let { it to SchemaResolutionType.IF_THEN })
            IfState.NO_MATCH -> listOfNotNull(elseBranch?.let { it to SchemaResolutionType.IF_ELSE })
            IfState.UNDETERMINED -> listOfNotNull(
                thenBranch?.let { it to SchemaResolutionType.IF_THEN },
                elseBranch?.let { it to SchemaResolutionType.IF_ELSE }
            )
        }
    }

    /**
     * Soft-validates [ref]'s schema against [docVal] using [ValidationMode.PARTIAL]: a branch is
     * "compatible" unless the document ACTIVELY contradicts it (a present value violates a value
     * constraint).  Mere incompleteness — a missing required property, a not-yet-reached minimum —
     * never disqualifies a branch, because the schema layer itself skips those constraints in
     * partial mode.
     *
     * Errors located inside [incompleteRegion] — the span of the value the caller is still authoring —
     * are forgiven, so a half-typed value raises no disqualifying error: a branch is compatible when
     * every partial-validation error it logs falls within that region.
     */
    private fun isCompatibleWithDocument(
        ref: ResolvedRef,
        docVal: KsonValue
    ): Boolean {
        val schema = SchemaParser.parseSchemaElement(
            ref.resolvedValue, MessageSink(), ref.resolvedValueBaseUri, idLookup
        ) ?: return true
        val sink = MessageSink()
        schema.validate(docVal, sink, SourceContext(mode = ValidationMode.PARTIAL))
        // compatible iff every partial-validation error comes from the value being authored
        return sink.loggedMessages().all { logged ->
            incompleteRegion != null && logged.location in incompleteRegion
        }
    }

    /**
     * Evaluate an `if` condition against [docVal] into three states.  A fully valid condition is
     * [IfState.MATCH].  Otherwise the condition is re-checked under [ValidationMode.PARTIAL] and every
     * remaining error located inside [incompleteRegion] — the span of the value being authored — is
     * forgiven.  If nothing survives that forgiveness the `if` is only unsatisfied because the document
     * is incomplete (e.g. a required discriminator hasn't been typed yet), so it is
     * [IfState.UNDETERMINED] (emit both branches) rather than [IfState.NO_MATCH].  An empty error list
     * likewise yields UNDETERMINED, matching partial validity.  This distinguishes "not decidable due
     * to incompleteness" from "decidably false against present data" (emit `else`), without inspecting
     * any validator message types.
     */
    private fun evaluateIf(ifCondition: KsonValue, baseUri: String, docVal: KsonValue?): IfState {
        if (docVal == null) return IfState.UNDETERMINED
        val ifSchema = SchemaParser.parseSchemaElement(ifCondition, MessageSink(), baseUri, idLookup)
            ?: return IfState.UNDETERMINED
        if (ifSchema.isValid(docVal, MessageSink())) return IfState.MATCH
        val sink = MessageSink()
        ifSchema.validate(docVal, sink, SourceContext(mode = ValidationMode.PARTIAL))
        val onlyAuthoredValueErrors = sink.loggedMessages().all { logged ->
            incompleteRegion != null && logged.location in incompleteRegion
        }
        return if (onlyAuthoredValueErrors) IfState.UNDETERMINED else IfState.NO_MATCH
    }

    /** Outcome of evaluating an `if` condition during [SchemaNavigator] flattening. */
    private enum class IfState { MATCH, NO_MATCH, UNDETERMINED }
}
