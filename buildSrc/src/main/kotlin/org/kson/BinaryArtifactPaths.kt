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
     * The name of the release archive carrying [artifactName] built on this platform, for example
     * `kson-lib-shared-arm64-macos.tar.gz`.
     *
     * Every release artifact we collect is named this way so that the CI output of a release build
     * can be uploaded as-is (see `docs/release_process.md`), and because for `kson-lib` the name is
     * a hard contract: `lib-rust/kson-sys/build.rs` downloads
     * `kson-lib-shared-{arch}-{os}.tar.gz` from the `kson-binaries` release, so an archive named
     * anything else is one nobody can fetch.
     */
    fun releaseArchiveName(artifactName: String) : String = "$artifactName-${platformToken()}.tar.gz"

    /**
     * The `<arch>-<os>` token identifying this platform in release artifact names, e.g. `arm64-macos`.
     */
    fun platformToken() : String = "${archToken(System.getProperty("os.arch"))}-${osToken(os)}"

    /**
     * Maps a JVM `os.arch` to the architecture token used in release artifact names.
     *
     * The spellings are the ones `lib-rust/kson-sys/build.rs` maps Rust's `CARGO_CFG_TARGET_ARCH`
     * onto, and must stay in step with it.
     */
    fun archToken(osArch: String) : String {
        return when (osArch.lowercase()) {
            "aarch64", "arm64" -> "arm64"
            "x86_64", "amd64" -> "amd64"
            else -> throw Exception("Unsupported CPU architecture: $osArch")
        }
    }

    /**
     * Maps an operating system to the OS token used in release artifact names.
     *
     * The spellings are Rust's `CARGO_CFG_TARGET_OS` values, which `lib-rust/kson-sys/build.rs`
     * drops into the download URL unchanged, and must stay in step with it.
     */
    fun osToken(os: OperatingSystem) : String {
        return when {
            os.isWindows -> "windows"
            os.isLinux -> "linux"
            os.isMacOsX -> "macos"
            // `name` is the host's `os.name` whatever instance we were handed, so report the family
            else -> throw Exception("Unsupported OS: ${os.familyName}")
        }
    }
}
