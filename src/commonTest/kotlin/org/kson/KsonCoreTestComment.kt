package org.kson

import org.kson.CompileTarget.*
import org.kson.KsonCoreTest.*
import kotlin.test.Test

/**
 * Tests for Kson comment handling and preservation
 */
class KsonCoreTestComment : KsonCoreTest {
    @Test
    fun testSourceWithComment() {
        assertParsesTo("""
            # this is a comment
            "string"
        """,
            """
            # this is a comment
            string
        """.trimIndent(),
            """
            # this is a comment
            string
        """.trimIndent(),
            """
            "string"
        """.trimIndent()
        )
    }

    @Test
    fun testSanityCheckCommentFreeCompiles() {
        assertParsesTo("""
            # a comment
            key: {
              # nested comment
              value: 42
            }
            # document end comment
        """.trimIndent(),
            """
            key:
              value: 42
        """.trimIndent(),
            """
            key:
              value: 42
        """.trimIndent(),
            """
            {
              "key": {
                "value": 42
              }
            }
        """.trimIndent(),
            compileSettings = CompileSettings(
                Kson(preserveComments = false),
                Yaml(preserveComments = false),
            )
        )
    }

    @Test
    fun testCommentPreservationOnDashLists() {
        assertParsesTo(
            """
                # comment one
                - one
                # comment two
                - two
                # comment three.1
                # comment three.2
                - three
            """.trimIndent(),
            """
                # comment one
                - one
                # comment two
                - two
                # comment three.1
                # comment three.2
                - three
            """.trimIndent(),
            """
                # comment one
                - one
                # comment two
                - two
                # comment three.1
                # comment three.2
                - three
            """.trimIndent(),
            """
                [
                  "one",
                  "two",
                  "three"
                ]
            """.trimIndent()
        )
    }

    @Test
    fun testMultipleCommentsOnNestedElement() {
        assertParsesTo(
            """
              [
                # first comment
                # second comment
                # third comment
                one,
                two
              ]
            """,
            """
              # first comment
              # second comment
              # third comment
              - one
              - two
            """.trimIndent(),
            """
              # first comment
              # second comment
              # third comment
              - one
              - two
            """.trimIndent(),
            """
              [
                "one",
                "two"
              ]
            """.trimIndent()
        )
    }

    @Test
    fun testCommentPreservationOnConstants() {

        assertParsesTo(
            """
                # comment on a number
                4.5
            """,
            """
                # comment on a number
                4.5
            """.trimIndent(),
            """
                # comment on a number
                4.5
            """.trimIndent(),
            "4.5"
        )

        assertParsesTo(
            """
                # comment on a boolean
                false
            """,
            """
                # comment on a boolean
                false
            """.trimIndent(),
            """
                # comment on a boolean
                false
            """.trimIndent(),
            "false"
        )

        assertParsesTo(
            """
                # comment on an identifier
                id
            """,
            """
                # comment on an identifier
                id
            """.trimIndent(),
            """
                # comment on an identifier
                id
            """.trimIndent(),
            "\"id\""
        )

        assertParsesTo(
            """
                # comment on a string
                "a string"
            """,
            """
                # comment on a string
                'a string'
            """.trimIndent(),
            """
                # comment on a string
                "a string"
            """.trimIndent(),
            "\"a string\""
        )
    }

    @Test
    fun testTrailingCommentPreservationOnConstants() {

        assertParsesTo(
            """
                4.5 # trailing comment
            """,
            """
                # trailing comment
                4.5
            """.trimIndent(),
            """
                # trailing comment
                4.5
            """.trimIndent(),
            "4.5"
        )

        assertParsesTo(
            """
                false # trailing comment
            """,
            """
                # trailing comment
                false
            """.trimIndent(),
            """
                # trailing comment
                false
            """.trimIndent(),
            "false"
        )

        assertParsesTo(
            """
                id # trailing comment
            """,
            """
                # trailing comment
                id
            """.trimIndent(),
            """
                # trailing comment
                id
            """.trimIndent(),
            "\"id\""
        )

        assertParsesTo(
            """
                "a string" # trailing comment
            """,
            """
                # trailing comment
                'a string'
            """.trimIndent(),
            """
                # trailing comment
                "a string"
            """.trimIndent(),
            "\"a string\""
        )
    }

    @Test
    fun testCommentPreservationOnObjects() {
        assertParsesTo(
            """
                # a comment
                key
                
                :
                    # an odd but legal comment on this val
                    val 
                # another comment
                key2: val2
            """,
            """
               # a comment
               # an odd but legal comment on this val
               key: val
               # another comment
               key2: val2
            """.trimIndent(),
            """
               # a comment
               # an odd but legal comment on this val
               key: val
               # another comment
               key2: val2
            """.trimIndent(),
            """
               {
                 "key": "val",
                 "key2": "val2"
               }
            """.trimIndent()
        )
    }

    @Test
    fun testCommentsPreservationOnCommas() {
        assertParsesTo(
            """
                {
                    key1:val1
                    # this comment should be preserved on this property
                    ,
                    key2:val2
                    # as should this one
                    ,
                }
            """,
            """
                # this comment should be preserved on this property
                key1: val1
                # as should this one
                key2: val2
            """.trimIndent(),
            """
                # this comment should be preserved on this property
                key1: val1
                # as should this one
                key2: val2
            """.trimIndent(),
            """
                {
                  "key1": "val1",
                  "key2": "val2"
                }
            """.trimIndent()
        )
    }

    @Test
    fun testCommentPreservationOnLists() {
        assertParsesTo(
            """
                # comment on list
                [
                # comment on first_element
                first_element,
                # comment on second_element
                second_element ]
            """,
            """
                # comment on list
                # comment on first_element
                - first_element
                # comment on second_element
                - second_element
            """.trimIndent(),
            """
                # comment on list
                # comment on first_element
                - first_element
                # comment on second_element
                - second_element
            """.trimIndent(),
            """
                [
                  "first_element",
                  "second_element"
                ]
            """.trimIndent()
        )

        assertParsesTo(
            """
                # comment on first_element
                - first_element
                # comment on second_element
                - second_element
            """,
            """
                # comment on first_element
                - first_element
                # comment on second_element
                - second_element
            """.trimIndent(),
            """
                # comment on first_element
                - first_element
                # comment on second_element
                - second_element
            """.trimIndent(),
            """
                [
                  "first_element",
                  "second_element"
                ]
            """.trimIndent(),
            "should always anchor dash-delimited list comments to the elements " +
                    "since there's no syntactic way to identify a comment on the whole list"
        )

        assertParsesTo(
            """
                # a list of lists
                [
                  [1.2, # trailing comment on constant element
                    # a nested list element
                    2.2, 3.2],
                  # a nested dash-delimited list
                  - [10.2]
                  - # a further nested braced list
                    [4.2,
                    # a further nested braced list element
                    5.2] # trailing comment on nested list
                  - [9.2,8.2]
                ]
            """,
            """
              # a list of lists
              - 
                # trailing comment on constant element
                - 1.2
                # a nested list element
                - 2.2
                - 3.2
                =
              - 
                # a nested dash-delimited list
                - 
                  - 10.2
                  =
                # a further nested braced list
                # trailing comment on nested list
                - 
                  - 4.2
                  # a further nested braced list element
                  - 5.2
                  =
                - 
                  - 9.2
                  - 8.2
            """.trimIndent(),
            """
            # a list of lists
            - 
              # trailing comment on constant element
              - 1.2
              # a nested list element
              - 2.2
              - 3.2
            - 
              # a nested dash-delimited list
              - 
                - 10.2
              # a further nested braced list
              # trailing comment on nested list
              - 
                - 4.2
                # a further nested braced list element
                - 5.2
              - 
                - 9.2
                - 8.2
            """.trimIndent(),
            """
                [
                  [
                    1.2,
                    2.2,
                    3.2
                  ],
                  [
                    [
                      10.2
                    ],
                    [
                      4.2,
                      5.2
                    ],
                    [
                      9.2,
                      8.2
                    ]
                  ]
                ]
            """.trimIndent(),
            "should preserve comments in nested lists"
        )
    }

    @Test
    fun testCommentPreservationOnEmbedBlocks() {
        assertParsesTo(
            """
                # a comment on an embed block
                %
                embedded stuff
                %%
            """,
            """
               # a comment on an embed block
               %
               embedded stuff
               %%
            """.trimIndent(),
            """
               # a comment on an embed block
               |
                 embedded stuff
            """.trimIndent(),
            """
              "embedded stuff"
            """.trimIndent()
        )
    }

    @Test
    fun testTrailingCommentOnList() {
        assertParsesTo(
            """
                # leading
                [one, # trailing "one"
                two # trailing "two"
                ] # trailing list brace
            """,
            """
                # leading
                # trailing list brace
                # trailing "one"
                - one
                # trailing "two"
                - two
            """.trimIndent(),
            """
                # leading
                # trailing list brace
                # trailing "one"
                - one
                # trailing "two"
                - two
            """.trimIndent(),
            """
                [
                  "one",
                  "two"
                ]
            """.trimIndent()
        )
    }

    @Test
    fun testTrailingCommentsInObjects() {
        assertParsesTo(
            """
                # leading
                {
                  keyword:value # trailing
                }
            """,
            """
                # leading
                # trailing
                keyword: value
            """.trimIndent(),
            """
                # leading
                # trailing
                keyword: value
            """.trimIndent(),
            """
                {
                  "keyword": "value"
                }
            """.trimIndent()
        )

        assertParsesTo(
            """
                {
                # leading
                keyword:value # trailing
                }
            """,
            """
                # leading
                # trailing
                keyword: value
            """.trimIndent(),
            """
                # leading
                # trailing
                keyword: value
            """.trimIndent(),
            """
                {
                  "keyword": "value"
                }
            """.trimIndent()
        )
    }

    @Test
    fun testDocumentEndComments() {
        assertParsesTo(
            """
                null
                
                
                
                # these are some trailing
                # comments that would like 
                # to be preserved at the end
                # of the file
            """,
            """
                null
                
                # these are some trailing
                # comments that would like 
                # to be preserved at the end
                # of the file
            """.trimIndent(),
            """
                null
                
                # these are some trailing
                # comments that would like 
                # to be preserved at the end
                # of the file
            """.trimIndent(),
            "null"
        )
    }
} 
