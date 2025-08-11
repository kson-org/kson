package org.kson

import org.gradle.internal.os.OperatingSystem

/**
 * The artifacts produced by kotlin-multiplatform have different names depending on the platform.
 * This object provides helper methods to obtain the file names with minimal hassle.
 */
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