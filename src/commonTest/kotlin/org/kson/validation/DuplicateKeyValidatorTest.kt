package org.kson.validation

import org.kson.KsonCore
import org.kson.parser.Coordinates
import org.kson.parser.Location
import org.kson.parser.messages.MessageType.OBJECT_DUPLICATE_KEY
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DuplicateKeyValidatorTest {

    @Test
    fun testNoDuplicateKeys() {
        val source = """
            key1: value1
            key2: value2
            key3: value3
        """.trimIndent()

        val result = KsonCore.parseToAst(source)
        assertTrue(result.messages.isEmpty(), "Should have no errors for unique keys")
    }

    @Test
    fun testSingleDuplicateKey() {
        val source = """
            key1: value1
            key2: value2
            key1: value3
        """.trimIndent()

        val result = KsonCore.parseToAst(source)
        assertEquals(1, result.messages.size, "Should have one error for duplicate key")

        val error = result.messages.first()
        assertEquals(OBJECT_DUPLICATE_KEY, error.message.type)
        assertEquals(Location(Coordinates(2,0), Coordinates(2,4),26,30), error.location)
    }

    @Test
    fun testMultipleDuplicateKeys() {
        val source = """
            key1: value1
            key2: value2
            key1: value3
            key2: value4
            key3: value5
        """.trimIndent()

        val result = KsonCore.parseToAst(source)
        assertEquals(2, result.messages.size, "Should have two errors for two duplicate keys")

        val errors = result.messages
        assertTrue(errors.map { it.message }.all { it.type == OBJECT_DUPLICATE_KEY }, "All errors should be duplicate key errors")
        assertEquals(listOf(
            Location(Coordinates(2,0), Coordinates(2,4), 26,30),
            Location(Coordinates(3,0), Coordinates(3,4), 39,43)
        ), errors.map { it.location })
    }

    @Test
    fun testDuplicateKeysInNestedObjects() {
        val source = """
            outer: {
                inner1: value1
                inner2: value2
                inner1: value3
            }
            outer2: {
                key: value
                key: value2
            }
        """.trimIndent()

        val result = KsonCore.parseToAst(source)
        assertEquals(2, result.messages.size, "Should have errors for duplicate keys in nested objects")

        val errorTypes = result.messages.map { it.message.type }
        assertTrue(errorTypes.all { it == OBJECT_DUPLICATE_KEY }, "All errors should be duplicate key errors")
    }

    @Test
    fun testDuplicateQuotedKeys() {
        val source = """
            "key1": value1
            'key1': value2
            key2: value3
        """.trimIndent()

        val result = KsonCore.parseToAst(source)
        assertEquals(1, result.messages.size, "Should detect duplicates even with different quote types")

        val error = result.messages.first()
        assertEquals(OBJECT_DUPLICATE_KEY, error.message.type)
    }

    @Test
    fun testListsWithObjectsContainingDuplicateKeys() {
        val source = """
            - key1: value1
              key1: value2
            - key2: value3
              key3: value4
            - key4: value5
              key4: value6
        """.trimIndent()

        val result = KsonCore.parseToAst(source)
        assertEquals(2, result.messages.size, "Should have errors for duplicate keys in list objects")

        val errorTypes = result.messages.map { it.message.type }
        assertTrue(errorTypes.all { it == OBJECT_DUPLICATE_KEY }, "All errors should be duplicate key errors")
    }

    @Test
    fun testSameKeyInDifferentObjectsIsAllowed() {
        val source = """
            object1: {
                key: value1
            }
            object2: {
                key: value2
            }
        """.trimIndent()

        val result = KsonCore.parseToAst(source)
        assertTrue(result.messages.isEmpty(), "Same key in different objects should be allowed")
    }
}