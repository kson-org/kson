import org.eclipse.jgit.api.Git
import org.gradle.testfixtures.ProjectBuilder
import org.kson.DirtyRepoException
import java.io.File
import java.nio.file.Files.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class VerifyCleanCheckoutTaskTest {

    /**
     * Create a temp git repo with an initial commit, returning the repo directory.
     *
     * Includes a `.gitignore` for directories that [ProjectBuilder] creates
     * in the project directory (`.gradle/` and `userHome/`).
     */
    private fun createTempGitRepo(): File {
        val repoDir = createTempDirectory("VerifyCleanCheckoutTest").toFile()
        Git.init().setDirectory(repoDir).call().use { git ->
            File(repoDir, ".gitignore").writeText(".gradle/\nuserHome/\n")
            File(repoDir, "initial.txt").writeText("initial content")
            git.add().addFilepattern(".").call()
            git.commit().setMessage("Initial commit").call()
        }
        return repoDir
    }

    private fun registerTask(projectDir: File): VerifyCleanCheckoutTask {
        val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
        project.tasks.register("verifyCleanCheckout", VerifyCleanCheckoutTask::class.java)
        return project.tasks.getByName("verifyCleanCheckout") as VerifyCleanCheckoutTask
    }

    @Test
    fun cleanCheckoutPasses() {
        val repoDir = createTempGitRepo()
        val task = registerTask(repoDir)

        // should not throw
        task.verify()
    }

    @Test
    fun modifiedTrackedFileIsDetected() {
        val repoDir = createTempGitRepo()
        val task = registerTask(repoDir)

        File(repoDir, "initial.txt").writeText("modified content")

        val exception = assertFailsWith<DirtyRepoException> { task.verify() }

        assertTrue(
            exception.message!!.contains("initial.txt"),
            "Exception should name the modified file. Got: ${exception.message}"
        )
    }

    @Test
    fun stagedNewFileIsDetected() {
        val repoDir = createTempGitRepo()
        val task = registerTask(repoDir)

        File(repoDir, "staged.txt").writeText("staged content")
        Git.open(repoDir).use { git -> git.add().addFilepattern("staged.txt").call() }

        val exception = assertFailsWith<DirtyRepoException> { task.verify() }

        assertTrue(
            exception.message!!.contains("staged.txt"),
            "Exception should name the staged file. Got: ${exception.message}"
        )
    }

    @Test
    fun deletedTrackedFileIsDetected() {
        val repoDir = createTempGitRepo()
        val task = registerTask(repoDir)

        File(repoDir, "initial.txt").delete()

        val exception = assertFailsWith<DirtyRepoException> { task.verify() }

        assertTrue(
            exception.message!!.contains("initial.txt"),
            "Exception should name the deleted file. Got: ${exception.message}"
        )
    }

    @Test
    fun untrackedFileDoesNotTriggerFailure() {
        val repoDir = createTempGitRepo()
        val task = registerTask(repoDir)

        // an untracked file is not an uncommitted change to a tracked file
        File(repoDir, "untracked.txt").writeText("new file")

        // should not throw
        task.verify()
    }
}
