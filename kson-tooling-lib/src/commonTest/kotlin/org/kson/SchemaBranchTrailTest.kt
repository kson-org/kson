package org.kson

import org.kson.schema.SchemaIdLookup
import org.kson.tooling.KsonTooling
import org.kson.tooling.navigation.Branch
import org.kson.tooling.navigation.BranchStep
import org.kson.tooling.navigation.SchemaNavigator
import org.kson.value.KsonBoolean
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

    private fun trails(
        schema: String,
        path: List<String>,
        document: String? = null,
        toNewProperty: Boolean = false
    ): List<String> {
        val schemaValue = KsonCore.parseToAst(schema).ksonValue ?: fail("Schema should parse")
        val navigated = SchemaNavigator(SchemaIdLookup(schemaValue), toNewProperty = toNewProperty).navigate(
            TreePointer(JsonPointer.fromTokens(path)),
            document?.let { KsonTooling.parse(it).rootAstNode }
        )
        return navigated.map { schemaTrail ->
            val value = schemaTrail.resolvedValue
            val type = ((value as? KsonObject)?.propertyLookup?.get("type") as? KsonString)?.value
            val label = if (value is KsonBoolean) value.value.toString() else type ?: "untyped"
            val steps = schemaTrail.branchTrail.map { describe(it, schemaValue) }
            if (steps.isEmpty()) label else "$label via ${steps.joinToString(", then ")}"
        }
    }

    private fun describe(step: BranchStep, schemaValue: KsonValue): String {
        val alternative = pointerTo(step.alternative, schemaValue) ?: fail("alternative not in the schema")
        return "$alternative took ${describe(step.branch)} at depth ${step.depth}"
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

    @Test
    fun branchForbiddingThePathIsCarriedToANewProperty() {
        val schema = """
            {
                "anyOf": [
                    { "properties": { "a": { "properties": { "b": { "type": "string" } } } } },
                    { "additionalProperties": false }
                ]
            }
        """
        assertEquals(listOf("string via /anyOf took 0 at depth 0"), trails(schema, listOf("a", "b")))
        assertEquals(
            listOf("string via /anyOf took 0 at depth 0", "false via /anyOf took 1 at depth 0"),
            trails(schema, listOf("a", "b"), toNewProperty = true)
        )
    }

    @Test
    fun itemOfANewPropertyStepsToItsOwnSchema() {
        val schema = """
            {
                "items": [ { "properties": { "a": { "type": "string" } } } ],
                "additionalItems": { "properties": { "a": { "type": "object" } } }
            }
        """
        assertEquals(listOf("object"), trails(schema, listOf("0", "a"), "[{}]"), "default navigation ignores the tuple")
        assertEquals(listOf("string"), trails(schema, listOf("0", "a"), "[{}]", toNewProperty = true))
        assertEquals(listOf("object"), trails(schema, listOf("1", "a"), "[{}, {}]", toNewProperty = true))
    }

    @Test
    fun documentRulesOutNoBranchOnTheWayToANewProperty() {
        val schema = """
            {
                "oneOf": [
                    { "properties": { "kind": { "const": "a" }, "p": { "type": "string" } } },
                    { "properties": { "kind": { "const": "b" }, "p": { "type": "object" } } }
                ]
            }
        """
        assertEquals(listOf("object via /oneOf took 1 at depth 0"), trails(schema, listOf("p"), "kind: b"))
        assertEquals(
            listOf("string via /oneOf took 0 at depth 0", "object via /oneOf took 1 at depth 0"),
            trails(schema, listOf("p"), "kind: b", toNewProperty = true)
        )
    }

    @Test
    fun documentDecidesNoIfOnTheWayToANewProperty() {
        val schema = """
            {
                "properties": {
                    "settings": {
                        "if": { "properties": { "kind": { "const": "a" } }, "required": ["kind"] },
                        "then": { "properties": { "p": { "type": "string" } } },
                        "else": { "properties": { "p": { "type": "object" } } }
                    }
                }
            }
        """
        assertEquals(
            listOf("string via /properties/settings/if took then at depth 1"),
            trails(schema, listOf("settings", "p"), "settings: {kind: a}")
        )
        assertEquals(
            listOf(
                "string via /properties/settings/if took then at depth 1",
                "object via /properties/settings/if took else at depth 1"
            ),
            trails(schema, listOf("settings", "p"), "settings: {kind: a}", toNewProperty = true)
        )
    }

    @Test
    fun propertyNamedByANumberGivesANewPropertyNoSchema() {
        val schema = """
            {
                "properties": { "0": { "properties": { "a": { "type": "object" } } } },
                "items": { "properties": { "a": { "type": "string" } } }
            }
        """
        assertEquals(listOf("string"), trails(schema, listOf("0", "a"), "'0': {}"), "default navigation steps a numeric name as an index")
        assertEquals(emptyList(), trails(schema, listOf("0", "a"), "'0': {}", toNewProperty = true))
    }

    @Test
    fun refThatNeverResolvesGivesANewPropertyNoSchema() {
        val schema = """
            {
                "properties": { "p": { "${'$'}ref": "#/${'$'}defs/loop" } },
                "${'$'}defs": {
                    "loop": { "${'$'}ref": "#/${'$'}defs/loop", "type": "object", "properties": { "x": { "type": "string" } } }
                }
            }
        """
        assertEquals(listOf("object"), trails(schema, listOf("p")), "default navigation reads keywords beside an unresolved \$ref")
        assertEquals(emptyList(), trails(schema, listOf("p"), toNewProperty = true))
        assertEquals(listOf("string"), trails(schema, listOf("p", "x")), "default navigation reads keywords beside an unresolved \$ref")
        assertEquals(emptyList(), trails(schema, listOf("p", "x"), toNewProperty = true))
    }
}
