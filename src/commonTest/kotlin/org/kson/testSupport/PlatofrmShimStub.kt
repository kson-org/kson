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
        throw Success()
    }

    override fun exitFailure(): Nothing {
        throw Failure()
    }

    class Success: RuntimeException("Fake success exit")
    class Failure: RuntimeException("Fake failure exit")
}