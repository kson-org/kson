package org.kson

import org.eclipse.jgit.api.Git
import java.io.File

/**
 * Centralized version management for KSON projects.
 *
 * Two version strings are provided:
 * - [getVersion]: stable version for `project.version` (e.g., `0.3.0-SNAPSHOT`).
 *   This avoids build-output churn (like package-lock.json) on every commit.
 * - [getPublishVersion]: SHA-qualified version for Maven coordinates
 *   (e.g., `0.3.0-abc1234-SNAPSHOT`). This prevents stale dependency caching
 *   in shared Maven repositories.
 *
 * Release builds produce the same version from both methods (e.g., `0.3.0`).
 */
object KsonVersion {
    /**
     * Base version number without snapshot suffix.
     * Update this when preparing a new release.
     */
    const val BASE_VERSION = "0.3.0"

    /**
     * Returns the short git commit SHA (8 characters) using JGit.
     */
    internal fun getGitSha(projectDir: File): String {
        Git.open(projectDir).use { git ->
            val head = git.repository.resolve("HEAD")
                ?: throw IllegalStateException("Cannot resolve HEAD in git repository at $projectDir")
            return head.name.substring(0, 8)
        }
    }

    /**
     * Returns a stable version string for use as `project.version`.
     *
     * Snapshot versions use a fixed suffix (e.g., `0.3.0-SNAPSHOT`) so that
     * build outputs like package-lock.json don't churn on every commit.
     */
    fun getVersion(baseVersion: String = BASE_VERSION, isRelease: Boolean): String {
        return if (isRelease) baseVersion else "$baseVersion-SNAPSHOT"
    }

    /**
     * Returns a SHA-qualified version string for Maven publishing coordinates.
     *
     * The SHA ensures each commit produces a unique artifact version, preventing
     * stale dependency caching in shared Maven repositories (including mavenLocal).
     * For releases, delegates to [getVersion] to guarantee identical versions.
     */
    fun getPublishVersion(projectDir: File, baseVersion: String = BASE_VERSION, isRelease: Boolean): String {
        if (isRelease) return getVersion(baseVersion, isRelease)
        return "$baseVersion-${getGitSha(projectDir)}-SNAPSHOT"
    }
}
