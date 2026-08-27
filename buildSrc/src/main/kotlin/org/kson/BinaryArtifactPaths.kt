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
            os.isMacOsX -> "libkson.dylib"
            else -> throw Exception("Unsupported OS")
        }
    }

    fun binaryFileNameWithoutExtension() : String = binaryFileName().substringBeforeLast('.')

    /**
     * The release archive name for [artifactName] on this platform, e.g.
     * `kson-lib-shared-arm64-macos.tar.gz`, so CI output can be uploaded as-is.
     *
     * For `kson-lib` this is a contract: `lib-rust/kson-sys/build.rs` downloads
     * `kson-lib-shared-{arch}-{os}.tar.gz` from the `kson-binaries` release, so any other name is
     * one nobody can fetch.
     */
    fun releaseArchiveName(artifactName: String) : String = "$artifactName-${platformToken()}.tar.gz"

    /**
     * The `<arch>-<os>` token identifying this platform in release artifact names, e.g. `arm64-macos`.
     */
    private fun platformToken() : String = "${archToken(System.getProperty("os.arch"))}-${osToken(os)}"

    /**
     * Maps a JVM `os.arch` to the architecture token in release artifact names. The spellings are
     * what `build.rs` maps `CARGO_CFG_TARGET_ARCH` onto, and must stay in step with it.
     */
    private fun archToken(osArch: String) : String {
        return when (osArch) {
            "aarch64", "arm64" -> "arm64"
            "x86_64", "amd64" -> "amd64"
            else -> throw Exception("Unsupported CPU architecture: $osArch")
        }
    }

    /**
     * Maps an operating system to the OS token in release artifact names. The spellings are Rust's
     * `CARGO_CFG_TARGET_OS` values, which `build.rs` drops into the download URL unchanged.
     */
    private fun osToken(os: OperatingSystem) : String {
        return when {
            os.isWindows -> "windows"
            os.isLinux -> "linux"
            os.isMacOsX -> "macos"
            // report the family, since `name` is the host's `os.name` whichever instance this is
            else -> throw Exception("Unsupported OS: ${os.familyName}")
        }
    }
}
