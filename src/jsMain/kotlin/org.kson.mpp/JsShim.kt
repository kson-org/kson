package org.kson.mpp

actual fun getPlatformShim(): PlatformShim {
    return JsShim()
}

class JsShim: PlatformShim {
    override fun readLine(): String? {
        PLATFORM_TODO()
    }

    override fun readFile(ksonFilename: String): String {
        PLATFORM_TODO()
    }

    override fun exitSuccess(): Nothing {
        PLATFORM_TODO()
    }

    override fun exitFailure(): Nothing {
        PLATFORM_TODO()
    }
}