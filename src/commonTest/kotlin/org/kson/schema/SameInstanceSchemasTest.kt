package org.kson.schema

import org.kson.KsonCore
import org.kson.value.KsonValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SameInstanceSchemasTest {
    /** Each call parses a new value, equal to the ones before */
    private fun schema(): KsonValue = KsonCore.parseToAst("{ minimum: 1 }").ksonValue!!

    @Test
    fun aSchemaIsAmongThemButAnEqualOneElsewhereIsNot() {
        val schema = schema()
        val equalSchema = schema()
        val schemas = SameInstanceSchemas.NONE.with(schema)

        assertEquals(schema, equalSchema)
        assertTrue(schema in schemas)
        assertFalse(equalSchema in schemas)
    }

    @Test
    fun addingASchemaKeepsTheOthersWithoutChangingThem() {
        val outer = schema()
        val inner = schema()
        val withOuter = SameInstanceSchemas.NONE.with(outer)
        val withBoth = withOuter.with(inner)

        assertTrue(outer in withBoth)
        assertTrue(inner in withBoth)
        assertFalse(inner in withOuter)
    }

    @Test
    fun onlyASubschemaOfTheSameInstanceStartsWithTheSchemas() {
        val schema = schema()
        val schemas = SameInstanceSchemas.NONE.with(schema)

        assertTrue(schema in schemas.into(Applies.Negated))
        assertFalse(schema in schemas.into(Applies.Property("name")))
    }
}
