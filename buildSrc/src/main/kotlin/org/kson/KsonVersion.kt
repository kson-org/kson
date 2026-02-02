package org.kson

import java.io.File

/**
 * Centralized version management for KSON projects.
 * Computes version strings that include git commit SHA for snapshot builds.
 *
 * Usage in build.gradle.kts:
 *   val isRelease = project.findProperty("release") == "true"
 *   version = org.kson.KsonVersion.getVersion(rootProject.projectDir, isRelease)
 *
 * Build commands:
 *   Snapshot: ./gradlew build              -> 0.3.0-abc1234-SNAPSHOT
 *   Release:  ./gradlew build -Prelease=true -> 0.3.0
 */
object KsonVersion {
    /**
     * Base version number without snapshot suffix.
     * Update this when preparing a new release.
     */
    const val BASE_VERSION = "0.3.0"

    /**
     * Returns the short git commit SHA (8 characters).
     * Falls back to "unknown" if git is not available.
     */
    private fun getGitSha(projectDir: File): String {
        return try {
            val process = ProcessBuilder("git", "rev-parse", "--short=8", "HEAD")
                .directory(projectDir)
                .redirectErrorStream(true)
                .start()
            process.inputStream.bufferedReader().readText().trim()
        } catch (e: Exception) {
            "unknown"
        }
    }

    /**
     * Returns the version string based on release mode.
     *
     * @param projectDir The project directory for git SHA lookup
     * @param isRelease If true, returns release version (e.g., "0.3.0").
     *                  If false, returns snapshot version (e.g., "0.3.0-abc1234-SNAPSHOT").
     */
    fun getVersion(projectDir: File, baseVersion: String = BASE_VERSION, isRelease: Boolean): String {
        return if (isRelease) {
            baseVersion
        } else {
            "$baseVersion-${getGitSha(projectDir)}-SNAPSHOT"
        }
    }
}