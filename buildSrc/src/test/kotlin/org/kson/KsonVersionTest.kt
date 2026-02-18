package org.kson

import org.eclipse.jgit.api.Git
import java.io.File
import java.nio.file.Files.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class KsonVersionTest {

    private fun createTempGitRepo(): File {
        val repoDir = createTempDirectory("KsonVersionTest").toFile()
        Git.init().setDirectory(repoDir).call().use { git ->
            File(repoDir, "initial.txt").writeText("initial content")
            git.add().addFilepattern(".").call()
            git.commit().setMessage("Initial commit").call()
        }
        return repoDir
    }

    @Test
    fun snapshotVersionIsStable() {
        assertEquals("0.3.0-SNAPSHOT", KsonVersion.getVersion(isRelease = false))
    }

    @Test
    fun releaseVersionIsBareBaseVersion() {
        assertEquals("0.3.0", KsonVersion.getVersion(isRelease = true))
    }

    @Test
    fun customBaseVersion() {
        assertEquals("x.4-SNAPSHOT", KsonVersion.getVersion("x.4", isRelease = false))
        assertEquals("x.4", KsonVersion.getVersion("x.4", isRelease = true))
    }

    @Test
    fun publishVersionIncludesShaForSnapshots() {
        val repoDir = createTempGitRepo()
        val version = KsonVersion.getPublishVersion(repoDir, isRelease = false)
        assertTrue(
            Regex("""0\.3\.0-[0-9a-f]{8}-SNAPSHOT""").matches(version),
            "Expected SHA-qualified snapshot version, got: $version"
        )
    }

    @Test
    fun publishVersionIncludesShaForSnapshotsWithCustomBase() {
        val repoDir = createTempGitRepo()
        val version = KsonVersion.getPublishVersion(repoDir, "x.4", isRelease = false)
        assertTrue(
            Regex("""x\.4-[0-9a-f]{8}-SNAPSHOT""").matches(version),
            "Expected SHA-qualified snapshot version with custom base, got: $version"
        )
    }

    @Test
    fun publishVersionMatchesGetVersionForReleases() {
        val repoDir = createTempGitRepo()
        assertEquals(
            KsonVersion.getVersion(isRelease = true),
            KsonVersion.getPublishVersion(repoDir, isRelease = true)
        )
    }

    @Test
    fun publishVersionMatchesGetVersionForReleasesWithCustomBase() {
        val repoDir = createTempGitRepo()
        assertEquals(
            KsonVersion.getVersion("x.4", isRelease = true),
            KsonVersion.getPublishVersion(repoDir, "x.4", isRelease = true)
        )
    }

    @Test
    fun gitShaIsEightHexCharacters() {
        val repoDir = createTempGitRepo()
        val sha = KsonVersion.getGitSha(repoDir)
        assertTrue(
            Regex("[0-9a-f]{8}").matches(sha),
            "Expected 8 hex characters, got: $sha"
        )
    }

    @Test
    fun gitShaFailsForNonGitDirectory() {
        val nonGitDir = createTempDirectory("not-a-repo").toFile()
        assertFailsWith<Exception> {
            KsonVersion.getGitSha(nonGitDir)
        }
    }
}
