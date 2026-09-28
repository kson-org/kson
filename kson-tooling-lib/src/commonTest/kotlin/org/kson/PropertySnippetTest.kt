package org.kson

import org.kson.tooling.CompletionItem
import org.kson.tooling.CompletionKind
import org.kson.tooling.KsonTooling
import org.kson.tooling.PropertySnippetBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class PropertySnippetTest {

    private fun withProperty(propertySchema: String) =
        """{ "type": "object", "properties": { "p": $propertySchema } }"""

    private val keySchema = """
        {
            "type": "object",
            "properties": {
                "name": { "type": "string" },
                "tags": { "type": "array" },
                "settings": {
                    "type": "object",
                    "properties": { "theme": { "type": "string" }, "tags": { "type": "string" } }
                }
            }
        }
    """

    private val layoutSchema = """
        {
            "type": "object",
            "properties": {
                "tags": { "type": "array" },
                "settings": {
                    "type": "object",
                    "properties": {
                        "theme": { "type": "string" },
                        "size": { "type": "integer" },
                        "tags": { "type": "string" }
                    }
                },
                "outer": {
                    "type": "object",
                    "properties": {
                        "inner": { "type": "object", "properties": { "x": { "type": "string" } } },
                        "frame": { "type": "object" }
                    }
                },
                "items": {
                    "type": "array",
                    "items": { "type": "object", "properties": { "id": { "type": "string" } } }
                }
            }
        }
    """

    private val entrySchema = """
        {
            "type": "object",
            "properties": {
                "entries": {
                    "type": "object",
                    "additionalProperties": {
                        "anyOf": [ { "${'$'}ref": "#/${'$'}defs/group" }, { "${'$'}ref": "#/${'$'}defs/item" } ]
                    }
                }
            },
            "${'$'}defs": {
                "group": { "type": "object", "additionalProperties": false, "properties": { "members": { "type": "array" } } },
                "itemBase": { "type": "object", "properties": { "name": { "type": "string" } } },
                "item": {
                    "allOf": [
                        { "${'$'}ref": "#/${'$'}defs/itemBase" },
                        {
                            "oneOf": [
                                {
                                    "properties": { "kind": { "const": "a" }, "params": { "${'$'}ref": "#/${'$'}defs/aParams" } },
                                    "required": ["kind", "params"]
                                },
                                {
                                    "properties": { "kind": { "const": "b" }, "params": { "${'$'}ref": "#/${'$'}defs/bParams" } },
                                    "required": ["kind", "params"]
                                }
                            ]
                        }
                    ]
                },
                "aParams": { "type": "object", "additionalProperties": false, "properties": { "size": { "type": "integer" } } },
                "bParams": { "type": "object", "additionalProperties": false, "properties": { "color": { "type": "string" } } }
            }
        }
    """

    private fun completionFor(schema: String, documentWithCaret: String, property: String): CompletionItem {
        val caretIndex = documentWithCaret.indexOf(CARET)
        require(caretIndex >= 0) { "Document must contain $CARET" }
        val beforeCaret = documentWithCaret.substring(0, caretIndex)
        val line = beforeCaret.count { it == '\n' }
        val column = caretIndex - (beforeCaret.lastIndexOf('\n') + 1)
        val document = documentWithCaret.replace(CARET, "")

        val completions = KsonTooling.getCompletionsAtLocation(
            KsonTooling.parse(document), KsonTooling.parse(schema), line, column
        )
        return completions.singleOrNull { it.kind == CompletionKind.PROPERTY && it.label == property }
            ?: fail("expected a `$property` property completion, got: ${completions.map { it.label }}")
    }

    private fun accept(schema: String, documentWithCaret: String, property: String): String {
        val edit = completionFor(schema, documentWithCaret, property).snippetEdit
            ?: fail("expected a snippet for `$property`")
        val lines = documentWithCaret.replace(CARET, "").split("\n")
        fun offset(line: Int, column: Int) = lines.take(line).sumOf { it.length + 1 } + column
        val document = lines.joinToString("\n")
        fun withEdit(newText: String) = document.substring(0, offset(edit.range.startLine, edit.range.startColumn)) +
                newText +
                document.substring(offset(edit.range.endLine, edit.range.endColumn))
        fun diagnostics(edited: String) =
            KsonTooling.validateDocument(KsonTooling.parse(edited), KsonTooling.parse(schema)).map { it.message }
        fun diagnosticsWithoutSchema(edited: String) =
            KsonTooling.validateDocument(KsonTooling.parse(edited)).map { it.message }

        val edited = withEdit(edit.newText.replace("\$0", CARET))
        assertEquals(emptyList(), diagnostics(edited.replace(CARET, "")), "should parse and validate:\n$edited")
        val opened = edit.newText.substringAfter(": ")
        val valuesOfOtherKinds = VALUE_OF_EACH_KIND.filter { it.first() != opened.first() }
        for (value in valuesOfOtherKinds) {
            val withOtherKind = withEdit("$property: $value")
            assertEquals(emptyList(), diagnosticsWithoutSchema(withOtherKind), "should parse:\n$withOtherKind")
            assertTrue(diagnostics(withOtherKind).isNotEmpty(), "only the snippet's kind should validate, not:\n$withOtherKind")
        }
        return edited
    }

    private fun assertPlain(schema: String, documentWithCaret: String, property: String) {
        assertNull(completionFor(schema, documentWithCaret, property).snippetEdit, "`$property` should be plain")
    }

    // The value's type

    @Test
    fun objectPropertyOpensBraces() {
        assertEquals("p: {<caret>}", accept(withProperty("""{ "type": "object" }"""), "<caret>", "p"))
    }

    @Test
    fun arrayPropertyOpensBrackets() {
        assertEquals("p: [<caret>]", accept(withProperty("""{ "type": "array" }"""), "<caret>", "p"))
    }

    @Test
    fun stringPropertyOpensQuotes() {
        assertEquals("p: '<caret>'", accept(withProperty("""{ "type": "string" }"""), "<caret>", "p"))
    }

    @Test
    fun otherScalarsStayPlain() {
        for (type in listOf("number", "integer", "boolean", "null")) {
            assertPlain(withProperty("""{ "type": "$type" }"""), "<caret>", "p")
        }
    }

    @Test
    fun severalTypesStayPlain() {
        assertPlain(withProperty("""{ "type": ["object", "string"] }"""), "<caret>", "p")
    }

    @Test
    fun untypedStaysPlain() {
        assertPlain(withProperty("""{ "description": "anything goes" }"""), "<caret>", "p")
    }

    @Test
    fun refToATypedSchemaOpensThatType() {
        val schema = """
            {
                "type": "object",
                "properties": { "p": { "${'$'}ref": "#/${'$'}defs/list" } },
                "${'$'}defs": { "list": { "type": "array" } }
            }
        """
        assertEquals("p: [<caret>]", accept(schema, "<caret>", "p"))
    }

    @Test
    fun refChainOpensTheTypeAtItsEnd() {
        val schema = """
            {
                "type": "object",
                "properties": { "p": { "${'$'}ref": "#/${'$'}defs/a" } },
                "${'$'}defs": {
                    "a": { "${'$'}ref": "#/${'$'}defs/b", "type": "string" },
                    "b": { "type": "object" }
                }
            }
        """
        assertEquals("p: {<caret>}", accept(schema, "<caret>", "p"))
    }

    @Test
    fun refThatNeverResolvesStaysPlain() {
        for (p in listOf(
            """{ "${'$'}ref": "#/${'$'}defs/loop" }""",
            """{ "${'$'}ref": "#/${'$'}defs/a" }""",
            """{ "${'$'}ref": "#/${'$'}defs/missing", "type": "string" }""",
            """{ "${'$'}ref": "#/${'$'}defs/toMissing" }""",
            """{ "${'$'}ref": "#/${'$'}defs/missing", "allOf": [ { "type": "string" } ] }""",
            """{ "allOf": [ { "${'$'}ref": "#/${'$'}defs/missing" }, { "type": "string" } ] }"""
        )) {
            val schema = """
                {
                    "type": "object",
                    "properties": { "p": $p },
                    "${'$'}defs": {
                        "loop": { "${'$'}ref": "#/${'$'}defs/loop", "type": "object" },
                        "a": { "${'$'}ref": "#/${'$'}defs/b", "type": "string" },
                        "b": { "${'$'}ref": "#/${'$'}defs/a", "type": "object" },
                        "toMissing": { "${'$'}ref": "#/${'$'}defs/missing", "type": "string" }
                    }
                }
            """
            assertNull(completionFor(schema, "<caret>", "p").snippetEdit, p)
        }
    }

    @Test
    fun propertyBelowARefThatNeverResolvesStaysPlain() {
        for (p in listOf(
            """{ "${'$'}ref": "#/${'$'}defs/loop" }""",
            """{ "${'$'}ref": "#/${'$'}defs/missing", "properties": { "x": { "type": "object" } } }""",
            """{ "allOf": [ { "${'$'}ref": "#/${'$'}defs/loop" }, { "properties": { "x": { "type": "object" } } } ] }"""
        )) {
            val schema = """
                {
                    "type": "object",
                    "properties": { "p": $p },
                    "${'$'}defs": { "loop": { "${'$'}ref": "#/${'$'}defs/loop", "properties": { "x": { "type": "object" } } } }
                }
            """
            assertNull(completionFor(schema, "p: {<caret>}", "x").snippetEdit, p)
        }
        val rootSchema = """
            {
                "type": "object",
                "allOf": [ { "${'$'}ref": "#/${'$'}defs/loop" }, { "properties": { "p": { "type": "object" } } } ],
                "${'$'}defs": { "loop": { "${'$'}ref": "#/${'$'}defs/loop" } }
            }
        """
        assertPlain(rootSchema, "<caret>", "p")
    }

    @Test
    fun refToAnAlternativeOpensTheTypeItsBranchesShare() {
        val schema = """
            {
                "type": "object",
                "properties": { "p": { "${'$'}ref": "#/${'$'}defs/either" } },
                "${'$'}defs": { "either": { "oneOf": [ { "type": "array" }, { "type": "array", "minItems": 2 } ] } }
            }
        """
        assertEquals("p: [<caret>]", accept(schema, "<caret>", "p"))
    }

    @Test
    fun refToAnIdResolvesTheTargetsAllOfUnderThatId() {
        val schema = """
            {
                "type": "object",
                "properties": { "p": { "${'$'}ref": "http://example.com/a.json" } },
                "${'$'}defs": {
                    "a": {
                        "${'$'}id": "http://example.com/a.json",
                        "allOf": [ { "${'$'}ref": "#/${'$'}defs/x" } ],
                        "${'$'}defs": { "x": { "type": "object" } }
                    },
                    "x": { "type": "string" }
                }
            }
        """
        assertEquals("p: {<caret>}", accept(schema, "<caret>", "p"))
    }

    @Test
    fun refToAnIdResolvesTheTargetsRefsUnderThatId() {
        val schema = """
            {
                "type": "object",
                "properties": { "p": { "${'$'}ref": "http://example.com/a.json" } },
                "${'$'}defs": {
                    "a": {
                        "${'$'}id": "http://example.com/a.json",
                        "anyOf": [ { "${'$'}ref": "#/${'$'}defs/x" } ],
                        "${'$'}defs": { "x": { "type": "object" } }
                    },
                    "x": { "type": "string" }
                }
            }
        """
        assertEquals("p: {<caret>}", accept(schema, "<caret>", "p"))
    }

    @Test
    fun branchWithAnIdResolvesItsRefsUnderThatId() {
        val schema = """
            {
                "type": "object",
                "properties": {
                    "p": {
                        "anyOf": [ {
                            "${'$'}id": "http://example.com/b.json",
                            "allOf": [ { "${'$'}ref": "#/${'$'}defs/x" } ],
                            "${'$'}defs": { "x": { "type": "object" } }
                        } ]
                    }
                },
                "${'$'}defs": { "x": { "type": "string" } }
            }
        """
        assertEquals("p: {<caret>}", accept(schema, "<caret>", "p"))
    }

    @Test
    fun allOfIntersectsTheDeclaredTypes() {
        val schema = withProperty("""{ "allOf": [ { "type": ["object", "string"] }, { "type": "object" } ] }""")
        assertEquals("p: {<caret>}", accept(schema, "<caret>", "p"))
    }

    @Test
    fun allOfMemberForbiddingThePropertyStaysPlain() {
        val schema = """
            {
                "type": "object",
                "allOf": [
                    { "properties": { "p": { "type": "string" } } },
                    { "properties": { "q": {} }, "additionalProperties": false }
                ]
            }
        """
        assertPlain(schema, "<caret>", "p")
    }

    @Test
    fun integerCountsAsANumberWhenIntersecting() {
        val schema = withProperty("""{ "allOf": [ { "type": ["number", "string"] }, { "type": ["integer", "string"] } ] }""")
        assertPlain(schema, "<caret>", "p")
    }

    // Alternatives

    @Test
    fun oneOfBranchesSharingATypeOpenIt() {
        val schema = withProperty("""{ "oneOf": [ { "type": "object" }, { "type": "object", "required": ["x"] } ] }""")
        assertEquals("p: {<caret>}", accept(schema, "<caret>", "p"))
    }

    @Test
    fun anyOfBranchesSharingATypeOpenIt() {
        val schema = withProperty("""{ "anyOf": [ { "type": "array" }, { "type": "array", "minItems": 2 } ] }""")
        assertEquals("p: [<caret>]", accept(schema, "<caret>", "p"))
    }

    @Test
    fun thenAndElseSharingATypeOpenIt() {
        val schema = withProperty("""{ "if": { "minLength": 2 }, "then": { "type": "string" }, "else": { "type": "string" } }""")
        assertEquals("p: '<caret>'", accept(schema, "<caret>", "p"))
    }

    @Test
    fun branchesDisagreeingStayPlainThoughTheDocumentPicksOne() {
        val schema = """
            {
                "type": "object",
                "oneOf": [
                    { "properties": { "kind": { "const": "a" }, "params": { "type": "object" } } },
                    { "properties": { "kind": { "const": "b" }, "params": { "type": "string" } } }
                ]
            }
        """
        assertPlain(schema, "<caret>", "params")
        assertPlain(schema, "kind: b\n<caret>", "params")
    }

    @Test
    fun branchNotMentioningThePropertyKeepsItPlain() {
        val schema = """
            {
                "type": "object",
                "anyOf": [ { "properties": { "p": { "type": "string" } } }, { "properties": { "q": { "type": "string" } } } ]
            }
        """
        assertPlain(schema, "<caret>", "p")
    }

    @Test
    fun branchForbiddingThePropertyDropsOut() {
        val schema = """
            {
                "type": "object",
                "anyOf": [
                    { "properties": { "p": { "type": "string" } } },
                    { "properties": { "q": { "type": "string" } }, "additionalProperties": false }
                ]
            }
        """
        assertEquals("p: '<caret>'", accept(schema, "<caret>", "p"))
    }

    @Test
    fun branchForbiddingAnEnclosingPropertyDropsOut() {
        val schema = """
            {
                "type": "object",
                "anyOf": [
                    { "properties": { "other": { "type": "string" } }, "additionalProperties": false },
                    { "properties": { "settings": { "type": "object", "properties": { "theme": { "type": "string" } } } } }
                ]
            }
        """
        assertEquals("settings:\n  theme: '<caret>'", accept(schema, "settings:\n  <caret>", "theme"))
    }

    @Test
    fun alternativeInsideABranchIntersectsWithTheRestOfIt() {
        val schema = withProperty(
            """
            {
                "anyOf": [
                    { "allOf": [ { "type": ["object", "string"] }, { "anyOf": [ { "type": "object" }, { "type": "array" } ] } ] },
                    { "type": "object", "required": ["x"] }
                ]
            }
            """
        )
        assertEquals("p: {<caret>}", accept(schema, "<caret>", "p"))
    }

    @Test
    fun undecidedIfWithoutElseStaysPlain() {
        assertPlain(withProperty("""{ "if": { "minLength": 2 }, "then": { "type": "string" } }"""), "<caret>", "p")
    }

    @Test
    fun ifTestingThePropertyStaysPlain() {
        val schema = """
            {
                "type": "object",
                "properties": { "p": {}, "q": {} },
                "if": { "properties": { "p": { "type": "string" } } },
                "then": { "properties": { "p": { "type": "string", "minLength": 1 } } },
                "else": { "properties": { "p": { "type": "object" } } }
            }
        """
        assertPlain(schema, "q: 1\n<caret>", "p")
    }

    @Test
    fun ifTestingThePropertyOpensTheBranchThatAllowsIt() {
        val schema = """
            {
                "type": "object",
                "properties": { "p": {}, "q": {} },
                "if": { "properties": { "p": { "type": "object" } } },
                "then": { "properties": { "q": {} }, "additionalProperties": false },
                "else": { "properties": { "p": { "type": "string" } } }
            }
        """
        assertEquals("q: 1\np: '<caret>'", accept(schema, "q: 1\n<caret>", "p"))
    }

    @Test
    fun ifOnAnEnclosingObjectTestingThePropertyStaysPlain() {
        val schema = """
            {
                "type": "object",
                "properties": { "settings": { "type": "object", "properties": { "p": {}, "q": {} } } },
                "if": { "properties": { "settings": { "properties": { "p": { "type": "string" } } } } },
                "then": { "properties": { "settings": { "properties": { "p": { "type": "string" } } } } },
                "else": { "properties": { "settings": { "properties": { "p": { "type": "object" } } } } }
            }
        """
        assertPlain(schema, "settings:\n  q: 1\n  <caret>", "p")
    }

    @Test
    fun ifStaysUndecidedThoughTheDocumentDecidesIt() {
        val schema = """
            {
                "type": "object",
                "properties": { "kind": {}, "p": {} },
                "if": { "properties": { "kind": { "const": "a" } } },
                "then": { "properties": { "p": { "type": "string" } } },
                "else": { "properties": { "p": { "type": "object" } } }
            }
        """
        assertPlain(schema, "kind: a\n<caret>", "p")
        assertPlain(schema, "kind: b\n<caret>", "p")
    }

    @Test
    fun alternativeReachedAgainOneLevelDownIsAChoiceOfItsOwn() {
        val schema = """
            {
                "${'$'}defs": {
                    "node": {
                        "anyOf": [
                            { "type": ["object", "string"], "properties": { "child": { "type": "object" } } },
                            { "type": ["object", "array"], "properties": { "child": { "type": "string" } } }
                        ],
                        "properties": { "child": { "${'$'}ref": "#/${'$'}defs/node" } }
                    }
                },
                "${'$'}ref": "#/${'$'}defs/node"
            }
        """
        assertPlain(schema, "<caret>", "child")
    }

    @Test
    fun alternativeReadUnderAnotherBaseUriIsAChoiceOfItsOwn() {
        val schema = """
            {
                "type": "object",
                "allOf": [ { "${'$'}ref": "#/${'$'}defs/a/${'$'}defs/shape" }, { "${'$'}ref": "#/${'$'}defs/b/${'$'}defs/shape" } ],
                "${'$'}defs": {
                    "a": {
                        "${'$'}id": "http://example.com/a.json",
                        "${'$'}defs": {
                            "shape": { "properties": { "p": { "anyOf": [ { "${'$'}ref": "#/${'$'}defs/first" }, { "${'$'}ref": "#/${'$'}defs/second" } ] } } },
                            "first": { "type": ["string", "object"] },
                            "second": { "type": "array" }
                        }
                    },
                    "b": {
                        "${'$'}id": "http://example.com/b.json",
                        "${'$'}defs": {
                            "shape": { "properties": { "p": { "anyOf": [ { "${'$'}ref": "#/${'$'}defs/first" }, { "${'$'}ref": "#/${'$'}defs/second" } ] } } },
                            "first": { "type": "string" },
                            "second": { "type": "object" }
                        }
                    }
                }
            }
        """
        assertPlain(schema, "<caret>", "p")
    }

    @Test
    fun basePropertyOpensWhenTheOtherBranchForbidsIt() {
        // Not accepted, as an item doesn't validate before its `kind` and `params` are typed
        assertEquals("name: '\$0'", completionFor(entrySchema, "entries:\n  first: {<caret>}", "name").snippetEdit?.newText)
    }

    @Test
    fun propertyThatIsAnObjectInEveryVariantOpensBraces() {
        assertEquals("params: {\$0}", completionFor(entrySchema, "entries:\n  first: {<caret>}", "params").snippetEdit?.newText)
        assertEquals(
            "entries:\n  first: {kind: a, params: {<caret>}}",
            accept(entrySchema, "entries:\n  first: {kind: a, <caret>}", "params")
        )
    }

    @Test
    fun allOfNestedInAnyOfStaysPlain() {
        assertPlain(withProperty("""{ "anyOf": [ { "allOf": [ { "type": "array" } ] }, {} ] }"""), "<caret>", "p")
    }

    @Test
    fun allOfNestedInIfThenStaysPlain() {
        val schema = withProperty("""{ "if": { "minItems": 2 }, "then": { "allOf": [ { "type": "array" } ] } }""")
        assertPlain(schema, "<caret>", "p")
    }

    @Test
    fun propertyDeclaredInsideAnAlternativeStaysPlain() {
        val schema = """
            {
                "type": "object",
                "anyOf": [ { "allOf": [ { "properties": { "p": { "type": "array" } } } ] }, {} ]
            }
        """
        assertPlain(schema, "<caret>", "p")
    }

    // Where the caret is

    @Test
    fun insertsIntoAnEmptyDocument() {
        assertEquals("name: '<caret>'", accept(keySchema, "<caret>", "name"))
    }

    @Test
    fun insertsOnANewLine() {
        assertEquals("tags: []\nname: '<caret>'", accept(keySchema, "tags: []\n<caret>", "name"))
    }

    @Test
    fun replacesATypedKeyEndingAtTheCaret() {
        assertEquals("tags: []\nname: '<caret>'", accept(keySchema, "tags: []\nna<caret>", "name"))
    }

    @Test
    fun replacesATypedKeyAroundTheCaret() {
        assertEquals("tags: []\nname: '<caret>'", accept(keySchema, "tags: []\nn<caret>am", "name"))
    }

    @Test
    fun typedKeyThatIsTheWholeDocumentDoesNotRuleOutABranch() {
        val schema = """{ "type": "object", "oneOf": [ { "type": "object", "properties": { "name": { "type": "string" } } } ] }"""
        assertEquals("name: '<caret>'", accept(schema, "na<caret>", "name"))
    }

    @Test
    fun insertsOnAnIndentedLineBelowItsObject() {
        assertEquals("settings:\n  theme: '<caret>'", accept(keySchema, "settings:\n  <caret>", "theme"))
    }

    @Test
    fun insertsAtTheValuePositionOfAnObjectProperty() {
        assertEquals("settings: theme: '<caret>'", accept(keySchema, "settings: <caret>", "theme"))
    }

    @Test
    fun insertsBetweenBraces() {
        assertEquals("settings: {theme: '<caret>'}", accept(keySchema, "settings: {<caret>}", "theme"))
    }

    @Test
    fun insertsAfterAComma() {
        assertEquals("{tags: [],name: '<caret>'}", accept(keySchema, "{tags: [],<caret>}", "name"))
        assertEquals("{tags: [], name: '<caret>'}", accept(keySchema, "{tags: [], <caret>}", "name"))
    }

    @Test
    fun quotedKeysStayPlain() {
        assertPlain(keySchema, "'na<caret>'", "name")
        assertPlain(keySchema, "'name'<caret>", "name")
    }

    @Test
    fun unclosedQuoteStaysPlain() {
        assertPlain(keySchema, "'na<caret>", "name")
    }

    @Test
    fun keywordAndNumberKeysStayPlain() {
        assertPlain(keySchema, "null<caret>", "name")
        assertPlain(keySchema, "true<caret>", "name")
        assertPlain(keySchema, "1<caret>", "name")
    }

    @Test
    fun keyFollowedByAColonStaysPlain() {
        assertPlain(keySchema, "na<caret> : 'x'", "name")
    }

    @Test
    fun caretTouchingAnotherTokenStaysPlain() {
        assertPlain(keySchema, "name: 'x'\n<caret>tags: []", "settings")
        assertPlain(keySchema, "settings: {}<caret>", "theme")
        assertPlain(keySchema, "settings:<caret>", "theme")
    }

    @Test
    fun caretPastTheEndOfTheDocumentStaysPlain() {
        for ((line, column) in listOf(0 to 5, 3 to 0)) {
            val completions = KsonTooling.getCompletionsAtLocation(
                KsonTooling.parse("na"), KsonTooling.parse(keySchema), line, column
            )
            assertEquals(listOf("name", "tags", "settings"), completions.map { it.label }, "at $line:$column")
            assertEquals(listOf(null, null, null), completions.map { it.snippetEdit }, "at $line:$column")
        }
    }

    // Where a key lands

    @Test
    fun keyTypedAfterAClosedValueOpensARootProperty() {
        for (document in listOf(
            "settings: {theme: dark}\nta<caret>",
            "settings: {size: 3}\nta<caret>",
            "settings: {x: true}\nta<caret>",
            "settings:\n  theme: dark.\nta<caret>",
            "items:\n  - {id: x}\nta<caret>"
        )) {
            assertEquals(document.replace("ta<caret>", "tags: [<caret>]"), accept(layoutSchema, document, "tags"))
        }
    }

    @Test
    fun emptyPositionAfterAClosedValueOpensARootProperty() {
        for (document in listOf(
            "settings: {theme: dark}\n<caret>",
            "settings: {size: 3}\n<caret>",
            "settings: {x: true}\n<caret>",
            "settings:\n  theme: dark.\n<caret>"
        )) {
            assertEquals(document.replace("<caret>", "tags: [<caret>]"), accept(layoutSchema, document, "tags"))
        }
    }

    @Test
    fun keyAfterAClosedValueInsideAnOpenObjectOpensItsProperty() {
        assertEquals(
            "settings:\n  theme: 'dark'\n  tags: '<caret>'",
            accept(layoutSchema, "settings:\n  theme: 'dark'\n  ta<caret>", "tags")
        )
        assertEquals(
            "outer:\n  inner: {x: y}\n  frame: {<caret>}",
            accept(layoutSchema, "outer:\n  inner: {x: y}\n  fr<caret>", "frame")
        )
        assertEquals(
            "outer:\n  inner: {x: y}\n  frame: {<caret>}",
            accept(layoutSchema, "outer:\n  inner: {x: y}\n  <caret>", "frame")
        )
    }

    @Test
    fun emptyDashBeforeAClosingBracketStaysPlain() {
        // The caret path falls back to the root, but a key typed here would start the list item
        assertPlain(layoutSchema, "{\n  items:\n    - <caret>\n}", "tags")
        assertPlain(layoutSchema, "items: <\n  - <caret>\n>", "tags")
    }

    @Test
    fun emptyDashItemOpensItsPropertyValue() {
        assertEquals("items:\n  - id: '<caret>'", accept(layoutSchema, "items:\n  - <caret>", "id"))
    }

    @Test
    fun keyNamedLikeThePlaceholderDoesNotMisleadTheCheck() {
        val existing = PropertySnippetBuilder.PLACEHOLDER_KEY
        val schema = """{ "type": "object", "properties": { "$existing": { "type": "string" }, "name": { "type": "string" } } }"""
        assertEquals("name: '<caret>'\n$existing: x", accept(schema, "<caret>\n$existing: x", "name"))
    }

    @Test
    fun parseErrorElsewhereStaysPlain() {
        assertPlain(keySchema, "<caret>\nsettings: {theme: 'x'", "name")
        assertEquals("name: '<caret>'\nsettings: {theme: 'x'}", accept(keySchema, "<caret>\nsettings: {theme: 'x'}", "name"))
    }

    // Arrays

    @Test
    fun itemOfAnArrayOpensThePropertyValue() {
        val schema = withProperty(
            """{ "type": "array", "items": { "type": "object", "properties": { "a": { "type": "string" } } } }"""
        )
        assertEquals("p: [{a: '<caret>'}]", accept(schema, "p: [{<caret>}]", "a"))
    }

    @Test
    fun additionalItemsFalseBesideAnItemsSchemaForbidsNoItem() {
        val schema = withProperty(
            """
            {
                "type": "array",
                "items": { "type": "object", "properties": { "a": { "type": "string" } } },
                "additionalItems": false
            }
            """
        )
        assertEquals("p: [{a: '<caret>'}]", accept(schema, "p: [{<caret>}]", "a"))
    }

    @Test
    fun additionalItemsWithoutATupleStaysOutOfIt() {
        val additionalItems = """"additionalItems": { "type": "object", "properties": { "a": { "type": "string" } } }"""
        assertPlain(
            withProperty("""{ "type": "array", "items": { "type": "object", "properties": { "a": {} } }, $additionalItems }"""),
            "p: [{<caret>}]",
            "a"
        )
        assertPlain(withProperty("""{ "type": "array", $additionalItems }"""), "p: [{<caret>}]", "a")
    }

    @Test
    fun itemOfATupleArrayOpensItsOwnSchema() {
        val schema = withProperty(
            """
            {
                "type": "array",
                "items": [ { "type": "object", "properties": { "a": { "type": "string" } } } ],
                "additionalItems": { "type": "object", "properties": { "a": { "type": "object" } } }
            }
            """
        )
        assertEquals("p: [{a: '<caret>'}]", accept(schema, "p: [{<caret>}]", "a"))
        assertEquals("p:\n  - a: '<caret>'", accept(schema, "p:\n  - <caret>", "a"))
        assertEquals("p: [{}, {a: {<caret>}}]", accept(schema, "p: [{}, {<caret>}]", "a"))
    }

    @Test
    fun propertyInsideATupleItemOpensItsOwnSchema() {
        val schema = withProperty(
            """
            {
                "type": "array",
                "items": [ {
                    "type": "object",
                    "properties": { "sub": { "type": "object", "properties": { "x": { "type": "string" } } } }
                } ],
                "additionalItems": {
                    "type": "object",
                    "properties": { "sub": { "type": "object", "properties": { "x": { "type": "object" } } } }
                }
            }
            """
        )
        assertEquals("p: [{sub: {x: '<caret>'}}]", accept(schema, "p: [{sub: {<caret>}}]", "x"))
    }

    @Test
    fun propertyInsideAPropertyNamedByANumberStaysPlain() {
        val schema = """
            {
                "type": "object",
                "properties": { "0": { "type": "object", "properties": { "a": { "type": "object" } } } },
                "items": { "type": "object", "properties": { "a": { "type": "string" } } }
            }
        """
        assertPlain(schema, "'0': {<caret>}", "a")
    }

    // The property name

    @Test
    fun namesWithSnippetMetacharactersStayPlain() {
        val schema = """
            {
                "type": "object",
                "properties": {
                    "a${'$'}b": { "type": "string" },
                    "a}b": { "type": "string" },
                    "a\\b": { "type": "string" }
                }
            }
        """
        for (name in listOf("a\$b", "a}b", "a\\b")) {
            assertPlain(schema, "<caret>", name)
        }
    }

    @Test
    fun namesThatAreNotBareKeysStayPlain() {
        val schema = """
            {
                "type": "object",
                "properties": {
                    "two words": { "type": "string" },
                    "null": { "type": "string" },
                    "1st": { "type": "string" }
                }
            }
        """
        for (name in listOf("two words", "null", "1st")) {
            assertPlain(schema, "<caret>", name)
        }
    }

    @Test
    fun valueCompletionsStayPlain() {
        // `additionalProperties` would open a property named `alpha` as an object
        val schema = withProperty(
            """{ "type": "string", "enum": ["alpha"], "additionalProperties": { "type": "object" } }"""
        )
        val completion = KsonTooling.getCompletionsAtLocation(
            KsonTooling.parse("p: "), KsonTooling.parse(schema), 0, 3
        ).single()
        assertEquals("alpha", completion.label)
        assertEquals(CompletionKind.VALUE, completion.kind)
        assertNull(completion.snippetEdit)
    }

    private companion object {
        const val CARET = "<caret>"

        val VALUE_OF_EACH_KIND = listOf("{}", "[]", "'x'", "1", "true", "null")
    }
}
