package org.kson.jetbrains.parser

import com.intellij.testFramework.ParsingTestCase
import org.kson.parser.messages.MessageType.BLANK_SOURCE

/**
 * These tests are powered by infrastructure provided by [ParsingTestCase]
 * ([see here for a related tutorial](https://plugins.jetbrains.com/docs/intellij/parsing-test.html#define-a-parsing-test)
 *
 * The tests here are all structured as follows:
 *
 * ```
 * @Test
 * fun testKsonFileName() {
 *     doTest(true)
 * }
 * ```
 *
 * By convention based on the test name, say `fun testKsonFileName()){...}` this will look for two files
 * inside [testData.parser]:
 * - `KsonFileName.kson` containing the Kson source to parse in the test
 * - `KsonFileName.txt` contains a text representation of the expected PSI tree to be parsed from `KsonFileName.kson`
 *
 * NOTES:
 * - if the `.txt` file does not exist for a test, it will be automatically generated on first run
 * - the [testData.parser] directory is configured by [getTestDataPath] and the first constructor
 *   arg to [ParsingTestCase]
 */
class KsonParserTest : ParsingTestCase("parser", "kson", KsonParserDefinition()) {

    /**
     * Sanity check parse of a file containing many Kson constructs
     */
    fun testManyConstructs() {
        doTest(true)
    }

    fun testObject() {
        doTest(true)
    }

    fun testNumber() {
        doTest(true)
    }

    fun testUnclosedEmbedBlock() {
        doTest(true)
    }

    fun testUnclosedString() {
        doTest(true)
    }

    fun testUnopenedList() {
        doTest(true)
    }

    fun testNumberError() {
        doTest(true)
    }

    fun testNumberDanglingExponentError() {
        doTest(true)
    }

    fun testTwoConsecutiveStrings() {
        doTest(true)
    }

    fun testEmptyCommaError() {
        doTest(true)
    }

    fun testDelimitedDashList() {
        doTest(true)
    }

    /**
     * Sanity check we do not error on a blank file: strictly speaking, an empty Kson file should produce
     * a [BLANK_SOURCE] error, but in an editor, it makes no sense to be so strict
     */
    fun testBlankFile() {
        doTest(true)
    }

    /**
     * @return path to test data file directory relative to root of this module
     */
    override fun getTestDataPath(): String {
        return "src/test/resources/testData"
    }

    override fun skipSpaces(): Boolean {
        return true
    }

    override fun includeRanges(): Boolean {
        return true
    }
}