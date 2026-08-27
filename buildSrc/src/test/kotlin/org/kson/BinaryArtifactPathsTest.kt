package org.kson

import org.gradle.internal.os.OperatingSystem
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The tokens asserted here are a contract with `lib-rust/kson-sys/build.rs`, which builds
 * `https://github.com/kson-org/kson-binaries/releases/download/kson-lib-{VERSION}/kson-lib-shared-{arch}-{os}.tar.gz`
 * out of Rust's `CARGO_CFG_TARGET_ARCH`/`CARGO_CFG_TARGET_OS`. Renaming a token here without
 * renaming it there leaves every Rust build downloading a URL that 404s, so these are spelled out
 * literally rather than derived.
 */
class BinaryArtifactPathsTest {

    @Test
    fun archTokensCoverTheSpellingsJvmsReport() {
        // `aarch64` on every JVM we ship on; `arm64` is what some older/Apple-flavoured JDKs say
        assertEquals("arm64", BinaryArtifactPaths.archToken("aarch64"))
        assertEquals("arm64", BinaryArtifactPaths.archToken("arm64"))
        // `amd64` on Linux and Windows JVMs, `x86_64` on macOS ones
        assertEquals("amd64", BinaryArtifactPaths.archToken("amd64"))
        assertEquals("amd64", BinaryArtifactPaths.archToken("x86_64"))
    }

    @Test
    fun archTokensAreCaseInsensitive() {
        assertEquals("arm64", BinaryArtifactPaths.archToken("AARCH64"))
        assertEquals("amd64", BinaryArtifactPaths.archToken("AMD64"))
    }

    @Test
    fun unsupportedArchIsRejectedByName() {
        val failure = assertFailsWith<Exception> { BinaryArtifactPaths.archToken("riscv64") }
        assertContains(
            failure.message!!, "riscv64",
            message = "the unsupported architecture should be named, since it is the one thing the caller needs to know"
        )
    }

    @Test
    fun osTokensAreRustsTargetOsSpellings() {
        assertEquals("windows", BinaryArtifactPaths.osToken(OperatingSystem.WINDOWS))
        assertEquals("linux", BinaryArtifactPaths.osToken(OperatingSystem.LINUX))
        assertEquals("macos", BinaryArtifactPaths.osToken(OperatingSystem.MAC_OS))
    }

    @Test
    fun unsupportedOsIsRejectedByName() {
        // we ship on three platforms; anything else must fail loudly rather than pick a token
        val failure = assertFailsWith<Exception> { BinaryArtifactPaths.osToken(OperatingSystem.forName("SunOS")) }
        assertContains(
            failure.message!!, "solaris",
            message = "the rejected OS should be named, not the host we happen to be running on"
        )
    }

    @Test
    fun releaseArchiveNamesAreTheOnesBuildRsFetches() {
        // spelled out end-to-end for the platform we are running on: the pieces above can each be
        // right while the assembled name -- the thing that actually has to match a URL -- is not
        val expectedToken = "${BinaryArtifactPaths.archToken(System.getProperty("os.arch"))}-" +
            BinaryArtifactPaths.osToken(OperatingSystem.current())
        assertEquals(
            "kson-lib-shared-$expectedToken.tar.gz",
            BinaryArtifactPaths.releaseArchiveName("kson-lib-shared")
        )
        assertEquals(
            "kson-cli-$expectedToken.tar.gz",
            BinaryArtifactPaths.releaseArchiveName("kson-cli")
        )
    }
}
