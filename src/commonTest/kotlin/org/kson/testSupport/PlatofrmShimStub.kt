package org.kson.testSupport

import org.kson.mpp.PlatformShim

/**
 * Customizable stub implementation of [PlatformShim].  Customize by overriding methods in a subclass.
 *
 * @param interactiveInput the input to provide line-by-line from [readLine]
 */
open class PlatformShimStub(interactiveInput: String? = null) : PlatformShim {
    private val linesToRead = interactiveInput?.split("\n")?.toMutableList()
    override fun readLine(): String? {
        if (linesToRead == null) {
            throw RuntimeException("No interactiveInput provided in constructor, so should call readline()")
        }
        if (linesToRead.isEmpty()) {
            return null
        }
        return linesToRead.removeFirst()
    }

    override fun readFile(ksonFilename: String): String {
        throw RuntimeException("Override to return the file contents desired for this test")
    }

    override fun exitSuccess(): Nothing {
        throw SimulatedSuccessExit()
    }

    override fun exitFailure(): Nothing {
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