package org.kson

import org.kson.schema.SchemaIdLookup
import org.kson.tooling.navigation.NavigatedSchema
import org.kson.tooling.navigation.SchemaNavigator
import org.kson.tooling.navigation.SchemaResolutionType
import org.kson.tooling.navigation.SchemaResolutionType.ALL_OF
import org.kson.tooling.navigation.SchemaResolutionType.ANY_OF
import org.kson.tooling.navigation.SchemaResolutionType.IF_ELSE
import org.kson.tooling.navigation.SchemaResolutionType.IF_THEN
import org.kson.tooling.navigation.SchemaResolutionType.ONE_OF
import org.kson.value.KsonObject
import org.kson.value.KsonString
import org.kson.value.navigation.json_pointer.JsonPointer
import org.kson.walker.TreePointer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * A schema reached through an alternative (oneOf, anyOf, if/then, if/else) only applies when the
 * document takes that branch, so navigation must mark it with that alternative, including when an
 * allOf sits between the alternative and the schema.
 */
class SchemaAlternativeMarkingTest {

    private fun navigate(schema: String, vararg path: String): List<NavigatedSchema> {
        val parsedSchema = KsonCore.parseToAst(schema).ksonValue ?: fail("Schema should parse")
        return SchemaNavigator(SchemaIdLookup(parsedSchema)).navigate(TreePointer(JsonPointer.fromTokens(path.toList())))
    }

    private fun declaredType(schema: NavigatedSchema): String? =
        ((schema.resolvedValue as? KsonObject)?.propertyLookup?.get("type") as? KsonString)?.value

    /** Asserts the single navigated schema declaring `type: [type]` carries the [expected] mark. */
    private fun assertMark(expected: SchemaResolutionType, navigated: List<NavigatedSchema>, type: String) {
        val summary = navigated.map { "${declaredType(it) ?: "-"}:${it.resolutionType}" }
        val schema = navigated.singleOrNull { declaredType(it) == type }
            ?: fail("expected one `$type` schema, navigated: $summary")
        assertEquals(expected, schema.resolutionType, "mark of the `$type` schema; navigated: $summary")
    }

    // An allOf nested in an alternative, at the navigated property

    @Test
    fun testAllOfInsideAnyOfAtTheProperty() {
        val schema = """
            {
                "properties": {
                    "p": { "anyOf": [ { "allOf": [ { "type": "array" } ] }, {} ] }
                }
            }
        """
        assertMark(ANY_OF, navigate(schema, "p"), "array")
    }

    @Test
    fun testAllOfInsideIfThenAtTheProperty() {
        val schema = """
            {
                "properties": {
                    "p": {
                        "if": { "minProperties": 1 },
                        "then": { "allOf": [ { "type": "object" } ] }
                    }
                }
            }
        """
        assertMark(IF_THEN, navigate(schema, "p"), "object")
    }

    @Test
    fun testAllOfInsideAllOfInsideAnyOfAtTheProperty() {
        val schema = """
            {
                "properties": {
                    "p": { "anyOf": [ { "allOf": [ { "allOf": [ { "type": "array" } ] } ] }, {} ] }
                }
            }
        """
        assertMark(ANY_OF, navigate(schema, "p"), "array")
    }

    // An allOf nested in an alternative, above the navigated property

    @Test
    fun testAllOfInsideAnyOfAboveTheProperty() {
        val schema = """
            {
                "anyOf": [
                    { "allOf": [ { "properties": { "p": { "type": "array" } } } ] },
                    {}
                ]
            }
        """
        assertMark(ANY_OF, navigate(schema, "p"), "array")
    }

    @Test
    fun testAllOfInsideOneOfAboveTheProperty() {
        val schema = """
            {
                "oneOf": [
                    { "allOf": [ { "properties": { "p": { "type": "string" } } } ] },
                    { "required": ["q"] }
                ]
            }
        """
        assertMark(ONE_OF, navigate(schema, "p"), "string")
    }

    @Test
    fun testAllOfInsideIfThenAboveTheProperty() {
        val schema = """
            {
                "if": { "properties": { "kind": { "const": "a" } } },
                "then": { "allOf": [ { "properties": { "p": { "type": "object" } } } ] }
            }
        """
        assertMark(IF_THEN, navigate(schema, "p"), "object")
    }

    @Test
    fun testAllOfInsideIfElseAboveTheProperty() {
        val schema = """
            {
                "if": { "properties": { "kind": { "const": "a" } } },
                "then": {},
                "else": { "allOf": [ { "properties": { "p": { "type": "object" } } } ] }
            }
        """
        assertMark(IF_ELSE, navigate(schema, "p"), "object")
    }

    @Test
    fun testAllOfInsideAnyOfTwoLevelsAboveTheProperty() {
        val schema = """
            {
                "anyOf": [
                    { "properties": { "a": { "allOf": [ { "properties": { "b": { "type": "object" } } } ] } } },
                    {}
                ]
            }
        """
        assertMark(ANY_OF, navigate(schema, "a", "b"), "object")
    }

    // An allOf outside any alternative holds unconditionally, so it stays ALL_OF

    @Test
    fun testAllOfAtThePropertyIsNotAnAlternative() {
        val schema = """
            {
                "properties": {
                    "p": { "allOf": [ { "type": "array" } ] }
                }
            }
        """
        assertMark(ALL_OF, navigate(schema, "p"), "array")
    }

    @Test
    fun testAllOfAboveThePropertyIsNotAnAlternative() {
        val schema = """
            {
                "allOf": [ { "properties": { "p": { "type": "array" } } } ]
            }
        """
        assertMark(ALL_OF, navigate(schema, "p"), "array")
    }
}
