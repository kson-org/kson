package org.kson

import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider

/**
 * The one directory every release artifact is staged into
 */
val Project.releaseArtifactsDir: Provider<Directory>
    get() = rootProject.layout.buildDirectory.dir("release-artifacts")
