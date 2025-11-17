package org.kson

import kotlin.test.*

/**
 * Tests for the public [Kson] interface.  Note we explicitly call this out as a [KsonSmokeTest]: since the underlying
 * code that [Kson] puts an interface on it well-tested, we only need to smoke test each [Kson] method to be
 * confident in this code
 */
class KsonSmokeTest {
    
    @Test
    fun testFormat_withDefaultOptions() {
        val input = """{"name": "test", "value": 123}"""
        val formatted = Kson.format(input)
        assertEquals("""
              name: test
              value: 123
            """.trimIndent(),
            formatted)
    }
    
    @Test
    fun testFormat_withSpacesOption() {
        val input = """{"name": "test", "value": 123}"""
        val formatted = Kson.format(input, FormatOptions(IndentType.Spaces(6)))
        assertEquals("""
                  name: test
                  value: 123
            """.trimIndent(),
            formatted)
    }

    @Test
    fun testFormat_withDelimitedOption() {
        val input = """{"name": "test", "list": [1, 2, 3]}"""
        val formatted = Kson.format(input, FormatOptions(formattingStyle = FormattingStyle.DELIMITED))
        assertEquals(
            """
            {
              name: test
              list: <
                - 1
                - 2
                - 3
              >
            }
        """.trimIndent(),
            formatted
        )
    }
    
    @Test
    fun testFormat_withTabsOption() {
        val input = """{"name": "test", "value": 123}"""
        val result = Kson.format(input, FormatOptions(IndentType.Tabs))
        assertIs<String>(result)
        assertTrue(result.isNotEmpty())
    }
    
    @Test
    fun testToJson_success() {
        val input = """{"name": "test", "value": 123}"""
        val result = Kson.toJson(input)
        assertIs<Result.Success>(result)
        assertTrue(result.output.isNotEmpty())
    }
    
    @Test
    fun testToJson_failure() {
        val input = """{"invalid": }"""
        val result = Kson.toJson(input)
        assertIs<Result.Failure>(result)
        assertTrue(result.errors.isNotEmpty())
        val error = result.errors.first()
        assertIs<String>(error.message)
        assertIs<Position>(error.start)
        assertIs<Position>(error.end)
        assertTrue(error.start.line == 0)
        assertTrue(error.start.column > 0)
    }
    
    @Test
    fun testToYaml_success() {
        val input = """{"name": "test", "value": 123}"""
        val result = Kson.toYaml(input)
        assertIs<Result.Success>(result)
        assertTrue(result.output.isNotEmpty())
    }
    
    @Test
    fun testToYaml_failure() {
        val input = """{"invalid": }"""
        val result = Kson.toYaml(input)
        assertIs<Result.Failure>(result)
        assertTrue(result.errors.isNotEmpty())
    }
    
    @Test
    fun testAnalyze() {
        val input = """{"name": "test", "value": 123}"""
        val analysis = Kson.analyze(input)
        assertIs<Analysis>(analysis)
        assertIs<List<Message>>(analysis.errors)
        assertIs<List<Token>>(analysis.tokens)
        assertTrue(analysis.tokens.isNotEmpty())

        val token = analysis.tokens.first()
        assertIs<TokenType>(token.tokenType)
        assertIs<String>(token.text)
        assertIs<Position>(token.start)
        assertIs<Position>(token.end)
    }

    @Test
    fun testAnalysisUnclosedString() {
        val analysis = Kson.analyze("'unclosed string")
        assertIs<Analysis>(analysis)
        assertIs<List<Message>>(analysis.errors)
        assertIs<List<Token>>(analysis.tokens)
        assertTrue(analysis.tokens.isNotEmpty())

        val token = analysis.tokens.first()
        assertIs<TokenType>(token.tokenType)
        assertIs<String>(token.text)
        assertIs<Position>(token.start)
        assertIs<Position>(token.end)
    }

    @Test
    fun testAnalyze_tokens() {
        val input = """name: test, complexString: "this has legal \n and illegal \x escapes and \u3456 unicode""""
        val tokens = Kson.analyze(input).tokens
        assertEquals(
            listOf(TokenType.UNQUOTED_STRING,
                TokenType.COLON,
                TokenType.UNQUOTED_STRING,
                TokenType.COMMA,
                TokenType.UNQUOTED_STRING,
                TokenType.COLON,
                TokenType.STRING_OPEN_QUOTE,
                TokenType.STRING_CONTENT,
                TokenType.STRING_CLOSE_QUOTE,
                TokenType.EOF),
            tokens.map { it.tokenType })
    }

    @Test
    fun testAnalyze_value() {
        val input = """
            key: value
            list:
              - 1
              - 2.1
              - 3E5
            embed:%tag
            %%""".trimIndent()
        val value = Kson.analyze(input).ksonValue
        assertNotNull(value)
        assertTrue(value is KsonValue.KsonObject)

        assertEquals(3, value.properties.size)

        // Check root object location (should span entire document)
        assertEquals(0, value.start.line)
        assertEquals(0, value.start.column)
        assertEquals(6, value.end.line)
        assertEquals(2, value.end.column)

        // Check "key" property
        val keyValue = value.properties.get("key")
        assertTrue(keyValue is KsonValue.KsonString)
        assertEquals("value", keyValue.value)
        assertEquals(0, keyValue.start.line)
        assertEquals(5, keyValue.start.column)
        assertEquals(0, keyValue.end.line)
        assertEquals(10, keyValue.end.column)

        // Check "list" property
        val listValue = value.properties.get("list")
        assertTrue(listValue is KsonValue.KsonArray)
        assertEquals(3, listValue.elements.size)
        assertEquals(2, listValue.start.line)
        assertEquals(2, listValue.start.column)
        assertEquals(4, listValue.end.line)
        assertEquals(7, listValue.end.column)

        // Check list elements
        val firstElement = listValue.elements[0]
        assertTrue(firstElement is KsonValue.KsonNumber.Integer)
        assertEquals(1, firstElement.value)
        assertEquals(2, firstElement.start.line)
        assertEquals(4, firstElement.start.column)
        assertEquals(2, firstElement.end.line)
        assertEquals(5, firstElement.end.column)

        val secondElement = listValue.elements[1]
        assertTrue(secondElement is KsonValue.KsonNumber.Decimal)
        assertEquals(2.1, secondElement.value)
        assertEquals(3, secondElement.start.line)
        assertEquals(4, secondElement.start.column)
        assertEquals(3, secondElement.end.line)
        assertEquals(7, secondElement.end.column)

        val thirdElement = listValue.elements[2]
        assertTrue(thirdElement is KsonValue.KsonNumber.Decimal)
        assertEquals(3e5, thirdElement.value)
        assertEquals(4, thirdElement.start.line)
        assertEquals(4, thirdElement.start.column)
        assertEquals(4, thirdElement.end.line)
        assertEquals(7, thirdElement.end.column)

        // Check "embed" property
        val embedValue = value.properties.get("embed")
        assertTrue(embedValue is KsonValue.KsonEmbed)
        assertEquals("tag", embedValue.tag)
        assertEquals("", embedValue.content)
        assertEquals(5, embedValue.start.line)
        assertEquals(6, embedValue.start.column)
        assertEquals(6, embedValue.end.line)
        assertEquals(2, embedValue.end.column)
    }
    
    @Test
    fun testParseSchema_success() {
        val schemaKson = """{
            "type": "object",
            "properties": {
                "name": {"type": "string"},
                "age": {"type": "number"}
            }
        }"""
        val result = Kson.parseSchema(schemaKson)
        assertIs<SchemaResult.Success>(result)
        assertIs<SchemaValidator>(result.schemaValidator)
    }
    
    @Test
    fun testParseSchema_failure() {
        val invalidSchema = """{"type": }"""
        val result = Kson.parseSchema(invalidSchema)
        assertIs<SchemaResult.Failure>(result)
        assertTrue(result.errors.isNotEmpty())
    }
    
    @Test
    fun testSchemaValidator_validInput() {
        val schemaKson = """{
            "type": "object",
            "properties": {
                "name": {"type": "string"},
                "age": {"type": "number"}
            }
        }"""
        val schemaResult = Kson.parseSchema(schemaKson)
        assertIs<SchemaResult.Success>(schemaResult)
        
        val validator = schemaResult.schemaValidator
        val validKson = """{"name": "John", "age": 30}"""
        val errors = validator.validate(validKson)
        assertTrue(errors.isEmpty())
    }
    
    @Test
    fun testSchemaValidator_invalidInput() {
        val schemaKson = """{
            "type": "object",
            "properties": {
                "name": {"type": "string"},
                "age": {"type": "number"}
            },
            "required": ["name", "age"]
        }"""
        val schemaResult = Kson.parseSchema(schemaKson)
        assertIs<SchemaResult.Success>(schemaResult)
        
        val validator = schemaResult.schemaValidator
        val invalidKson = """{"name": "John"}"""
        val errors = validator.validate(invalidKson)
        assertTrue(errors.isNotEmpty())
    }
    
    @Test
    fun testSchemaValidator_validateWithParseErrors() {
        val schemaKson = """{"type": "object"}"""
        val schemaResult = Kson.parseSchema(schemaKson)
        assertIs<SchemaResult.Success>(schemaResult)

        val validator = schemaResult.schemaValidator
        val invalidKson = """{"invalid": }"""
        val errors = validator.validate(invalidKson)
        assertTrue(errors.isNotEmpty())
    }

    @Test
    fun testPropertyKeys_basicAccess() {
        val input = """
            name: John
            age: 30
            city: 'New York'
        """.trimIndent()
        val analysis = Kson.analyze(input)
        val value = analysis.ksonValue
        assertNotNull(value)
        assertTrue(value is KsonValue.KsonObject)

        // Verify all keys are present in propertyKeys
        assertEquals(3, value.propertyKeys.size)
        assertTrue(value.propertyKeys.containsKey("name"))
        assertTrue(value.propertyKeys.containsKey("age"))
        assertTrue(value.propertyKeys.containsKey("city"))

        // Verify propertyKeys contains KsonString values
        val nameKey = value.propertyKeys.get("name")
        assertNotNull(nameKey)
        assertEquals("name", nameKey.value)

        val ageKey = value.propertyKeys.get("age")
        assertNotNull(ageKey)
        assertEquals("age", ageKey.value)
    }

    @Test
    fun testPropertyKeys_withPositionInformation() {
        val input = """
            name: John
            age: 30
        """.trimIndent()
        val analysis = Kson.analyze(input)
        val value = analysis.ksonValue
        assertNotNull(value)
        assertTrue(value is KsonValue.KsonObject)

        // Verify position information for keys
        val nameKey = value.propertyKeys.get("name")
        assertNotNull(nameKey)
        assertEquals(0, nameKey.start.line)
        assertEquals(0, nameKey.start.column)
        assertEquals(0, nameKey.end.line)
        assertEquals(4, nameKey.end.column)

        val ageKey = value.propertyKeys.get("age")
        assertNotNull(ageKey)
        assertEquals(1, ageKey.start.line)
        assertEquals(0, ageKey.start.column)
        assertEquals(1, ageKey.end.line)
        assertEquals(3, ageKey.end.column)
    }

    @Test
    fun testPropertyKeys_emptyObject() {
        val input = "{}"
        val analysis = Kson.analyze(input)
        val value = analysis.ksonValue
        assertNotNull(value)
        assertTrue(value is KsonValue.KsonObject)

        // Empty object should have no propertyKeys
        assertEquals(0, value.propertyKeys.size)
        assertEquals(0, value.properties.size)
    }
}
