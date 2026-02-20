package org.kson

import org.eclipse.jgit.api.Git
import java.io.File
import java.nio.file.Files.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class FormatGitStatusReportTest {

    private fun createTempGitRepo(): Pair<File, Git> {
        val repoDir = createTempDirectory("FormatGitStatusReportTest").toFile()
        val git = Git.init().setDirectory(repoDir).call()

        File(repoDir, "tracked.txt").writeText("original")
        git.add().addFilepattern("tracked.txt").call()
        git.commit().setMessage("Initial commit").call()

        return repoDir to git
    }

    @Test
    fun cleanStatusProducesEmptyReport() {
        val (_, git) = createTempGitRepo()
        git.use {
            val report = formatGitStatusReport(it.status().call())
            assertEquals("", report)
        }
    }

    @Test
    fun modifiedUnstagedFileUsesDistinctLabel() {
        val (repoDir, git) = createTempGitRepo()
        git.use {
            File(repoDir, "tracked.txt").writeText("modified")
            val report = formatGitStatusReport(it.status().call())
            assertContains(report, "Modified (unstaged)")
            assertFalse(report.contains("Modified (staged)"), "Should not contain staged label")
        }
    }

    @Test
    fun stagedModificationUsesDistinctLabel() {
        val (repoDir, git) = createTempGitRepo()
        git.use {
            File(repoDir, "tracked.txt").writeText("modified")
            it.add().addFilepattern("tracked.txt").call()
            val report = formatGitStatusReport(it.status().call())
            assertContains(report, "Modified (staged)")
            assertFalse(report.contains("Modified (unstaged)"), "Should not contain unstaged label")
        }
    }

    @Test
    fun untrackedSetIsIncludedWhenProvided() {
        val (_, git) = createTempGitRepo()
        git.use {
            val report = formatGitStatusReport(it.status().call(), setOf("new-file.txt"))
            assertContains(report, "Untracked")
            assertContains(report, "new-file.txt")
        }
    }

    @Test
    fun untrackedLineOmittedWhenSetIsEmpty() {
        val (repoDir, git) = createTempGitRepo()
        git.use {
            File(repoDir, "tracked.txt").writeText("modified")
            val report = formatGitStatusReport(it.status().call())
            assertFalse(report.contains("Untracked"), "Should not contain Untracked when not provided")
        }
    }
}
