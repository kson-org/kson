package org.kson

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider

/**
 * The release artifacts: build outputs stored as either `native` or `portable`.
 */
enum class ReleaseArtifactSet(vararg stagingTaskPaths: String) {
    /** Built by GraalVM native-image, so each platform stages its own */
    NATIVE(":kson-lib:packageReleaseArchive", ":kson-lib:packageReleaseStaticArchive", ":tooling:cli:packageReleaseArchive"),

    /** The same whichever platform stages them */
    PORTABLE(
        ":kson-lib:packageReleaseNpmTarball",
        ":lib-python:packageReleaseSdist",
        ":tooling:jetbrains:packageReleasePlugin",
        ":tooling:lsp-clients:packageReleaseVsix",
        ":tooling:lsp-clients:packageReleaseMonacoNpmTarball",
    );

    val stagingTaskPaths = stagingTaskPaths.toList()

    private val dirName = name.lowercase()
    private val taskNameInfix = dirName.replaceFirstChar { it.uppercaseChar() }

    val packageTaskName = "package${taskNameInfix}Artifacts"
    val cleanTaskName = "clean${taskNameInfix}Artifacts"
    private val cleanTaskPath = ":$cleanTaskName"

    /** Where the set stages; tasks staging into it use [stageInto] */
    fun stagingDir(project: Project): Provider<Directory> =
        project.rootProject.layout.buildDirectory.dir("release-artifacts/$dirName")

    /**
     * Makes [task] wait for the set to be emptied, and returns the directory it then stages into.
     * [task] must declare its outputs, or Gradle may delete them as stale.
     */
    fun stageInto(task: Task): Provider<Directory> {
        require(task.path in stagingTaskPaths) { "${task.path} must be listed in $name's stagingTaskPaths" }
        task.dependsOn(cleanTaskPath)
        return stagingDir(task.project)
    }
}
