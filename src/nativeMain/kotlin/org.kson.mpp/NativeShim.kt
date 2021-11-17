package org.kson.mpp

import platform.posix.EXIT_FAILURE
import platform.posix.EXIT_SUCCESS

actual fun getPlatformShim(): PlatformShim {
    return NativeShim()
}

class NativeShim : PlatformShim {
    override fun readLine(): String? {
        return kotlin.io.readLine()
    }

     override fun readFile(ksonFilename: String): String {
         PLATFORM_TODO()
    }

    override fun exitSuccess(): Nothing {
        kotlin.system.exitProcess(EXIT_SUCCESS)
    }

    override fun exitFailure(): Nothing {
        kotlin.system.exitProcess(EXIT_FAILURE)
    }
}