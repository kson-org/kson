package org.kson.parser.json


import org.kson.Kson
import org.kson.ParseResult
import org.kson.parser.LoggedMessage
import org.kson.readResourceAsString
import kotlin.test.*

/**
 * We execute the parsing tests provided by https://github.com/nst/JSONTestSuite.
 * Tests are organized into files with one of the following prefixes:
 * y_: content must be accepted by parsers
 * n_: content must be rejected by parsers
 * i_: parsers are free to accept or reject content
 */
class JSONParsingTests {

    @Test
    fun testJSONTestSuite() {
        val allTestCases = readResourceAsString("JSONTestSuite/parsing-test-cases-list.txt")
        val assertionErrorsByTestCase = LinkedHashMap<String, AssertionError>()

        for (testCase in allTestCases.split("\n")) {
            try {
                when {
                    testCase.isBlank() -> continue
                    testCase.startsWith("test_parsing/i_") ->
                        // don't care whether this parses, just don't blow up
                        runTestCase(testCase)
                    testCase.startsWith("test_parsing/n_") ->
                        assertInvalidKson(testCase)
                    testCase.startsWith("test_parsing/y_") ->
                        assertValidKson(testCase)
                    else ->
                        // we should never get here; all test cases should be prefixed with i_, n_, or y_
                        throw IllegalStateException("unexpected test file: $testCase")
                }
            } catch (assertionError: AssertionError) {
                // TODO: include file:// goodness for developers
                println("Failure in test case $testCase: ${assertionError.stackTraceToString()}")
                assertionErrorsByTestCase[testCase] = assertionError
            }
        }

        assertTrue(assertionErrorsByTestCase.isEmpty(), "${assertionErrorsByTestCase.size} test cases failed")
    }

    private fun assertValidKson(testCase: String): ParseResult {
        val result = runTestCase(testCase)
        val detailedMessage = LoggedMessage.print(result.messages)
        if (result.messages.isNotEmpty()) {
            // let's fail with formatted details
            fail("Expected no messages but got ${result.messages.size}: \n  $detailedMessage")
        }
        assertNotNull(result.ast, "Expected the parsed AST to be non-null")
        return result
    }

    private fun assertInvalidKson(testCase: String) {
        val result = runTestCase(testCase)
        assertTrue(result.messages.isNotEmpty()) // there must be some error messages
        assertNull(result.ast) // we should not have any AST generated
    }

    private fun runTestCase(testCase: String): ParseResult {
        val contents = readResourceAsString("JSONTestSuite/$testCase")
        return Kson.parse(contents)
    }
}
