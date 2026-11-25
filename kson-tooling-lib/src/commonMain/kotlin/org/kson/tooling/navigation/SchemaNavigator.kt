package org.kson.tooling.navigation

import org.kson.ast.AstNode
import org.kson.ast.ObjectNode
import org.kson.parser.Location
import org.kson.schema.Applies
import org.kson.schema.IndexedSchemaDocuments
import org.kson.schema.SchemaChoice
import org.kson.schema.Subschema
import org.kson.schema.SchemaNode
import org.kson.schema.SchemaProgress
import org.kson.value.KsonBoolean
import org.kson.value.KsonObject
import org.kson.value.KsonValue
import org.kson.value.navigation.json_pointer.JsonPointer
import org.kson.value.toKsonValueOrNull
import org.kson.walker.AstNodeWalker
import org.kson.walker.TreePointer
import org.kson.walker.navigate
import org.kson.walker.nodesAlong

/** A [schema] found by navigation, with the branches taken to reach it. */
internal data class NavigatedSchema(val schema: SchemaNode, val branchTrail: List<BranchStep> = emptyList()) {
    val value: KsonValue get() = schema.value
    val endsAtUnresolvedRef: Boolean get() = schema.endsAtUnresolvedRef
    val propertySubschemas: PropertySubschemas by lazy { PropertySubschemas(schema.subschemas) }
}

/** The subschemas of a schema that validate the instance's properties. */
internal class PropertySubschemas(subschemas: List<Subschema>) {
    val byName: Map<String, List<Subschema>> =
        subschemas.filter { it.applies is Applies.Property }.groupBy { (it.applies as Applies.Property).name }
    val byPattern: List<Subschema> = subschemas.filter { it.applies is Applies.PatternProperty }
    val forOthers: List<Subschema> = subschemas.filter { it.applies == Applies.OtherProperties }
}

/** Navigation took [branch] of [choice]. */
internal data class BranchStep(val choice: Choice, val branch: Branch)

/**
 * A `oneOf`, `anyOf` or `if` that navigation met [depth] pointer tokens down.  [openBranches] are the branches the
 * document hasn't ruled out.
 *
 * Equal choices are one choice, as a value takes the same branch of each: equal `if` conditions in two `allOf`
 * members decide alike.
 */
internal data class Choice(
    val schemaChoice: SchemaChoice,
    val depth: Int,
    val openBranches: List<Branch>
)

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
 * [evaluateIf] against the document).
 *
 * The navigator is built on two primitives:
 * - [stepInto]: structural per-token descent through a single schema, no combinator
 *   awareness.
 * - [flatten]: doc-aware decomposition of a schema's top-level branches.
 *
 * Both choose among the [subschemas][SchemaNode.subschemas] that core returns for a schema, by the part of the
 * instance each validates.
 * Every level applies `flatten` before stepping, including the root and the target,
 * so no post-navigation expansion pass is needed.
 *
 * Mirrors the shape of `TreeNavigator` in the walker package (see
 * [org.kson.walker.navigate]) — one entry point, small internal
 * helpers — so the pattern is recognizable.
 *
 * With [carryDeadEnds], navigation carries a dead end to the result as itself: a `false` schema, which allows nothing
 * below it, or a schema [ending at an unresolved `$ref`][NavigatedSchema.endsAtUnresolvedRef], whose keywords it
 * leaves unread.  Without it, nothing is found below a `false` schema, and the keywords beside an unresolved `$ref`
 * are read, as hover and completions expect.
 *
 * With [narrow], branches the document contradicts are dropped: a `oneOf` or `anyOf` branch it doesn't fit, and
 * the `then` or `else` its `if` rules out.  Without it, every branch is kept, as for an empty document.
 */
internal class SchemaNavigator(
    private val indexedDocuments: IndexedSchemaDocuments,
    private val incompleteRegion: Location? = null,
    private val carryDeadEnds: Boolean = false,
    private val narrow: Boolean = true
) {

    /**
     * Navigate schema by document path tokens.
     *
     * This function translates document paths to schema paths:
     * - For object properties: navigates to the property's subschemas under `properties` and `patternProperties`
     * - For array indices: navigates to the item schema validation applies at that index (see [itemSchema])
     * - Falls back to "additionalProperties" when neither `properties` nor `patternProperties` matches the property
     * - Resolves `$ref` references to their target schemas
     * - Handles combinators (allOf, anyOf, oneOf) and conditionals (if/then/else), flattening
     *   them at every level so callers receive fully decomposed branches
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
     * val indexedDocuments = IndexedSchemaDocuments(SchemaDocument(schemaRoot))
     * val schemas = SchemaNavigator(indexedDocuments).navigate(TreePointer(JsonPointer.fromTokens(listOf("users", "0", "name"))))
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
     * @param documentAst Root of the document's AST, walked along [documentPointer]
     * @return List of [NavigatedSchema] containing all sub-schemas at that location (empty if not found)
     */
    fun navigate(
        documentPointer: TreePointer<AstNode>,
        documentAst: AstNode? = null
    ): List<NavigatedSchema> {
        val root = NavigatedSchema(indexedDocuments.resolvedRootSchema)

        // the bare tokens step the schema by name; the document is walked along the tagged pointer
        val tokens = documentPointer.pointer.tokens
        val docNodes = documentAst?.let { AstNodeWalker.nodesAlong(it, documentPointer) }.orEmpty()
        // the node each token is read in: the root, then the node the token before it stepped to
        val containers = listOfNotNull(documentAst) + docNodes
        var current = flatten(root, documentAst?.takeIf { narrow }?.toKsonValueOrNull(), depth = 0)

        for ((index, token) in tokens.withIndex()) {
            current = step(current, token, containers.getOrNull(index), docNodes.getOrNull(index), depth = index + 1)
            if (current.isEmpty()) break
        }

        return current
    }

    /**
     * Returns, for each of [names], what [navigate] finds at that property of the object at [objectPointer].  The
     * object is navigated to once, not once per name.
     */
    fun navigateToProperties(
        objectPointer: TreePointer<AstNode>,
        names: List<String>,
        documentAst: AstNode? = null
    ): Map<String, List<NavigatedSchema>> {
        val objectSchemas = navigate(objectPointer, documentAst)
        val objectNode = documentAst?.let { AstNodeWalker.navigate(it, objectPointer) }
        val depth = objectPointer.pointer.tokens.size + 1
        return names.associateWith { name ->
            val propertyNode = documentAst?.let { AstNodeWalker.navigate(it, objectPointer.child(name)) }
            step(objectSchemas, name, objectNode, propertyNode, depth)
        }
    }

    /**
     * Returns the schemas one pointer token below [schemas].  The token is a list index if it is a number and
     * [container] is not an object, and a property name otherwise.
     */
    private fun step(
        schemas: List<NavigatedSchema>,
        token: String,
        container: AstNode?,
        node: AstNode?,
        depth: Int
    ): List<NavigatedSchema> {
        val arrayIndex = arrayIndex(token, container)
        val docValue = node?.takeIf { narrow }?.toKsonValueOrNull()
        return schemas.flatMap { stepInto(it, token, arrayIndex) }.flatMap { flatten(it, docValue, depth) }
    }

    /**
     * Returns the list index [token] names, or null when [token] is a property name.  In an object [container] every
     * token is a property name; anywhere else, even without a [container], [JsonPointer.arrayIndex] decides.
     */
    private fun arrayIndex(token: String, container: AstNode?): Int? =
        if (container is ObjectNode) null else JsonPointer.arrayIndex(token)

    /** Returns the subschema among [subschemas] that validates the list item at [index], or null if none does. */
    private fun itemSchema(subschemas: List<Subschema>, index: Int): Subschema? =
        subschemas.firstOrNull { (it.applies as? Applies.Item)?.index == index }
            ?: subschemas.firstOrNull { subschema ->
                (subschema.applies as? Applies.ItemsFrom)?.let { it.start <= index } == true
            }

    /** Returns the subschemas that validate the instance's property named [token]. */
    private fun propertySchemas(propertySubschemas: PropertySubschemas, token: String): List<Subschema> {
        val named = propertySubschemas.byName[token].orEmpty() +
            propertySubschemas.byPattern.filter { patternMatches((it.applies as Applies.PatternProperty).pattern, token) }
        return named.ifEmpty { propertySubschemas.forOthers }
    }

    /**
     * Structural step by one pointer token, to the subschemas that validate the list item at [arrayIndex], or the
     * property named [token] when [arrayIndex] is null.  No combinator / conditional logic — [flatten] handles
     * branching.
     */
    private fun stepInto(navigated: NavigatedSchema, token: String, arrayIndex: Int?): List<NavigatedSchema> {
        if (carryDeadEnds && (navigated.value.isFalseSchema || navigated.endsAtUnresolvedRef)) return listOf(navigated)
        val stepped = if (arrayIndex != null) {
            listOfNotNull(itemSchema(navigated.schema.subschemas, arrayIndex))
        } else {
            propertySchemas(navigated.propertySubschemas, token)
        }
        return stepped.map { NavigatedSchema(it.resolved, navigated.branchTrail) }
    }

    /**
     * Whether [pattern] matches [token].  An invalid regex matches nothing — Throwable also catches JavaScript
     * SyntaxError and other platform-specific errors.
     */
    private fun patternMatches(pattern: String, token: String): Boolean =
        try {
            Regex(pattern).containsMatchIn(token)
        } catch (_: Throwable) {
            false
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
     *     failed `if` emits `else`; an undecidable `if` (no document, unparseable
     *     condition, or failing only because a required discriminator isn't present yet)
     *     emits both branches.
     *
     * Recurses into each branch so nested combinators/conditionals are fully flattened,
     * each narrowed against the same [docVal].
     *
     * The parent [navigated] is preserved at the head of the result so its title, description,
     * and constraints remain available.
     */
    private fun flatten(
        navigated: NavigatedSchema,
        docVal: KsonValue?,
        depth: Int,
        inProgress: MutableList<KsonValue> = mutableListOf()
    ): List<NavigatedSchema> {
        if (carryDeadEnds && navigated.endsAtUnresolvedRef) return listOf(navigated)
        val schemaObj = navigated.value as? KsonObject ?: return listOf(navigated)

        // A combinator branch can `$ref` back to a node already being flattened on this path, and
        // expanding it consumes no pointer token, so stop at a node we're already inside.  A node's
        // value alone identifies it (see [SchemaNode.baseUri]).
        if (inProgress.any { it === schemaObj }) {
            return listOf(navigated)
        }
        inProgress.add(schemaObj)

        val subschemas = navigated.schema.subschemas
        val results = mutableListOf<NavigatedSchema>()
        var addedBranches = false

        fun addBranch(branch: SchemaNode, step: BranchStep?) {
            val navigatedBranch = NavigatedSchema(branch, navigated.branchTrail + listOfNotNull(step))
            results.addAll(flatten(navigatedBranch, docVal, depth, inProgress))
            addedBranches = true
        }

        for (alternatives in subschemas.filter { it.applies is Applies.Alternative }.groupBy { it.keyword }.values) {
            narrowedBranches(alternatives, docVal, depth).forEach { (branch, step) -> addBranch(branch, step) }
            // narrowing still happened even if every branch was dropped
            addedBranches = true
        }

        subschemas.filter { it.applies == Applies.InPlace }.forEach { addBranch(it.resolved, step = null) }

        conditionalBranches(subschemas, docVal, depth).forEach { (branch, step) -> addBranch(branch, step) }

        if (addedBranches) {
            results.add(0, navigated)
        } else {
            results.add(navigated)
        }

        inProgress.removeAt(inProgress.size - 1)
        return results
    }

    /**
     * Returns the branches in [alternatives], the subschemas of one `oneOf` or `anyOf`, each with the step that takes
     * it.  A branch is dropped when it contradicts [docVal], the document at this level (e.g. a discriminating
     * sibling property).
     */
    private fun narrowedBranches(
        alternatives: List<Subschema>,
        docVal: KsonValue?,
        depth: Int
    ): List<Pair<SchemaNode, BranchStep>> {
        val openBranches = alternatives
            .filter { docVal == null || isCompatibleWithDocument(it.resolved, docVal) }
            .associateWith { Branch.Index((it.applies as Applies.Alternative).index) }
        val choice = Choice(checkNotNull(alternatives.first().choice), depth, openBranches.values.toList())
        return openBranches.map { (subschema, branch) -> subschema.resolved to BranchStep(choice, branch) }
    }

    /**
     * The `then`/`else` branches an `if` conditional contributes, per [evaluateIf] against [docVal]:
     * a matching `if` yields `then`; a failed `if` yields `else`; an undecidable `if` yields both.
     * Empty when there is no `if`.  Ordering (`then` before `else`) matches the flattened result order.
     *
     * Each step lists the outcomes left open, even one whose keyword the schema leaves out: the document
     * may still take it, and it constrains nothing.
     */
    private fun conditionalBranches(
        subschemas: List<Subschema>,
        docVal: KsonValue?,
        depth: Int
    ): List<Pair<SchemaNode, BranchStep>> {
        val condition = subschemas.firstOrNull { it.applies == Applies.Condition } ?: return emptyList()
        val openBranches = when (evaluateIf(condition.resolved, docVal)) {
            IfState.MATCH -> listOf(Branch.Then)
            IfState.NO_MATCH -> listOf(Branch.Else)
            IfState.UNDETERMINED -> listOf(Branch.Then, Branch.Else)
        }
        val choice = Choice(checkNotNull(condition.choice), depth, openBranches)
        val thenBranch = subschemas.firstOrNull { it.applies == Applies.Then }?.takeIf { Branch.Then in openBranches }
            ?.let { it.resolved to BranchStep(choice, Branch.Then) }
        val elseBranch = subschemas.firstOrNull { it.applies == Applies.Else }?.takeIf { Branch.Else in openBranches }
            ?.let { it.resolved to BranchStep(choice, Branch.Else) }
        return listOfNotNull(thenBranch, elseBranch)
    }

    /**
     * Whether [docVal] does not contradict [schema]: it either satisfies the schema already or
     * could be made to satisfy it through additive edits, such as adding a missing required property
     * or finishing the value still being typed.
     */
    private fun isCompatibleWithDocument(
        schema: SchemaNode,
        docVal: KsonValue
    ): Boolean {
        val progress = SchemaProgress(schema)
        return !progress.rejects(docVal, forgiving = incompleteRegion)
    }

    /** Classify [ifCondition] against [docVal] into an [IfState]. */
    private fun evaluateIf(ifCondition: SchemaNode, docVal: KsonValue?): IfState {
        if (docVal == null) return IfState.UNDETERMINED
        val progress = SchemaProgress(ifCondition)
        if (progress.accepts(docVal)) return IfState.MATCH
        return if (progress.rejects(docVal, forgiving = incompleteRegion)) {
            IfState.NO_MATCH
        } else {
            IfState.UNDETERMINED
        }
    }

    /** Outcome of evaluating an `if` condition during [SchemaNavigator] flattening. */
    private enum class IfState {
        /** The document satisfies the condition, so `then` applies. */
        MATCH,

        /** The document contradicts the condition, so `else` applies. */
        NO_MATCH,

        /** The condition can't be decided yet, e.g. a discriminator isn't typed, so `then` and `else` both apply. */
        UNDETERMINED
    }
}
