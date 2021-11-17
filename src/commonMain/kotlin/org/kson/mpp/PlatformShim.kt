package org.kson.mpp

/**
 * This interface represents the platform-specific behavior Kson depends on.  This should naturally be
 * kept as slender and simple as possible, surgically targeting only strictly necessary and simple
 * utilities
 */
interface PlatformShim {
    /**
     * Reads line-by-line from the platform's standard input, blocking while it waits on input
     */
    fun readLine(): String?

    /**
     * Reads the file at absolute path `ksonFilename` from disk
     */
    fun readFile(ksonFilename: String): String

    /**
     * Exits the current process, noting success as appropriate for the platform
     */
    fun exitSuccess(): Nothing

    /**
     * Exits the current process, noting failure as appropriate for the platform
     */
    fun exitFailure(): Nothing
}

expect fun getPlatformShim(): PlatformShim

/**
 * Custom [TODO]-style exception for marking platform-specific unimplemented (yet!) behavior
 */
@Suppress("FunctionName") // allow the all-caps here
inline fun PLATFORM_TODO(): Nothing =
    throw NotImplementedError("This operation is not supported on this platform, but is planned for the future")