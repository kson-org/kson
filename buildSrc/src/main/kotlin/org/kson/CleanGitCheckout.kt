package org.kson

import org.eclipse.jgit.api.Git
import java.io.File
import java.nio.file.Path

class NoRepoException(msg: String) : RuntimeException(msg)
class DirtyRepoException(msg: String) : RuntimeException(msg)

/**
 * Ensures there is a clean git checkout of [repoUri] in [cloneParentDir] at SHA [checkoutSHA]
 * Note: will clone if does not exist, will error if not clean
 *
 * @param repoUri the URI of the repo to clone.  May be any git URI that [org.eclipse.jgit.transport.URIish.URIish(java.lang.String)]
 *                 can parse, including `https://` URIs and local file paths
 * @param checkoutSHA the SHA of the desired clean checkout of the repo found at [repoUri]
 * @param cloneParentDir the directory to place our cloned [repoUri] into
 * @param cloneName the name of the directory in [cloneParentDir] to clone [repoUri] into
 */
open class CleanGitCheckout(private val repoUri: String,
                            private val checkoutSHA: String,
                            private val cloneParentDir: Path,
                            cloneName: String) {
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
                "ERROR: $checkoutDir should contain a checkout of https://github.com/nst/JSONTestSuite," +
                        "but it does not appear to be a git repo")
        }

        val git = Git.init().setDirectory(checkoutDir).call()
        if(!git.status().call().isClean) {
            // throw if we're not clean... don't want to build because the source files might be incorrect,
            // but also don't want to immediately blow it away since someone may have made changes on purpose
            // for reasons we're not guessing, and quietly nuking those changes as a side-effect of the build
            // could do them a real disservice
            throw DirtyRepoException(
                "ERROR: Dirty git status in $cloneParentDir.  Please ensure the git status is clean " +
                        "or delete the directory and re-run this script")
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