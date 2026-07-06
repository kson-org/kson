package org.kson.jsonsuite

import org.kson.CleanGitCheckout
import org.kson.DirtyRepoException
import java.io.File
import java.nio.file.Files.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CleanGitCheckoutSparseTest {

    @Test
    fun testSparseFilteringAndCheckout() {
        val source = createSourceRepo()
        val destParent = createTempDirectory("CleanGitCheckoutSparseDest")

        val checkout = CleanGitCheckout(source.path, source.firstSha, destParent, "dest", sparsePaths = listOf("wanted"))

        val entries = checkout.checkoutDir.list()!!.toSet()
        assertEquals(setOf("wanted", ".git"), entries, "should only check out the sparse path plus .git")
        assertTrue(File(checkout.checkoutDir, "wanted").isDirectory, "wanted dir should be present")
        assertFalse(File(checkout.checkoutDir, "unwanted").exists(), "unwanted dir should be filtered out")
        assertFalse(File(checkout.checkoutDir, "root.txt").exists(), "root file should be filtered out")
        assertFalse(File(checkout.checkoutDir, "nested").exists(), "anchored sparse path must not also pull a nested dir of the same name")
        assertEquals(source.firstSha, headSha(checkout.checkoutDir), "HEAD should be the requested SHA")
    }

    @Test
    fun testIdempotentWhenAlreadyAtSha() {
        val source = createSourceRepo()
        val destParent = createTempDirectory("CleanGitCheckoutSparseDest")

        val first = CleanGitCheckout(source.path, source.firstSha, destParent, "dest", sparsePaths = listOf("wanted"))
        // probe under .git is ignored by `git status`, so the tree stays clean and the probe survives iff we reuse
        val probe = File(first.checkoutDir, ".git/.idempotency-probe")
        probe.createNewFile()

        val second = CleanGitCheckout(source.path, source.firstSha, destParent, "dest", sparsePaths = listOf("wanted"))

        assertTrue(probe.exists(), "should not delete+refetch when already at the requested SHA")
        assertEquals(source.firstSha, headSha(second.checkoutDir), "HEAD should still be the requested SHA")
    }

    @Test
    fun testRefetchesOnWrongSha() {
        val source = createSourceRepo()
        val secondSha = source.addSecondCommit()
        val destParent = createTempDirectory("CleanGitCheckoutSparseDest")

        val first = CleanGitCheckout(source.path, source.firstSha, destParent, "dest", sparsePaths = listOf("wanted"))
        // probe under .git survives a reuse but is destroyed by delete+refetch
        val probe = File(first.checkoutDir, ".git/.idempotency-probe")
        probe.createNewFile()

        val second = CleanGitCheckout(source.path, secondSha, destParent, "dest", sparsePaths = listOf("wanted"))

        assertFalse(probe.exists(), "should delete+refetch when checked out at the wrong SHA")
        assertEquals(secondSha, headSha(second.checkoutDir), "HEAD should be the new SHA after refetch")
    }

    @Test
    fun testThrowsOnDirtyWorkingTree() {
        val source = createSourceRepo()
        val destParent = createTempDirectory("CleanGitCheckoutSparseDest")

        val first = CleanGitCheckout(source.path, source.firstSha, destParent, "dest", sparsePaths = listOf("wanted"))
        val tracked = File(first.checkoutDir, "wanted/file.txt")
        tracked.writeText("dirtied\n")

        assertFailsWith<DirtyRepoException>("should error on a dirty working tree") {
            CleanGitCheckout(source.path, source.firstSha, destParent, "dest", sparsePaths = listOf("wanted"))
        }

        assertEquals("dirtied\n", tracked.readText(), "should not silently refetch and lose the dirty change")
    }

    @Test
    fun testReusesWithAcceptableUntrackedFile() {
        val source = createSourceRepo()
        val destParent = createTempDirectory("CleanGitCheckoutSparseDest")

        val first = CleanGitCheckout(source.path, source.firstSha, destParent, "dest", sparsePaths = listOf("wanted"))
        File(first.checkoutDir, ".DS_Store").createNewFile()
        val probe = File(first.checkoutDir, ".git/.idempotency-probe")
        probe.createNewFile()

        CleanGitCheckout(source.path, source.firstSha, destParent, "dest", sparsePaths = listOf("wanted"))

        assertTrue(probe.exists(), "an acceptable untracked file must not count as dirty nor trigger a refetch")
    }

    @Test
    fun testThrowsWithGitGuidanceOnFailedFetch() {
        val destParent = createTempDirectory("CleanGitCheckoutSparseDest")
        val bogusRepo = createTempDirectory("CleanGitCheckoutSparseBogus").resolve("not-a-repo").toString()

        val exception = assertFailsWith<RuntimeException> {
            CleanGitCheckout(bogusRepo, "0".repeat(40), destParent, "dest", sparsePaths = listOf("wanted"))
        }

        assertTrue(
            exception.message!!.contains("ensure `git` is installed and on your PATH"),
            "error should include the git-installation guidance"
        )
    }

    private fun headSha(dir: File): String = runGit(dir, "rev-parse", "HEAD").trim()

    /** Creates a local source repo with `wanted/`, `unwanted/`, `nested/wanted/` and `root.txt`, returning a handle to it. */
    private fun createSourceRepo(): SourceRepo {
        val dir = createTempDirectory("CleanGitCheckoutSparseSource").toFile()
        runGit(dir, "init", "-q")
        runGit(dir, "config", "user.email", "test@example.com")
        runGit(dir, "config", "user.name", "Test")
        runGit(dir, "config", "commit.gpgsign", "false")

        File(dir, "wanted").mkdirs()
        File(dir, "wanted/file.txt").writeText("wanted\n")
        File(dir, "unwanted").mkdirs()
        File(dir, "unwanted/file.txt").writeText("unwanted\n")
        File(dir, "root.txt").writeText("root\n")
        File(dir, "nested/wanted").mkdirs()
        File(dir, "nested/wanted/nested.txt").writeText("nested\n")

        runGit(dir, "add", "-A")
        runGit(dir, "commit", "-q", "-m", "initial")
        val firstSha = runGit(dir, "rev-parse", "HEAD").trim()
        return SourceRepo(dir, firstSha)
    }

    private inner class SourceRepo(private val dir: File, val firstSha: String) {
        val path: String get() = dir.absolutePath

        fun addSecondCommit(): String {
            File(dir, "wanted/second.txt").writeText("second\n")
            runGit(dir, "add", "-A")
            runGit(dir, "commit", "-q", "-m", "second")
            return runGit(dir, "rev-parse", "HEAD").trim()
        }
    }

    private fun runGit(dir: File, vararg args: String): String {
        val process = ProcessBuilder(listOf("git", *args))
            .directory(dir)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText()
        check(process.waitFor() == 0) { "git ${args.joinToString(" ")} failed:\n$output" }
        return output
    }
}
