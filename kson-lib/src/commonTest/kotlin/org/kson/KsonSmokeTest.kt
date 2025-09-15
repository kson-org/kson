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
}
