package org.kson.cli

import org.kson.testSupport.PlatformShimStub
import kotlin.test.*

class CommandLineInterfaceTest {

    private enum class ExpectedExit {
        SUCCESS,
        FAILURE
    }

    /**
     * Constructs and runs a [CommandLineInterface] with [args] for test, asserting the [expectedExit] exit is received,
     * and returning the run's output as a string for further validation.
     *
     * Configure the input by customizing the given [platformShimStub]
     */
    private fun testCliRun(args: Array<String>, platformShimStub: PlatformShimStub, expectedExit: ExpectedExit): String {
        val cliOutput = StringBuilder()
        val cli = CommandLineInterface(platformShimStub) {
            cliOutput.appendLine(it)
        }

        when (expectedExit) {
            ExpectedExit.SUCCESS -> assertFailsWith<PlatformShimStub.Success> {
                cli.run(args)
            }
            ExpectedExit.FAILURE -> assertFailsWith<PlatformShimStub.Failure> {
                cli.run(args)
            }
        }

        return cliOutput.toString()
    }

    @Test
    fun testInteractiveInputMode() {
        val output = testCliRun(
            emptyArray(),
            PlatformShimStub(
                """
                    key: val
                    another_key: val
                """.trimIndent()
            ),
            ExpectedExit.SUCCESS
        )

        assertContains(
            output, """
                serializedAst: ```
                {
                  key: val
                  another_key: val
                }
                ```
            """.trimIndent(), false,
            "should get output confirming we've successfully parsed"
        )
    }

    @Test
    fun testTooManyArgs() {
        val tooManyArgs = arrayOf("one", "two", "three")
        val output = testCliRun(tooManyArgs, PlatformShimStub(), ExpectedExit.FAILURE)

        assertContains(output, Regex("^Usage:.*"), "should print a note about usage/correct args")
    }

    @Test
    fun testFileInputMode() {
        val fakeFilePath = "test/file/path"
        val fileInputArg = arrayOf(fakeFilePath)
        val output = testCliRun(
            fileInputArg,
            object : PlatformShimStub() {
                override fun readFile(ksonFilename: String): String {
                    assertEquals(fakeFilePath, ksonFilename, "should have ")
                    return """
                        file_key: file_val
                        another_key: val
                    """.trimIndent()
                }
            }, ExpectedExit.SUCCESS
        )

        assertContains(
            output, """
                serializedAst: ```
                {
                  file_key: file_val
                  another_key: val
                }
                ```
            """.trimIndent(), false,
            "should get output confirming we've successfully parsed the file contents"
        )
    }

    @Test
    fun testBadFileInputMode() {
        val output = testCliRun(arrayOf("ignored/test/path"), object : PlatformShimStub() {
            override fun readFile(ksonFilename: String): String {
                throw RuntimeException("This error simulates `readFile` throwing for some reason (bad filename, etc)")
            }
        }, ExpectedExit.FAILURE)

        assertContains(
            output, """
                Failed to read file at path: ignored/test/path
            """.trimIndent(), false,
            "should get file read error"
        )
    }
}