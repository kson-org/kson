package org.kson.jetbrains.parser

import com.intellij.testFramework.ParsingTestCase
import org.junit.Test

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
    @Test
    fun testManyConstructs() {
        doTest(true)
    }

    @Test
    fun testObject() {
        doTest(true)
    }

    @Test
    fun testNumber() {
        doTest(true)
    }

    @Test
    fun testUnclosedEmbedBlock() {
        doTest(true)
    }

    @Test
    fun testUnclosedString() {
        doTest(true)
    }

    @Test
    fun testNumberError() {
        doTest(true)
    }

    @Test
    fun testNumberDanglingExponentError() {
        doTest(true)
    }

    @Test
    fun testTwoConsecutiveStrings() {
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