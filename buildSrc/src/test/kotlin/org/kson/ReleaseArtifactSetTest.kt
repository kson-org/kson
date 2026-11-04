package org.kson

import org.gradle.testfixtures.ProjectBuilder
import java.nio.file.Files.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * `.circleci/config.kson` runs these tasks and stores these directories by name.
 */
class ReleaseArtifactSetTest {
    private val rootProject = ProjectBuilder.builder()
        .withProjectDir(createTempDirectory("ReleaseArtifactSetTest").toFile())
        .build()
    private val subproject = ProjectBuilder.builder().withParent(rootProject).withName("kson-lib").build()

    @Test
    fun eachSetIsPackagedByTheTaskCiRunsIntoTheDirectoryCiStores() {
        assertEquals(
            mapOf(
                "packageNativeArtifacts" to rootProject.file("build/release-artifacts/native"),
                "packagePortableArtifacts" to rootProject.file("build/release-artifacts/portable"),
            ),
            ReleaseArtifactSet.entries.associate { it.packageTaskName to it.stagingDir(subproject).get().asFile }
        )
    }

    @Test
    fun aTaskStagingIntoASetWaitsForTheRootTaskThatEmptiesIt() {
        val stagingTaskNames = mapOf(
            ReleaseArtifactSet.NATIVE to "packageReleaseArchive",
            ReleaseArtifactSet.PORTABLE to "packageReleaseNpmTarball",
        )

        val cleanTasksByStagingDir = stagingTaskNames.entries.associate { (artifactSet, taskName) ->
            val task = subproject.tasks.register(taskName).get()
            artifactSet.stageInto(task).get().asFile to task.dependsOn
        }

        assertEquals(
            mapOf(
                rootProject.file("build/release-artifacts/native") to setOf<Any>(":cleanNativeArtifacts"),
                rootProject.file("build/release-artifacts/portable") to setOf<Any>(":cleanPortableArtifacts"),
            ),
            cleanTasksByStagingDir
        )
    }

    @Test
    fun aTaskMissingFromTheSetsStagingTaskPathsCannotStageIntoIt() {
        val unlistedTask = subproject.tasks.register("packageReleaseUnlisted").get()

        val failure = assertFailsWith<IllegalArgumentException> { ReleaseArtifactSet.PORTABLE.stageInto(unlistedTask) }

        assertEquals(":kson-lib:packageReleaseUnlisted must be listed in PORTABLE's stagingTaskPaths", failure.message)
    }
}
