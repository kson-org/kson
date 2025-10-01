package org.kson.validation

import org.kson.KsonCore
import org.kson.parser.messages.MessageType.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IndentValidatorTest {

    @Test
    fun testValidObjectIndentation() {
        val source = """
            key1: value1
            key2: value2
            key3: value3
        """.trimIndent()

        val result = KsonCore.parseToAst(source)
        assertTrue(result.messages.isEmpty(), "Should have no errors for aligned properties")
    }

    @Test
    fun testMisalignedObjectProperties() {
        val source = """
            key1: value1
              key2: value2
            key3: value3
        """.trimIndent()

        val result = KsonCore.parseToAst(source)
        assertEquals(1, result.messages.size, "Should have one error for misaligned property")

        val error = result.messages.first()
        assertEquals(OBJECT_PROPERTIES_MISALIGNED, error.message.type)
    }

    @Test
    fun testMultipleMisalignedObjectProperties() {
        val source = """
            key1: value1
              key2: value2
                key3: value3
        """.trimIndent()

        val result = KsonCore.parseToAst(source)
        assertEquals(2, result.messages.size, "Should have two errors for misaligned properties")
    }

    @Test
    fun testValidDashListIndentation() {
        val source = """
            - item1
            - item2
            - item3
        """.trimIndent()

        val result = KsonCore.parseToAst(source)
        assertTrue(result.messages.isEmpty(), "Should have no errors for aligned list items")
    }

    @Test
    fun testMisalignedDashListItems() {
        val source = """
            - item1
              - item2
            - item3
        """.trimIndent()

        val result = KsonCore.parseToAst(source)
        assertEquals(1, result.messages.size, "Should have one error for misaligned list item")

        val error = result.messages.first()
        assertEquals(DASH_LIST_ITEMS_MISALIGNED, error.message.type)
    }

    @Test
    fun testNestedObjectsWithValidIndentation() {
        val source = """
            outer1:
              inner1: value1
              inner2: value2
              .
            outer2:
              inner3: value3
              inner4: value4
        """.trimIndent()

        val result = KsonCore.parseToAst(source)
        assertTrue(result.messages.isEmpty(), "Should have no errors for properly nested objects")
    }

    @Test
    fun testNestedObjectsWithMisalignedFirstInnerProperties() {
        val source = """
            outer1:
              inner1: value1
                inner2: value2
              .
            outer2: value2
        """.trimIndent()

        val result = KsonCore.parseToAst(source)
        assertEquals(1, result.messages.size, "Should have one error for the misaligned first inner property")

        val error = result.messages.first()
        assertEquals(OBJECT_PROPERTIES_MISALIGNED, error.message.type)
    }

    @Test
    fun testNestedListWithMisalignedFirstInnerProperties() {
        val source = """
            -
              - inner1
                - inner2
              =
            - outer1
            - outer2
        """.trimIndent()

        val result = KsonCore.parseToAst(source)
        assertEquals(1, result.messages.size, "Should have one error for misaligned first inner list")

        val error = result.messages.first()
        assertEquals(DASH_LIST_ITEMS_MISALIGNED, error.message.type)
    }

    @Test
    fun testNestedListsWithValidIndentation() {
        val source = """
            - 
              - inner1
              - inner2
              =
            - 
              - inner3
              - inner4
        """.trimIndent()

        val result = KsonCore.parseToAst(source)
        assertTrue(result.messages.isEmpty(), "Should have no errors for properly nested lists")
    }

    @Test
    fun testMixedObjectAndListNesting() {
        val source = """
            key1:
              - item1
              - item2
            key2:
              nested:
                - item3
                - item4
        """.trimIndent()

        val result = KsonCore.parseToAst(source)
        assertTrue(result.messages.isEmpty(), "Should have no errors for properly mixed nesting")
    }

    @Test
    fun testValidateSameLineConstructs() {
        val message = "Should have no errors for one-line constructs," +
                "provided leading items are aligned"

        assertTrue(
            KsonCore.parseToAst(
                """
                    key1: value1 key2: value2
                    key3: value3 key4: value4
                """.trimIndent()
            )
                .messages.isEmpty(), message
        )

        assertTrue(
            KsonCore.parseToAst(
                """
                    - - 1
                           - 2
                """.trimIndent()
            )
                .messages.isEmpty(), message
        )

        val misalignedSource = """
              key1: value1 key2: value2
            key3: value3 key4: value4
        """.trimIndent()

        val misalignedResult = KsonCore.parseToAst(misalignedSource)
        assertEquals(
            1, misalignedResult.messages.size, "Should have an error the mis-aligned " +
                    "one-line constructs"
        )

        val error = misalignedResult.messages.first()
        assertEquals(OBJECT_PROPERTIES_MISALIGNED, error.message.type)
    }

    @Test
    fun testValidateLeadingAlignment1() {
        val source = """
            key1: - 1 - 2
               - 3
               - 4
        """.trimIndent()

        val result = KsonCore.parseToAst(source)
        assertTrue(
            result.messages.isEmpty(), "Should have no errors provided leading alignment is correct " +
                    "for all entries"
        )

        val misalignedSource = """
            key1: - 1 - 2
               - 3
                    - 4
        """.trimIndent()

        val misalignedResult = KsonCore.parseToAst(misalignedSource)
        assertEquals(
            1, misalignedResult.messages.size, "Should have an error the mis-aligned " +
                    "end of this list"
        )

        val error = misalignedResult.messages.first()
        assertEquals(DASH_LIST_ITEMS_MISALIGNED, error.message.type)
    }

    @Test
    fun testValidateLeadingAlignment2() {
        val source = """
            key1: - 1 - 2
               - 3
               - 4 key2: w key3: x
            key4: y
            key5: z
        """.trimIndent()

        val result = KsonCore.parseToAst(source)
        assertTrue(
            result.messages.isEmpty(), "Should have no errors provided leading alignment is correct " +
                    "for all entries"
        )

        val misalignedSource = """
           - 1 - 2
           - 3
           - key2: w key3: x
                key4: y
                    key5: z
                key6: a
        """.trimIndent()

        val misalignedResult = KsonCore.parseToAst(misalignedSource)
        assertEquals(
            1, misalignedResult.messages.size, "Should have an error the mis-aligned " +
                    "end of this object"
        )

        val error = misalignedResult.messages.first()
        assertEquals(OBJECT_PROPERTIES_MISALIGNED, error.message.type)
    }

    @Test
    fun testEmptyObjectAndList() {
        val source = """
            empty_object: {}
            empty_list: []
        """.trimIndent()

        val result = KsonCore.parseToAst(source)
        assertTrue(result.messages.isEmpty(), "Should have no errors for empty structures")
    }

    @Test
    fun testSinglePropertyObject() {
        val source = """
            single: value
        """.trimIndent()

        val result = KsonCore.parseToAst(source)
        assertTrue(result.messages.isEmpty(), "Should have no errors for single property object")
    }

    @Test
    fun testDelimitedObjectsAlsoChecked() {
        // Delimited objects should also be checked for alignment
        val source = """
            {
              key1: value1
                key2: value2
              key3: value3
            }
        """.trimIndent()

        val result = KsonCore.parseToAst(source)
        assertEquals(1, result.messages.size, "Should have one error for misaligned property in delimited object")

        val error = result.messages.first()
        assertEquals(OBJECT_PROPERTIES_MISALIGNED, error.message.type)
    }

    @Test
    fun testNestedInSingleElementObject() {
        val source = """
            person:
              favorite_books: 10
                    test: ex
        """.trimIndent()

        val result = KsonCore.parseToAst(source)
        assertEquals(1, result.messages.size)

        val error = result.messages.first()
        assertEquals(OBJECT_PROPERTIES_MISALIGNED, error.message.type)
    }

    @Test
    fun testNestedDashedList() {
        val badSource = """
                - -
                  - 1
            """.trimIndent()

        val badResult = KsonCore.parseToAst(badSource)
        assertEquals(1, badResult.messages.size)

        val error = badResult.messages.first()
        assertEquals(DASH_LIST_ITEMS_NESTING_ISSUE, error.message.type)
    }

    /**
     * Regression test for a special case where delimited items hanging off a list dash were tripping up
     * our alignment detection
     */
    @Test
    fun testAlignmentWithDelimiters() {
        val message = "Should have no errors for things misaligned by their opening delimiter"

        assertTrue(
            KsonCore.parseToAst(
                """
                    {one:1
                    two:2}
                """.trimIndent()
            ).messages.isEmpty(), message
        )

        assertTrue(
            KsonCore.parseToAst(
                """
                    {one:1
                    two:2}
                """.trimIndent()
            ).messages.isEmpty(), message
        )

        assertTrue(
            KsonCore.parseToAst(
                """
                    <- 1
                    - 2>
                """.trimIndent()
            ).messages.isEmpty(), message
        )

        assertTrue(
            KsonCore.parseToAst(
                """
                    - # object hanging off dash
                     {key1: x
                     # this should not be considered mis-aligned
                     key2: y}
                """.trimIndent()
            ).messages.isEmpty(), message
        )

        assertTrue(
            KsonCore.parseToAst(
                """
                    - # list hanging off dash
                     [x
                     # this should not be considered mis-aligned
                     y]
                """.trimIndent()
            ).messages.isEmpty(), message
        )

        assertTrue(
            KsonCore.parseToAst(
                """
                    - # list hanging off dash
                     < - x
                     # this should not be considered mis-aligned
                     - y>
                """.trimIndent()
            ).messages.isEmpty(), message
        )
    }

    @Test
    fun testNonLeadingMultilineStrutures() {
        /**
         * Regression test for a bug in the validator where alignment tracking was incorrect in the case that
         * a non-leading item spanned multiple lines, like the `non_leading:` property in this test.  In this
         * case, we were incorrectly marking `should_not_error:` as a deceptive indent
         */
        assertTrue(
            KsonCore.parseToAst(
                """
                    x:y non_leading:{
                    } should_not_error: 0
                """.trimIndent()
            ).messages.isEmpty(),
            "should never consider a non-leading item on a line to mis-aligned"
        )
    }

    @Test
    fun testDeceptivelyAlignedSubObject() {
        val badSource = """
                key:
                   nested1: 80
                   nested2: 80000 nested3: 10000
                   nested4: 12000 nested5:
                   doubleNested: 14000
            """.trimIndent()

        val badResult = KsonCore.parseToAst(badSource)
        assertEquals(1, badResult.messages.size)

        val error = badResult.messages.first()
        assertEquals(OBJECT_PROPERTY_NESTING_ISSUE, error.message.type)

        val goodSource = """
                key:
                   nested1: 80
                   nested2: 80000 nested3: 10000
                   nested4: 12000 nested5:
                                    doubleNested: 14000
            """.trimIndent()
        val goodResult = KsonCore.parseToAst(goodSource)
        assertEquals(0, goodResult.messages.size)
    }

    @Test
    fun testDeceptivelyAlignedSubList() {
        val badSource = """
                ports:
                   - 80
                   - 8000 - 10000
                   - 12000 -
                   - 14000
            """.trimIndent()

        val badResult = KsonCore.parseToAst(badSource)
        assertEquals(1, badResult.messages.size)

        val error = badResult.messages.first()
        assertEquals(DASH_LIST_ITEMS_NESTING_ISSUE, error.message.type)

        val goodSource = """
                ports:
                   - 80
                   - 8000 - 10000
                   - 12000 -
                             - 14000
            """.trimIndent()
        val goodResult = KsonCore.parseToAst(goodSource)
        assertEquals(0, goodResult.messages.size)
    }

    @Test
    fun testMixedSubListsAndObjects() {
        val badSource = """
                deceptive_list:
                  - 1
                  - key:
                  # deceptive indent: nested under `key:`
                  - 9
                # deceptive indent: sibling to `key:`
                deceptive_object:
                    key: x
                    list: - 
                    # deceptive indent: nested under `list: - ` list
                    key: x
            """.trimIndent()

        val badResult = KsonCore.parseToAst(badSource)
        assertEquals(3, badResult.messages.size)

        val errors = badResult.messages
        assertEquals(OBJECT_PROPERTY_NESTING_ISSUE, errors[0].message.type)
        assertEquals(DASH_LIST_ITEMS_NESTING_ISSUE, errors[1].message.type)
        assertEquals(DASH_LIST_ITEMS_NESTING_ISSUE, errors[2].message.type)

        val goodSource = """
                deceptive_list:
                  - 1
                  - key:
                      - 9
                    deceptive_object:
                      key: x
                      list: - 
                             key: x
            """.trimIndent()
        val goodResult = KsonCore.parseToAst(goodSource)
        assertEquals(0, goodResult.messages.size)
    }

    @Test
    fun testSimpleListValueNesting() {
        val badSource = """
                    -
                bad_nest
                    -
                   also_bad
            """.trimIndent()

        val badResult = KsonCore.parseToAst(badSource)
        assertEquals(2, badResult.messages.size)

        val errors = badResult.messages
        assertEquals(DASH_LIST_ITEMS_NESTING_ISSUE, errors[0].message.type)
        assertEquals(DASH_LIST_ITEMS_NESTING_ISSUE, errors[1].message.type)
    }
}
