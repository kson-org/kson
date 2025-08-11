package org.kson

import org.gradle.internal.os.OperatingSystem

object BinaryArtifactPaths {
    val os: OperatingSystem = OperatingSystem.current()

    fun binaryFileName() : String {
        return when {
            os.isWindows -> "kson.dll"
            os.isLinux -> "libkson.so"
            os.isMacOsX -> "kson.dylib"
            else -> throw Exception("Unsupported OS")
        }
    }

    fun headerFileName() : String {
        return when {
            os.isWindows -> "kson_api.h"
            os.isLinux -> "libkson_api.h"
            os.isMacOsX -> "kson_api.h"
            else -> throw Exception("Unsupported OS")
        }
    }
}