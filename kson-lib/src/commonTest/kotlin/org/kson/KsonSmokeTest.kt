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

        val mappedProperties = value.properties.map { it.key.value to it.value }.toMap()
        // Check "key" property
        val keyValue = mappedProperties["key"]
        assertTrue(keyValue is KsonValue.KsonString)
        assertEquals("value", keyValue.value)
        assertEquals(0, keyValue.start.line)
        assertEquals(5, keyValue.start.column)
        assertEquals(0, keyValue.end.line)
        assertEquals(10, keyValue.end.column)

        // Check "list" property
        val listValue = mappedProperties["list"]
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
        val embedValue = mappedProperties["embed"]
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
    fun testGetPropertyByName_success() {
        val input = """
            name: John
            age: 30
            city: 'New York'
        """.trimIndent()
        val analysis = Kson.analyze(input)
        val value = analysis.ksonValue
        assertNotNull(value)
        assertTrue(value is KsonValue.KsonObject)

        val nameValue = value.getPropertyByName("name")
        assertNotNull(nameValue)
        assertTrue(nameValue is KsonValue.KsonString)
        assertEquals("John", nameValue.value)

        val ageValue = value.getPropertyByName("age")
        assertNotNull(ageValue)
        assertTrue(ageValue is KsonValue.KsonNumber.Integer)
        assertEquals(30, ageValue.value)

        val cityValue = value.getPropertyByName("city")
        assertNotNull(cityValue)
        assertTrue(cityValue is KsonValue.KsonString)
        assertEquals("New York", cityValue.value)
    }

    @Test
    fun testGetPropertyByName_withNestedObject() {
        val input = """
            person: 
              name: Alice
              age: 25
        """.trimIndent()
        val analysis = Kson.analyze(input)
        val value = analysis.ksonValue
        assertNotNull(value)
        assertTrue(value is KsonValue.KsonObject)

        val personValue = value.getPropertyByName("person")
        assertNotNull(personValue)
        assertTrue(personValue is KsonValue.KsonObject)

        val nestedName = personValue.getPropertyByName("name")
        assertNotNull(nestedName)
        assertTrue(nestedName is KsonValue.KsonString)
        assertEquals("Alice", nestedName.value)
    }

    @Test
    fun testGetPropertyByName_nonExistentProperty() {
        val input = """
            name: John
            age: 30
        """.trimIndent()
        val analysis = Kson.analyze(input)
        val value = analysis.ksonValue
        assertNotNull(value)
        assertTrue(value is KsonValue.KsonObject)

        val nonExistentValue = value.getPropertyByName("nonexistent")
        assertNull(nonExistentValue)
    }

    @Test
    fun testGetPropertyByName_withDifferentValueTypes() {
        val input = """
            string: "text"
            number: 42
            decimal: 3.14
            boolean: true
            null_value: null
            array: [1, 2, 3]
            object: {key: value}
        """.trimIndent()
        val analysis = Kson.analyze(input)
        val value = analysis.ksonValue
        assertNotNull(value)
        assertTrue(value is KsonValue.KsonObject)

        val stringValue = value.getPropertyByName("string")
        assertNotNull(stringValue)
        assertTrue(stringValue is KsonValue.KsonString)
        assertEquals("text", stringValue.value)

        val numberValue = value.getPropertyByName("number")
        assertNotNull(numberValue)
        assertTrue(numberValue is KsonValue.KsonNumber.Integer)
        assertEquals(42, numberValue.value)

        val decimalValue = value.getPropertyByName("decimal")
        assertNotNull(decimalValue)
        assertTrue(decimalValue is KsonValue.KsonNumber.Decimal)
        assertEquals(3.14, decimalValue.value)

        val boolValue = value.getPropertyByName("boolean")
        assertNotNull(boolValue)
        assertTrue(boolValue is KsonValue.KsonBoolean)
        assertEquals(true, boolValue.value)

        val nullValue = value.getPropertyByName("null_value")
        assertNotNull(nullValue)
        assertTrue(nullValue is KsonValue.KsonNull)

        val arrayValue = value.getPropertyByName("array")
        assertNotNull(arrayValue)
        assertTrue(arrayValue is KsonValue.KsonArray)
        assertEquals(3, arrayValue.elements.size)

        val objectValue = value.getPropertyByName("object")
        assertNotNull(objectValue)
        assertTrue(objectValue is KsonValue.KsonObject)
    }
}
