package org.kson.testSupport

import org.kson.cli.CommandLineInterface
import org.kson.mpp.PlatformShim

/**
 * Customizable stub implementation of [PlatformShim].  Customize through the constructor,
 * and optionally overriding [PlatformShimStub.readFile] to return stubbed file contents for test.
 *
 * @param interactiveInput the input to provide line-by-line from [readLine]
 */
open class PlatformShimStub(interactiveInput: String? = null) : PlatformShim {
    private val linesToRead = interactiveInput?.split("\n")?.toMutableList()

    /**
     * Customize the lines returned by [readLine] by passing a [String] as `interactiveInput` in the constructor
     */
    final override fun readLine(): String? {
        if (linesToRead == null) {
            throw RuntimeException("No interactiveInput provided in constructor, so should call readline()")
        }
        if (linesToRead.isEmpty()) {
            return null
        }

        return linesToRead.removeFirst()
    }

    /**
     * Override this to return stub file contents for tests which are exercising the [CommandLineInterface]'s
     * file reading mode
     */
    override fun readFile(ksonFilename: String): String {
        throw RuntimeException("Override to return the file contents desired for this test")
    }

    final override fun exitSuccess(): Nothing {
        throw SimulatedSuccessExit()
    }

    final override fun exitFailure(): Nothing {
        throw SimulatedFailureExit()
    }

    /**
     * Since our exit methods return [`Nothing`](https://kotlinlang.org/api/latest/jvm/stdlib/kotlin/-nothing.html),
     * we must stub them with exceptions
     */
    abstract class SimulatedExit(msg: String): RuntimeException(msg)
    class SimulatedSuccessExit: SimulatedExit("Fake success exit")
    class SimulatedFailureExit: SimulatedExit("Fake failure exit")
}