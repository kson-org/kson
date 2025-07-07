package org.kson.bindings

// Wrapper around `org.gradle.internal.os.OperatingSystem`, to make updates easier in case Gradle
// breaks the API in the future (it's internal after all)
object Platform {
    private val current = org.gradle.internal.os.OperatingSystem.current()

    val isLinux = current.isLinux
    val isMacOs = current.isMacOsX
    val isWindows = current.isWindows

    val symbolPrefix = when {
        isLinux -> "lib"
        else -> ""
    }

    val sharedLibraryName = when {
        isWindows -> "kson.dll"
        isLinux -> "libkson.so"
        isMacOs -> "kson.dylib"
        else -> throw Exception("Unsupported OS")
    }
}