package org.kson.tooling.navigation

import org.kson.ast.AstNode
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
import org.kson.walker.NodeChildren
import org.kson.walker.TreePointer
import org.kson.walker.nodesAlong

/** A schema node found by navigation, with the branches taken to reach it. */
internal data class NavigatedSchema(
    val resolvedValue: KsonValue,
    val resolvedValueBaseUri: String,
    val branchTrail: List<BranchStep> = emptyList()
)

/** Navigation took [branch] of [choice]. */
internal data class BranchStep(val choice: Choice, val branch: Branch)

/**
 * A `oneOf`, `anyOf` or `if` that navigation met [depth] pointer tokens down, read under [baseUri]: [node] is the
 * `oneOf`/`anyOf` list or the `if` condition.  [openBranches] are the branches the document hasn't ruled out.
 *
 * Equal choices are one choice, as a value takes the same branch of each: equal `if` conditions in two `allOf`
 * members decide alike.
 */
internal data class Choice(val node: KsonValue, val baseUri: String, val depth: Int, val openBranches: List<Branch>)

internal sealed interface Branch {
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
     * - For array indices: navigates to the item schema validation applies at that index (see [itemSchema])
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
     * The document is walked on its AST, the tree [documentPointer] was built on (see [TreePointer]).
     * A token reads the same for a list index and a property named by a number, so the node it is
     * read in tells which it is (see [arrayIndex]).  The node reached at each level is converted with
     * [toKsonValueOrNull] to narrow by, so whatever did parse in a broken document still narrows.
     *
     * @param documentPointer Pointer through the document's AST (e.g. from [KsonValuePathBuilder])
     * @param documentAst Root of the document's AST, walked along [documentPointer]; without it, a number is
     *   taken as an index and no branch is ruled out
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
            rootResolved.resolvedValueBaseUri
        )

        // the bare tokens step the schema by name; the document is walked along the tagged pointer
        val tokens = documentPointer.pointer.tokens
        val docNodes = documentAst?.let { AstNodeWalker.nodesAlong(it, documentPointer) }.orEmpty()
        // the node each token is read in: the root, then the node the token before it stepped to
        val containers = listOfNotNull(documentAst) + docNodes
        var current = flatten(rootRef, documentAst?.toKsonValueOrNull(), depth = 0)

        for ((index, token) in tokens.withIndex()) {
            val arrayIndex = arrayIndex(token, containers.getOrNull(index))
            val stepped = current.flatMap { stepInto(it, token, arrayIndex) }
            val docValue = docNodes.getOrNull(index)?.toKsonValueOrNull()
            current = stepped.flatMap { flatten(it, docValue, depth = index + 1) }
            if (current.isEmpty()) break
        }

        return current
    }

    /**
     * The list index [token] stands for, or null where it names a property.  In an object [container], a token names
     * a property even when it is a number.  Anywhere else a number is an index, including where the document has no
     * node to read the token in, as past what it holds.
     */
    private fun arrayIndex(token: String, container: AstNode?): Int? =
        if (container?.let(AstNodeWalker::getChildren) is NodeChildren.Object) null else token.toIntOrNull()

    /**
     * Structural step by one pointer token.  Looks at properties / patternProperties /
     * additionalProperties (for names) or at the item schema validation applies at the list
     * index [arrayIndex] (see [itemSchema]).  No combinator / conditional logic — [flatten]
     * handles branching.
     *
     * Applies `$id` on [ref] to the base URI before property lookup, and resolves `$ref`
     * on the stepped-into schema.
     */
    private fun stepInto(ref: NavigatedSchema, token: String, arrayIndex: Int?): List<NavigatedSchema> {
        val schemaObj = ref.resolvedValue as? KsonObject ?: return emptyList()
        val updatedBaseUri = baseUriWithin(schemaObj, ref.resolvedValueBaseUri)

        val stepped = mutableListOf<KsonValue>()

        if (arrayIndex != null) {
            itemSchema(schemaObj, arrayIndex)?.let {
                stepped.add(it)
            }
        } else {
            val properties = schemaObj.propertyLookup["properties"] as? KsonObject
            properties?.propertyMap?.get(token)?.let {
                stepped.add(it.propValue)
            }

            val patternProperties = schemaObj.propertyLookup["patternProperties"] as? KsonObject
            addPatternPropertyMatches(patternProperties, token, stepped)

            if (stepped.isEmpty()) {
                schemaObj.propertyLookup["additionalProperties"]?.let {
                    stepped.add(it)
                }
            }
        }

        return stepped.map { value ->
            val resolved = idLookup.resolveRefIfPresent(value, updatedBaseUri)
            NavigatedSchema(
                resolved.resolvedValue,
                resolved.resolvedValueBaseUri,
                ref.branchTrail
            )
        }
    }

    /**
     * The schema validation applies to the item at [index] of an array [arraySchema] describes: the tuple's own
     * schema for an index the tuple covers, `additionalItems` past it, or a single `items` schema at any index.  Null
     * when none applies, as without `items`.
     *
     * Validation applies the same rule in [org.kson.schema.validators.ItemsValidator].
     */
    private fun itemSchema(arraySchema: KsonObject, index: Int): KsonValue? {
        val items = arraySchema.propertyLookup["items"]
        val tuple = items as? KsonList ?: return items
        return tuple.elements.getOrNull(index) ?: arraySchema.propertyLookup["additionalItems"]
    }

    /** The base URI that `$ref`s in [schema] resolve against. */
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
        stepped: MutableList<KsonValue>
    ) {
        patternProperties?.propertyMap?.forEach { (pattern, property) ->
            try {
                if (Regex(pattern).containsMatchIn(token)) {
                    stepped.add(property.propValue)
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

        fun addBranch(resolved: ResolvedRef, step: BranchStep?) {
            val branchRef = NavigatedSchema(
                resolved.resolvedValue,
                resolved.resolvedValueBaseUri,
                ref.branchTrail + listOfNotNull(step)
            )
            results.addAll(flatten(branchRef, docVal, depth, inProgress))
            addedBranches = true
        }

        for (alternatives in listOf("oneOf", "anyOf").mapNotNull { schemaObj.propertyLookup[it] as? KsonList }) {
            narrowedBranches(alternatives, baseUri, docVal, depth).forEach { (branch, step) -> addBranch(branch, step) }
            // narrowing still happened even if every branch was dropped
            if (alternatives.elements.isNotEmpty()) addedBranches = true
        }

        (schemaObj.propertyLookup["allOf"] as? KsonList)?.elements?.forEach { branch ->
            addBranch(idLookup.resolveRefIfPresent(branch, baseUri), step = null)
        }

        conditionalBranches(schemaObj, baseUri, docVal, depth).forEach { (branch, step) ->
            addBranch(idLookup.resolveRefIfPresent(branch, baseUri), step)
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
     * The branches of [alternatives], a `oneOf` or `anyOf` list, each with the step taking it, narrowed against the
     * document at this level: a branch is dropped when it contradicts [docVal] (e.g. a discriminating sibling
     * property).
     */
    private fun narrowedBranches(
        alternatives: KsonList,
        baseUri: String,
        docVal: KsonValue?,
        depth: Int
    ): List<Pair<ResolvedRef, BranchStep>> {
        val branches = alternatives.elements.map { idLookup.resolveRefIfPresent(it, baseUri) }
        val openBranches = branches.indices
            .filter { docVal == null || isCompatibleWithDocument(branches[it], docVal) }
            .map { Branch.Index(it) }
        val choice = Choice(alternatives, baseUri, depth, openBranches)
        return openBranches.map { branches[it.index] to BranchStep(choice, it) }
    }

    /**
     * The `then`/`else` branches an `if` conditional contributes, per [evaluateIf] against [docVal]:
     * a matching `if` yields `then`; a contradicted `if` yields `else`; an undecidable `if` yields both.
     * Empty when there is no `if`.  Ordering (`then` before `else`) matches the flattened result order.
     *
     * Each step lists the outcomes left open, even one whose keyword the schema leaves out: the document
     * may still take it, and it constrains nothing.
     */
    private fun conditionalBranches(
        schemaObj: KsonObject,
        baseUri: String,
        docVal: KsonValue?,
        depth: Int
    ): List<Pair<KsonValue, BranchStep>> {
        val ifCondition = schemaObj.propertyLookup["if"] ?: return emptyList()
        val openBranches = when (evaluateIf(ifCondition, baseUri, docVal)) {
            IfState.MATCH -> listOf(Branch.Then)
            IfState.NO_MATCH -> listOf(Branch.Else)
            IfState.UNDETERMINED -> listOf(Branch.Then, Branch.Else)
        }
        val choice = Choice(ifCondition, baseUri, depth, openBranches)
        val thenBranch = schemaObj.propertyLookup["then"]?.takeIf { Branch.Then in openBranches }
            ?.let { it to BranchStep(choice, Branch.Then) }
        val elseBranch = schemaObj.propertyLookup["else"]?.takeIf { Branch.Else in openBranches }
            ?.let { it to BranchStep(choice, Branch.Else) }
        return listOfNotNull(thenBranch, elseBranch)
    }

    /**
     * Whether [docVal] does not contradict [ref]'s schema: it either satisfies the schema already or
     * could be made to satisfy it through additive edits, such as adding a missing required property
     * or finishing the value still being typed.
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
