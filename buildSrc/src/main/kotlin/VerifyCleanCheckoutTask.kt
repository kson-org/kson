import org.eclipse.jgit.api.Git
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.kson.DirtyRepoException
import org.kson.formatGitStatusReport

/**
 * Verifies that the project's git checkout has no uncommitted changes to tracked files.
 *
 * Intended as a CI verification step: run after the build to catch cases where the build
 * produces file changes that should have been committed (e.g. regenerated lockfiles,
 * transpiled config files).
 *
 * Uses JGit for cross-platform compatibility (no dependency on a system `git` install).
 * Checks [org.eclipse.jgit.api.Status.getUncommittedChanges] which covers all tracked-file
 * changes (staged adds, modifications, deletions, and conflicts).  We intentionally do not
 * check [org.eclipse.jgit.api.Status.getUntracked] because JGit has known gaps with nested
 * `.gitignore` files (see [Eclipse Bug 558861](https://bugs.eclipse.org/bugs/show_bug.cgi?id=558861)),
 * and build-produced changes are modifications to existing tracked files, not new untracked files.
 */
abstract class VerifyCleanCheckoutTask : DefaultTask() {

    init {
        group = "verification"
        description = "Verify that the git checkout has no uncommitted changes"

        // always run when invoked -- there's no meaningful "up-to-date" for this check
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun verify() {
        Git.open(project.rootDir).use { git ->
            val status = git.status().call()

            if (status.uncommittedChanges.isNotEmpty()) {
                throw DirtyRepoException(
                    "Build produced uncommitted changes. " +
                        "If these changes are expected, commit them and re-run.\n\n${formatGitStatusReport(status)}"
                )
            }

            logger.lifecycle("Checkout is clean -- no uncommitted changes detected.")
        }
    }
}
