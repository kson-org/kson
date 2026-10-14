package org.kson

import org.gradle.internal.os.OperatingSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * The archive name is a contract with `lib-rust/kson-sys/build.rs`, which builds its
 * `kson-lib-shared-{arch}-{os}.tar.gz` download URL out of `CARGO_CFG_TARGET_ARCH` and
 * `CARGO_CFG_TARGET_OS`. The tokens are restated here rather than read from
 * [BinaryArtifactPaths], so a renamed one fails instead of agreeing with itself.
 */
class BinaryArtifactPathsTest {

    @Test
    fun releaseArchiveNamesAreTheOnesBuildRsFetches() {
        val arch = when (val osArch = System.getProperty("os.arch")) {
            "aarch64", "arm64" -> "arm64"
            "x86_64", "amd64" -> "amd64"
            else -> fail("unsupported host architecture: $osArch")
        }
        val os = when {
            OperatingSystem.current().isWindows -> "windows"
            OperatingSystem.current().isLinux -> "linux"
            OperatingSystem.current().isMacOsX -> "macos"
            else -> fail("unsupported host OS: ${OperatingSystem.current().familyName}")
        }

        assertEquals(
            "kson-lib-shared-$arch-$os.tar.gz",
            BinaryArtifactPaths.releaseArchiveName("kson-lib-shared")
        )
        assertEquals(
            "kson-cli-$arch-$os.tar.gz",
            BinaryArtifactPaths.releaseArchiveName("kson-cli")
        )
    }
}
