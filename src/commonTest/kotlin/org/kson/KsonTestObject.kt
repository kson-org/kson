package org.kson

import kotlin.test.Test

class KsonTestObject : KsonTest {
    @Test
    fun testEmptyObjectSource() {
        assertParsesTo(
            "{    }",
            "{}",
            "{}",
            "{}"
        )
    }

    @Test
    fun testObjectSource() {
        val expectKsonRootObjectAst = """
              key: val
              'string key': 66.3
              hello: "y'all"
            """.trimIndent()

        val expectedYamlRootObject = """
            key: val
            "string key": 66.3
            hello: "y'all"
        """.trimIndent()

        val expectedJsonRootObject = """
            {
              "key": "val",
              "string key": 66.3,
              "hello": "y'all"
            }
        """.trimIndent()

        assertParsesTo(
            """
                {
                    key: val
                    'string key': 66.3
                    hello: "y'all"
                }
            """,
            expectKsonRootObjectAst,
            expectedYamlRootObject,
            expectedJsonRootObject,
            "should parse as a root object when optional root parens are provided"
        )

        assertParsesTo(
            """
                key: val
                "string key": 66.3
                hello: "y'all"
            """,
            expectKsonRootObjectAst,
            expectedYamlRootObject,
            expectedJsonRootObject
        )
    }

    /**
     * For plain/un-delimited objects nested in []-bracket lists, objects have precedence for any optional commas given
     */
    @Test
    fun testObjectCommaPrecedence() {
        assertParsesTo(
            """
                [
                  ObjA1: 1,
                  ObjA2: [
                    nested1:v,
                    nested2:v,
                    nested3:v,
                    .,
                    alsoNested:v,
                  ],
                  ObjA3: 3,
                  
                  "A string",
                  
                  ObjB4: 4,
                ]
            """.trimIndent(),
            """
                - ObjA1: 1
                  ObjA2:
                    - nested1: v
                      nested2: v
                      nested3: v
                
                    - alsoNested: v
                      .
                  ObjA3: 3

                - 'A string'
                - ObjB4: 4
            """.trimIndent(),
            """
                - ObjA1: 1
                  ObjA2:
                    - nested1: v
                      nested2: v
                      nested3: v
                
                    - alsoNested: v
                  ObjA3: 3

                - "A string"
                - ObjB4: 4
            """.trimIndent(),
            """
                [
                  {
                    "ObjA1": 1,
                    "ObjA2": [
                      {
                        "nested1": "v",
                        "nested2": "v",
                        "nested3": "v"
                      },
                      {
                        "alsoNested": "v"
                      }
                    ],
                    "ObjA3": 3
                  },
                  "A string",
                  {
                    "ObjB4": 4
                  }
                ]
            """.trimIndent()
        )
    }

    @Test
    fun testObjectSourceMixedWithStringContainingRawNewlines() {
        assertParsesTo(
            """
                first: "value"
                second: "this is a string with a
                raw newline in it and at its end
                "
            """.trimIndent(),
            """
                first: value
                second: 'this is a string with a
                raw newline in it and at its end
                '
            """.trimIndent(),
            """
                first: "value"
                second: "this is a string with a
                raw newline in it and at its end
                "
            """.trimIndent(),
            """
                {
                  "first": "value",
                  "second": "this is a string with a\nraw newline in it and at its end\n"
                }
            """.trimIndent()
        )
    }

    @Test
    fun testProhibitedKey(){
        assertParsesTo(
            """
                "false": 'false' 
                "true": 'true'
                "null": "null" 
            """.trimIndent(),
            """
                'false': 'false'
                'true': 'true'
                'null': 'null'
            """.trimIndent(),
            """
                "false": "false"
                "true": "true"
                "null": "null"
            """.trimIndent(),
            """
                {
                  "false": "false",
                  "true": "true",
                  "null": "null"
                }
            """.trimIndent()
        )
    }

    @Test
    fun testObjectSourceWithImmediateTrailingComment() {
        assertParsesTo(
            """
                {a:b}#comment
            """,
            """
                #comment
                a: b
            """.trimIndent(),
            """
                #comment
                a: b
            """.trimIndent(),
            """
                {
                  "a": "b"
                }
            """.trimIndent(),
            "should parse as a root object when optional root parens are provided"
        )
    }

    @Test
    fun testObjectSourceOptionalComma() {
        val expectKsonForRootObjectAst = """
              key: val
              'string key': 66.3
              hello: "y'all"
            """.trimIndent()

        assertParsesTo(
            """
                {
                    key: val
                    'string key': 66.3,
                    hello: "y'all",
                }
            """,
            expectKsonForRootObjectAst,
            """
               key: val
               "string key": 66.3
               hello: "y'all"
            """.trimIndent(),
            """
               {
                 "key": "val",
                 "string key": 66.3,
                 "hello": "y'all"
               }
            """.trimIndent(),
            "should parse object ignoring optional commas, even trailing"
        )

        assertParsesTo(
            """
                key: val
                "string key": 66.3,
                hello: "y'all"
            """,
            expectKsonForRootObjectAst,
            """
               key: val
               "string key": 66.3
               hello: "y'all"
            """.trimIndent(),
            """
               {
                 "key": "val",
                 "string key": 66.3,
                 "hello": "y'all"
               }
            """.trimIndent(),
            "should parse ignoring optional commas, even in brace-free root objects"
        )
    }

    @Test
    fun testNestedNonDelimitedObjects() {
        assertParsesTo(
            """
                key:
                  nested_key: 10
                  another_nest_key: 3
                  .
                unnested_key: 44
            """.trimIndent(),
            """
                key:
                  nested_key: 10
                  another_nest_key: 3
                  .
                unnested_key: 44
            """.trimIndent(),
            """
                key:
                  nested_key: 10
                  another_nest_key: 3
                
                unnested_key: 44
            """.trimIndent(),
            """
                {
                  "key": {
                    "nested_key": 10,
                    "another_nest_key": 3
                  },
                  "unnested_key": 44
                }
            """.trimIndent()
        )
    }
} 
