package org.kson.tooling.navigation

import org.kson.ast.AstNode
import org.kson.ast.ListNode
import org.kson.parser.Location
import org.kson.parser.MessageSink
import org.kson.schema.ResolvedRef
import org.kson.schema.SchemaIdLookup
import org.kson.schema.SchemaIdLookup.Companion.resolveUri
import org.kson.schema.SchemaParser
import org.kson.validation.SourceContext
import org.kson.validation.ValidationMode
import org.kson.value.KsonBoolean
import org.kson.value.KsonList
import org.kson.value.KsonObject
import org.kson.value.KsonString
import org.kson.value.KsonValue
import org.kson.value.toKsonValueOrNull
import org.kson.walker.AstNodeWalker
import org.kson.walker.TreePointer
import org.kson.walker.nodesAlong

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

    /**
     * True for a schema that only applies when the document takes its branch: a oneOf/anyOf
     * branch or an if/then/else outcome.
     *
     * Exhaustive by design, like [isBranchMarker].
     */
    val isAlternative: Boolean
        get() = when (this) {
            ONE_OF, ANY_OF, IF_THEN, IF_ELSE -> true
            ALL_OF, DIRECT_PROPERTY, PATTERN_PROPERTY, ADDITIONAL_PROPERTY,
            ARRAY_ITEMS, ROOT -> false
        }
}

/** A schema node found by navigation, annotated with how it was reached. */
data class NavigatedSchema(
    val resolvedValue: KsonValue,
    val resolvedValueBaseUri: String,
    val resolutionType: SchemaResolutionType,
    val branchTrail: List<BranchStep> = emptyList()
)

/** Navigation took [branch] of [alternative], a `oneOf`/`anyOf` list or `if` condition, [depth] pointer tokens down. */
data class BranchStep(
    val alternative: KsonValue,
    val baseUri: String,
    val depth: Int,
    val branch: Branch
) {
    /** The branches [alternative] offers, whichever navigation took: its indices, or an `if`'s `then` and `else`. */
    val branches: List<Branch>
        get() = (alternative as? KsonList)?.elements?.indices?.map { Branch.Index(it) } ?: listOf(Branch.Then, Branch.Else)

    /** Equal alternatives, not only identical ones, are one choice: a value takes the same branch of each. */
    fun isSameChoice(other: BranchStep): Boolean =
        alternative == other.alternative && baseUri == other.baseUri && depth == other.depth
}

sealed interface Branch {
    data class Index(val index: Int) : Branch
    data object Then : Branch
    data object Else : Branch
}

internal val KsonValue.isFalseSchema: Boolean
    get() = this is KsonBoolean && !value

/**
 * Navigates a document's [TreePointer] through a schema, returning all sub-schemas at the
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
 * [org.kson.walker.navigate]) — one entry point, small internal
 * helpers — so the pattern is recognizable.
 *
 * With [toNewProperty], the pointer's last token is a property the document doesn't have yet.  Adding it may sway
 * any branch or `if`, so the document narrows none: it is read only to tell list indices from numeric names.
 */
internal class SchemaNavigator(
    private val idLookup: SchemaIdLookup,
    private val incompleteRegion: Location? = null,
    private val toNewProperty: Boolean = false
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
     * val schemaRefs = SchemaNavigator(idLookup).navigate(TreePointer(JsonPointer.fromTokens(listOf("users", "0", "name"))))
     * ```
     *
     * Narrowing treats the document as authoritative: a branch is dropped where the document
     * contradicts it at that level.  A caller editing a value in place (e.g. completion) passes the
     * span of that half-authored value as [incompleteRegion]; validation errors located inside it are
     * forgiven, so an incomplete value never disqualifies a branch.
     *
     * The document is walked on its AST, the tree [documentPointer] was built on (see [TreePointer]),
     * and the node reached at each level is converted with [toKsonValueOrNull] to narrow by, so
     * whatever did parse in a broken document still narrows.
     *
     * @param documentPointer Pointer through the document's AST (e.g. from [KsonValuePathBuilder])
     * @param documentAst Root of the document's AST, walked along [documentPointer] (drives branch narrowing)
     * @return List of [NavigatedSchema] containing all sub-schemas at that location (empty if not found)
     */
    fun navigate(
        documentPointer: TreePointer<AstNode>,
        documentAst: AstNode? = null
    ): List<NavigatedSchema> {
        val rootBaseUri = idLookup.rootBaseUri
        val rootResolved = idLookup.resolveRefIfPresent(idLookup.schemaRootValue, rootBaseUri)
        val rootRef = NavigatedSchema(
            rootResolved.resolvedValue,
            rootResolved.resolvedValueBaseUri,
            SchemaResolutionType.ROOT
        )

        // the bare tokens step the schema by name; the document is walked along the tagged pointer
        val tokens = documentPointer.pointer.tokens
        val docNodes = documentAst?.let { AstNodeWalker.nodesAlong(it, documentPointer) }.orEmpty()
        if (toNewProperty && !numbersAreIndices(tokens, listOfNotNull(documentAst) + docNodes)) return emptyList()
        var current = flatten(rootRef, narrowingValue(documentAst), depth = 0)
        if (reachesUnresolvedRef(current)) return emptyList()

        for ((index, token) in tokens.withIndex()) {
            val stepped = current.flatMap { stepInto(it, token) }
            val docValue = narrowingValue(docNodes.getOrNull(index))
            current = stepped.flatMap { flatten(it, docValue, depth = index + 1) }
            if (reachesUnresolvedRef(current)) return emptyList()
            if (current.isEmpty()) break
        }

        return current
    }

    private fun narrowingValue(docNode: AstNode?): KsonValue? = if (toNewProperty) null else docNode?.toKsonValueOrNull()

    // An unresolved `$ref` isn't read as `false`: that would let an `anyOf`'s other branches pin a
    // type validation doesn't.
    private fun reachesUnresolvedRef(level: List<NavigatedSchema>): Boolean =
        toNewProperty && level.any { (it.resolvedValue as? KsonObject)?.propertyLookup?.get($$"$ref") is KsonString }

    private fun numbersAreIndices(tokens: List<String>, containerOfEachToken: List<AstNode>): Boolean =
        tokens.withIndex().all { (index, token) -> token.toIntOrNull() == null || containerOfEachToken.getOrNull(index) is ListNode }

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
        if (toNewProperty && ref.resolvedValue.isFalseSchema) return listOf(ref)
        val schemaObj = ref.resolvedValue as? KsonObject ?: return emptyList()
        val updatedBaseUri = baseUriWithin(schemaObj, ref.resolvedValueBaseUri)

        val stepped = mutableListOf<Pair<KsonValue, SchemaResolutionType>>()
        val arrayIndex = token.toIntOrNull()

        if (arrayIndex != null && toNewProperty) {
            itemSchema(schemaObj, arrayIndex)?.let {
                stepped.add(it to SchemaResolutionType.ARRAY_ITEMS)
            }
        } else if (arrayIndex != null) {
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
                inheritedType ?: stepType,
                ref.branchTrail
            )
        }
    }

    // Mirrors the draft-7 item keywords SchemaParser validates, and must grow with it, as it would for prefixItems
    private fun itemSchema(arraySchema: KsonObject, index: Int): KsonValue? {
        val items = arraySchema.propertyLookup["items"]
        val tuple = items as? KsonList ?: return items
        return tuple.elements.getOrNull(index) ?: arraySchema.propertyLookup["additionalItems"]
    }

    /**
     * The base URI inside [schema], which is read under [baseUri]: [baseUri] updated by [schema]'s own
     * `$id`.  The `$ref`s in [schema]'s keywords resolve against it, as the validator resolves them.
     */
    private fun baseUriWithin(schema: KsonObject, baseUri: String): String {
        val id = schema.propertyLookup[$$"$id"] as? KsonString ?: return baseUri
        return resolveUri(id.value, baseUri).toString()
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
     *   - allOf: unconditional expansion, every branch emitted (all must hold).  Inside an
     *     alternative, the members keep the alternative's mark instead of ALL_OF: they hold
     *     only when that alternative does (see [SchemaResolutionType.isAlternative]).
     *     Accepted trade-off: consumers then union the members' enums with the alternative's
     *     instead of intersecting them, so a value only one member allows can be offered.
     *     Marking them ALL_OF instead would intersect them against every other alternative
     *     and hide values those allow, which is worse.
     *   - if / then / else: see [evaluateIf].  A matching `if` emits `then`; a
     *     contradicted `if` emits `else`; an undecidable `if` (no document, unparseable
     *     condition, or failing only because a required discriminator isn't present yet)
     *     emits both branches.
     *
     * Recurses into each branch so nested combinators/conditionals are fully flattened,
     * each narrowed against the same [docVal].  Like [stepInto], it reads [ref]'s keywords under the
     * base URI [ref]'s own `$id` gives (see [baseUriWithin]).
     *
     * The parent [ref] is preserved at the head of the result so its title, description,
     * and constraints remain available.
     */
    private fun flatten(
        ref: NavigatedSchema,
        docVal: KsonValue?,
        depth: Int,
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

        val baseUri = baseUriWithin(schemaObj, ref.resolvedValueBaseUri)
        val results = mutableListOf<NavigatedSchema>()
        var addedBranches = false

        fun addBranch(branch: KsonValue, resolutionType: SchemaResolutionType, step: BranchStep? = null) {
            val resolved = idLookup.resolveRefIfPresent(branch, baseUri)
            val branchRef = NavigatedSchema(
                resolved.resolvedValue,
                resolved.resolvedValueBaseUri,
                resolutionType,
                ref.branchTrail + listOfNotNull(step)
            )
            results.addAll(flatten(branchRef, docVal, depth, inProgress))
            addedBranches = true
        }

        // oneOf/anyOf alternatives are narrowed against the document at this level: a
        // branch is dropped when it contradicts the document (e.g. a discriminating
        // sibling property).  This is the single, doc-aware narrowing point — no
        // post-navigation sibling/leaf filtering pass is needed.
        fun addNarrowedBranch(alternative: KsonList, index: Int, resolutionType: SchemaResolutionType) {
            val resolved = idLookup.resolveRefIfPresent(alternative.elements[index], baseUri)
            if (docVal != null && !isCompatibleWithDocument(resolved, docVal)) {
                // narrowing still happened even if every branch was dropped
                addedBranches = true
                return
            }
            val branchRef = NavigatedSchema(
                resolved.resolvedValue,
                resolved.resolvedValueBaseUri,
                resolutionType,
                ref.branchTrail + BranchStep(alternative, baseUri, depth, Branch.Index(index))
            )
            results.addAll(flatten(branchRef, docVal, depth, inProgress))
            addedBranches = true
        }

        val oneOf = schemaObj.propertyLookup["oneOf"] as? KsonList
        oneOf?.elements?.indices?.forEach { addNarrowedBranch(oneOf, it, SchemaResolutionType.ONE_OF) }

        val anyOf = schemaObj.propertyLookup["anyOf"] as? KsonList
        anyOf?.elements?.indices?.forEach { addNarrowedBranch(anyOf, it, SchemaResolutionType.ANY_OF) }

        val allOfMemberType = ref.resolutionType.takeIf { it.isAlternative } ?: SchemaResolutionType.ALL_OF
        (schemaObj.propertyLookup["allOf"] as? KsonList)?.elements?.forEach { branch ->
            addBranch(branch, allOfMemberType)
        }

        conditionalBranches(schemaObj, baseUri, docVal, depth).forEach { (branch, resolutionType, step) ->
            addBranch(branch, resolutionType, step)
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
        docVal: KsonValue?,
        depth: Int
    ): List<Triple<KsonValue, SchemaResolutionType, BranchStep>> {
        val ifCondition = schemaObj.propertyLookup["if"] ?: return emptyList()
        val thenBranch = schemaObj.propertyLookup["then"]
            ?.let { Triple(it, SchemaResolutionType.IF_THEN, BranchStep(ifCondition, baseUri, depth, Branch.Then)) }
        val elseBranch = schemaObj.propertyLookup["else"]
            ?.let { Triple(it, SchemaResolutionType.IF_ELSE, BranchStep(ifCondition, baseUri, depth, Branch.Else)) }
        return when (evaluateIf(ifCondition, baseUri, docVal)) {
            IfState.MATCH -> listOfNotNull(thenBranch)
            IfState.NO_MATCH -> listOfNotNull(elseBranch)
            IfState.UNDETERMINED -> listOfNotNull(thenBranch, elseBranch)
        }
    }

    /**
     * Soft-validates [ref]'s schema against [docVal] using [ValidationMode.PARTIAL]: a branch is
     * "compatible" unless the document ACTIVELY contradicts it (a present value violates a value
     * constraint).  Mere incompleteness — a missing required property, a not-yet-reached minimum —
     * never disqualifies a branch, because the schema layer itself skips those constraints in
     * partial mode.  Narrowing assumes partial validation accepts whatever full validation accepts: then
     * a branch the document satisfies is never dropped, and a schema that fails partial validation fails
     * full validation too.
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
