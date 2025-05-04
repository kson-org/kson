package org.kson

import kotlin.test.Test

class KsonTestList : KsonTest() {
    @Test
    fun testEmptyListSource() {
        assertParsesTo(
            """
                []
            """,
            "<>",
            "[]",
            "[]"
        )
    }

    @Test
    fun testSquareBracketListSource() {
        assertParsesTo(
            """
                ["a string"]
            """,
            """
                - "a string"
            """.trimIndent(),
            """
                - "a string"
            """.trimIndent(),
            """
                [
                  "a string"
                ]
            """.trimIndent()
        )

        assertParsesTo(
            """
                [42.4, 43.1, 44.7]
            """,
            """
                - 42.4
                - 43.1
                - 44.7
            """.trimIndent(),
            """
                - 42.4
                - 43.1
                - 44.7
            """.trimIndent(),
            """
                [
                  42.4,
                  43.1,
                  44.7
                ]
            """.trimIndent()
        )

        assertParsesTo(
            """
                [true, false, null,]
            """,
            """
                - true
                - false
                - null
            """.trimIndent(),
            """
                - true
                - false
                - null
            """.trimIndent(),
            """
                [
                  true,
                  false,
                  null
                ]
            """.trimIndent(),
            "should support an optional trailing comma in lists"
        )

        assertParsesTo(
            """
                [true, false, [1.2, 3.4, 5.6]]
            """,
            """
                - true
                - false
                - 
                  - 1.2
                  - 3.4
                  - 5.6
            """.trimIndent(),
            """
                - true
                - false
                - 
                  - 1.2
                  - 3.4
                  - 5.6
            """.trimIndent(),
            """
                [
                  true,
                  false,
                  [
                    1.2,
                    3.4,
                    5.6
                  ]
                ]
            """.trimIndent()
        )
    }

    @Test
    fun testDashListSource() {
        assertParsesTo(
            """
                - "a string"
            """,
            """
                - "a string"
            """.trimIndent(),
            """
                - "a string"
            """.trimIndent(),
            """
                [
                  "a string"
                ]
            """.trimIndent()
        )

        assertParsesTo(
            """
                - 42.4
                - 43.1
                - 44.7
            """,
            """
                - 42.4
                - 43.1
                - 44.7
            """.trimIndent(),
            """
                - 42.4
                - 43.1
                - 44.7
            """.trimIndent(),
            """
                [
                  42.4,
                  43.1,
                  44.7
                ]
            """.trimIndent()
        )

        // note that the indentation isn't significant in kson dash-delimited lists (unlike yaml)
        assertParsesTo(
            """
                - true
                  - false
                    - null
            """,
            """
                - true
                - false
                - null
            """.trimIndent(),
            """
                - true
                - false
                - null
            """.trimIndent(),
            """
                [
                  true,
                  false,
                  null
                ]
            """.trimIndent()
        )
    }

    @Test
    fun testDelimitedDashList() {
        assertParsesTo("""
                <>
            """.trimIndent(),
            "<>",
            "[]",
            "[]"
        )

        assertParsesTo("""
                < - a - b - c >
            """.trimIndent(),
            """
                - a
                - b
                - c
            """.trimIndent(),
            """
                - a
                - b
                - c
            """.trimIndent(),
            """
                [
                  "a",
                  "b",
                  "c"
                ]
            """.trimIndent()
        )

        assertParsesTo("""
                < 
                  - a 
                  - b 
                  - c 
                >
            """.trimIndent(),
            """
                - a
                - b
                - c
            """.trimIndent(),
            """
                - a
                - b
                - c
            """.trimIndent(),
            """
                [
                  "a",
                  "b",
                  "c"
                ]
            """.trimIndent()
        )
    }

    @Test
    fun testDashListNestedWithCommaList() {
        assertParsesTo("""
            [- []]
        """.trimIndent(),
            """
               - 
                 - 
                   <>
            """.trimIndent(),
            """
               - 
                 - 
                   []
            """.trimIndent(),
            """
               [
                 [
                   []
                 ]
               ]
            """.trimIndent()
        )
    }

    @Test
    fun testDashListNestedWithObject() {
        assertParsesTo("""
            - { 
                nestedDashList: - a
                                - b
                                - c
              }
        """.trimIndent(),
            """
                - nestedDashList:
                    - a
                    - b
                    - c
            """.trimIndent(),
            """
                - nestedDashList:
                    - a
                    - b
                    - c
            """.trimIndent(),
            """
                [
                  {
                    "nestedDashList": [
                      "a",
                      "b",
                      "c"
                    ]
                  }
                ]
            """.trimIndent()
        )
    }

    @Test
    fun testDashListNestedWithDashList() {
        assertParsesTo("""
            - <
                - a
                - b
                - <
                    - a1
                    - b1
                    - c1
                  >
                - c
              >
        """.trimIndent(),
            """
                - 
                  - a
                  - b
                  - 
                    - a1
                    - b1
                    - c1
                    .
                  - c
            """.trimIndent(),
            """
                - 
                  - a
                  - b
                  - 
                    - a1
                    - b1
                    - c1
                  - c
            """.trimIndent(),
            """
                [
                  [
                    "a",
                    "b",
                    [
                      "a1",
                      "b1",
                      "c1"
                    ],
                    "c"
                  ]
                ]
            """.trimIndent()
        )
    }

    @Test
    fun testCommaFreeList() {
        assertParsesTo("""
            [
                null true [sublist] 
                - another 
                - sublist
            ]
        """,
            """
            - null
            - true
            - 
              - sublist
              .
            - 
              - another
              - sublist
        """.trimIndent(),
            """
            - null
            - true
            - 
              - sublist
            - 
              - another
              - sublist
        """.trimIndent(),
            """
            [
              null,
              true,
              [
                "sublist"
              ],
              [
                "another",
                "sublist"
              ]
            ]
        """.trimIndent()
        )
    }

    @Test
    fun testNestedNonDelimitedDashLists() {
        assertParsesTo(
            """
                - 
                  - "sub-list elem 1"
                  - "sub-list elem 2"
                  .
                - "outer list elem 1"
            """.trimIndent(),
            """
                - 
                  - "sub-list elem 1"
                  - "sub-list elem 2"
                  .
                - "outer list elem 1"
            """.trimIndent(),
            """
                - 
                  - "sub-list elem 1"
                  - "sub-list elem 2"
                - "outer list elem 1"
            """.trimIndent(),
            """
                [
                  [
                    "sub-list elem 1",
                    "sub-list elem 2"
                  ],
                  "outer list elem 1"
                ]
            """.trimIndent()
        )
    }
} 
