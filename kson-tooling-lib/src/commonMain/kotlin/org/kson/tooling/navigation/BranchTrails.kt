package org.kson.tooling.navigation

import org.kson.value.KsonValue

/**
 * What a value may be, given these schemas, all navigated to it.  [allowedBy] says what one schema allows, or null
 * when it leaves the value free; a `false` schema allows nothing, whatever [allowedBy] makes of it.
 *
 * The schemas' [branch trails][NavigatedSchema.branchTrail] say how they combine: the value must satisfy every schema
 * that applies without a further choice, so what those allow is intersected, and one
 * [open branch][Choice.openBranches] of each choice, so what a choice's branches allow is unioned.  An open branch
 * no schema came through is silent on the value, leaving its choice allowing anything.
 *
 * @return what the value may be, or null when nothing restricts it
 */
internal fun List<NavigatedSchema>.allowedAlongTrails(allowedBy: (KsonValue) -> Set<String>?): Set<String>? {
    fun allowedByOne(schema: KsonValue): Set<String>? = if (schema.isFalseSchema) emptySet() else allowedBy(schema)

    // [schemas] took the same branches for the first [trailIndex] steps of their trails
    fun allowed(schemas: List<NavigatedSchema>, trailIndex: Int): Set<String>? {
        val (unconditional, underChoices) = schemas.partition { it.branchTrail.size == trailIndex }
        val allowedPerChoice = groupByChoice(underChoices, trailIndex).map { (choice, group) ->
            choice.openBranches
                .map { branch -> group.filter { it.branchTrail[trailIndex].branch == branch } }
                .map { onBranch -> if (onBranch.isEmpty()) null else allowed(onBranch, trailIndex + 1) }
                .reduce(::union)
        }
        return (unconditional.map { allowedByOne(it.resolvedValue) } + allowedPerChoice).reduceOrNull(::intersect)
    }
    return allowed(this, trailIndex = 0)
}

// Compares choices for equality rather than grouping by hash, as hashing a schema node walks all of it
private fun groupByChoice(schemas: List<NavigatedSchema>, trailIndex: Int): List<Pair<Choice, List<NavigatedSchema>>> {
    val groups = mutableListOf<Pair<Choice, MutableList<NavigatedSchema>>>()
    for (schema in schemas) {
        val choice = schema.branchTrail[trailIndex].choice
        groups.find { it.first == choice }?.second?.add(schema)
            ?: groups.add(choice to mutableListOf(schema))
    }
    return groups
}

// null allows anything, so it absorbs a union and drops out of an intersection
private fun union(a: Set<String>?, b: Set<String>?): Set<String>? = if (a == null || b == null) null else a + b

private fun intersect(a: Set<String>?, b: Set<String>?): Set<String>? = when {
    a == null -> b
    b == null -> a
    else -> a intersect b
}
