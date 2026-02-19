package org.kson

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.Status
import java.io.File
import java.nio.file.Path

class NoRepoException(msg: String) : RuntimeException(msg)
class DirtyRepoException(msg: String) : RuntimeException(msg)

/**
 * Formats a human-readable report of the dirty entries in a JGit [Status].
 *
 * @param untracked optionally provide a (possibly filtered) set of untracked file paths to include
 */
internal fun formatGitStatusReport(status: Status, untracked: Set<String> = emptySet()): String = buildString {
    if (status.added.isNotEmpty()) appendLine("  Added: ${status.added}")
    if (status.changed.isNotEmpty()) appendLine("  Modified (staged): ${status.changed}")
    if (status.removed.isNotEmpty()) appendLine("  Removed: ${status.removed}")
    if (status.modified.isNotEmpty()) appendLine("  Modified (unstaged): ${status.modified}")
    if (status.missing.isNotEmpty()) appendLine("  Missing: ${status.missing}")
    if (untracked.isNotEmpty()) appendLine("  Untracked: $untracked")
    if (status.conflicting.isNotEmpty()) appendLine("  Conflicting: ${status.conflicting}")
}

/**
 * Ensures there is a clean git checkout of [repoUri] in [cloneParentDir] at SHA [checkoutSHA]
 * Note: will clone if does not exist, will error if not clean
 *
 * @param repoUri the URI of the repo to clone.  May be any git URI that [org.eclipse.jgit.transport.URIish.URIish(java.lang.String)]
 *                 can parse, including `https://` URIs and local file paths
 * @param checkoutSHA the SHA of the desired clean checkout of the repo found at [repoUri]
 * @param cloneParentDir the directory to place our cloned [repoUri] into
 * @param cloneName the name of the directory in [cloneParentDir] to clone [repoUri] into
 * @param dirtyMessage optionally provide a short sentence explaining why this directory must be clean.  Will be added
 *                     to the [DirtyRepoException] message thrown on a dirty repo
 */
open class CleanGitCheckout(private val repoUri: String,
                            private val checkoutSHA: String,
                            private val cloneParentDir: Path,
                            cloneName: String,
                            private val dirtyMessage: String? = null
    ) {
    val checkoutDir: File = File(cloneParentDir.toFile(), cloneName)

    init {
        ensureCleanGitCheckout()
    }

    private fun ensureCleanGitCheckout() {
        if (!checkoutDir.exists()) {
            checkoutDir.mkdirs()
            cloneRepository(repoUri, checkoutDir)
        } else if (!File(checkoutDir, ".git").exists()) {
            throw NoRepoException(
                "ERROR: cannot create a ${CleanGitCheckout::class.simpleName} because `$checkoutDir` " +
                        "does not appear to be a git repo")
        }

        val git = Git.init().setDirectory(checkoutDir).call()
        val status = git.status().call()

        /**
         * We are dirty in the presence of any uncommitted changes or any untracked files other than the ones enumerated
         * in [acceptableUntrackedFiles]
         */
        val isDirty =
            status.uncommittedChanges.isNotEmpty() || status.untracked.minus(acceptableUntrackedFiles).isNotEmpty()

        if(isDirty) {
            val statusReport = formatGitStatusReport(status, status.untracked.minus(acceptableUntrackedFiles))

            val customDirtyMessage = if (dirtyMessage != null) { dirtyMessage + "\n" } else { "" }

            /**
             * Error if we're not clean other than [acceptableUntrackedFiles], since we cannot create a [CleanGitCheckout],
             * emphasis on _Clean_.  We also can't automatically blow away any changes since someone may have made
             * those changes on purpose for reasons we're not guessing, and quietly nuking those changes as a
             * side-effect of the trying to verify a clean checkout could do them a real disservice
             */
            throw DirtyRepoException(
                "ERROR: Dirty git status in `$checkoutDir`.\n$customDirtyMessage" +
                "Suggested fixes:\n" +
                        "- either clean up the git status in `$checkoutDir`\n" +
                        "- or, delete `$checkoutDir`\n" +
                        "  so it is re-cloned on the next build" +
                        "\n\n# Dirty Git Status in `$checkoutDir`:\n$statusReport")
        }

        checkoutCommit(checkoutDir, checkoutSHA)
    }

    /**
     * Clone the given [uri] into [dir]
     *
     * @param uri will be passed to [org.eclipse.jgit.api.CloneCommand.setURI] to be parsed as a [org.eclipse.jgit.transport.URIish])
     * @param dir the directory to clone the repo at [uri] into
     */
    private fun cloneRepository(uri: String, dir: File) {
        Git.cloneRepository()
            .setURI(uri)
            .setDirectory(dir)
            .call()
    }

    /**
     * Checks out the given [commit] of the repo found in [dir]
     *
     * @param dir a directory containing a git repo
     * @param commit the commit of the repo in [dir] to be checked out
     */
    private fun checkoutCommit(dir: File, commit: String) {
        val git = Git.open(dir)
        git.checkout().setName(commit).call()
    }
}

/**
 * We still consider a directory clean if it contains any of these untracked files (we do not control the
 * underlying git repos .gitignore, else we would deal with these there)
 */
private val acceptableUntrackedFiles = setOf(".DS_Store", "Thumbs.db")
