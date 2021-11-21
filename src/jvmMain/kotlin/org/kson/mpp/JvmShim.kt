package org.kson.mpp

import java.io.File

actual fun getPlatformShim(): PlatformShim {
    return JvmShim()
}

class JvmShim : PlatformShim {
    override fun readLine(): String? {
        return kotlin.io.readLine()
    }

    override fun readFile(ksonFilename: String): String {
        return File(ksonFilename).readText()
    }

    override fun exitSuccess(): Nothing {
        kotlin.system.exitProcess(0)
    }

    override fun exitFailure(): Nothing {
        kotlin.system.exitProcess(-1)
    }
}