package org.kson

import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider

/**
 * The single directory every release artifact built on this platform is staged into, so that CI has
 * one path to collect and a release manager has one place to look (see `docs/release_process.md`).
 *
 * It lives under the root project's build directory no matter which project stages into it: a
 * release is cut from the repository as a whole, not from one subproject at a time.
 */
val Project.releaseArtifactsDir: Provider<Directory>
    get() = rootProject.layout.buildDirectory.dir("release-artifacts")
