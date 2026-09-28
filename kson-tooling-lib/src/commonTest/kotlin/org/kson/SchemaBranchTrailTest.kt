package org.kson

import org.kson.schema.SchemaIdLookup
import org.kson.tooling.KsonTooling
import org.kson.tooling.navigation.Branch
import org.kson.tooling.navigation.BranchStep
import org.kson.tooling.navigation.SchemaNavigator
import org.kson.value.KsonList
import org.kson.value.KsonObject
import org.kson.value.KsonString
import org.kson.value.KsonValue
import org.kson.value.navigation.json_pointer.JsonPointer
import org.kson.walker.TreePointer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class SchemaBranchTrailTest {

    private fun trails(schema: String, path: List<String>, document: String? = null): List<String> {
        val schemaValue = KsonCore.parseToAst(schema).ksonValue ?: fail("Schema should parse")
        val navigated = SchemaNavigator(SchemaIdLookup(schemaValue)).navigate(
            TreePointer(JsonPointer.fromTokens(path)),
            document?.let { KsonTooling.parse(it).rootAstNode }
        )
        return navigated.map { schemaTrail ->
            val type = ((schemaTrail.resolvedValue as? KsonObject)?.propertyLookup?.get("type") as? KsonString)?.value
            val label = type ?: "untyped"
            val steps = schemaTrail.branchTrail.map { describe(it, schemaValue) }
            if (steps.isEmpty()) label else "$label via ${steps.joinToString(", then ")}"
        }
    }

    private fun describe(step: BranchStep, schemaValue: KsonValue): String {
        val node = pointerTo(step.choice.node, schemaValue) ?: fail("choice not in the schema")
        return "$node took ${describe(step.branch)} at depth ${step.choice.depth}"
    }

    private fun describe(branch: Branch): String = when (branch) {
        is Branch.Index -> branch.index.toString()
        Branch.Then -> "then"
        Branch.Else -> "else"
    }

    private fun pointerTo(node: KsonValue, root: KsonValue, path: String = ""): String? = when {
        root === node -> path
        root is KsonObject -> root.propertyLookup.firstNotNullOfOrNull { (key, value) -> pointerTo(node, value, "$path/$key") }
        root is KsonList -> root.elements.withIndex().firstNotNullOfOrNull { (index, value) -> pointerTo(node, value, "$path/$index") }
        else -> null
    }

    @Test
    fun nestedAlternativesAreListedOutermostFirst() {
        val schema = """
            {
                "oneOf": [
                    { "properties": { "p": { "anyOf": [ { "type": "string" }, { "type": "object" } ] } } },
                    { "properties": { "p": { "type": "array" } } }
                ]
            }
        """
        assertEquals(
            listOf(
                "untyped via /oneOf took 0 at depth 0",
                "string via /oneOf took 0 at depth 0, then /oneOf/0/properties/p/anyOf took 0 at depth 1",
                "object via /oneOf took 0 at depth 0, then /oneOf/0/properties/p/anyOf took 1 at depth 1",
                "array via /oneOf took 1 at depth 0"
            ),
            trails(schema, listOf("p"))
        )
    }

    @Test
    fun propertyAndItemStepsKeepTheTrail() {
        val schema = """
            {
                "anyOf": [
                    { "properties": { "list": { "items": { "properties": { "p": { "type": "string" } } } } } },
                    {}
                ]
            }
        """
        assertEquals(listOf("string via /anyOf took 0 at depth 0"), trails(schema, listOf("list", "0", "p")))
    }

    @Test
    fun allOfAndRefAddNoStep() {
        val schema = """
            {
                "${'$'}defs": { "base": { "allOf": [ { "properties": { "p": { "type": "string" } } } ] } },
                "anyOf": [ { "${'$'}ref": "#/${'$'}defs/base" }, { "required": ["q"] } ]
            }
        """
        assertEquals(listOf("string via /anyOf took 0 at depth 0"), trails(schema, listOf("p")))
    }

    @Test
    fun ifRecordsEachOutcomeItTakes() {
        val schema = """
            {
                "if": { "properties": { "kind": { "const": "a" } }, "required": ["kind"] },
                "then": { "properties": { "p": { "type": "string" } } },
                "else": { "properties": { "p": { "type": "object" } } }
            }
        """
        assertEquals(listOf("string via /if took then at depth 0"), trails(schema, listOf("p"), "kind: a"))
        assertEquals(listOf("object via /if took else at depth 0"), trails(schema, listOf("p"), "kind: b"))
        assertEquals(
            listOf("string via /if took then at depth 0", "object via /if took else at depth 0"),
            trails(schema, listOf("p"))
        )
    }

    @Test
    fun alternativeReachedAgainFurtherDownIsAnotherChoice() {
        val schema = """
            {
                "${'$'}defs": {
                    "node": {
                        "anyOf": [
                            { "properties": { "child": { "type": "object" } } },
                            { "properties": { "child": { "type": "string" } } }
                        ],
                        "properties": { "child": { "${'$'}ref": "#/${'$'}defs/node" } }
                    }
                },
                "${'$'}ref": "#/${'$'}defs/node"
            }
        """
        assertEquals(
            listOf(
                "untyped",
                "untyped via /\$defs/node/anyOf took 0 at depth 1",
                "untyped via /\$defs/node/anyOf took 1 at depth 1",
                "object via /\$defs/node/anyOf took 0 at depth 0",
                "string via /\$defs/node/anyOf took 1 at depth 0"
            ),
            trails(schema, listOf("child"))
        )
    }
}
