package org.kson

import org.gradle.testfixtures.ProjectBuilder
import java.nio.file.Files.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

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
        val cleanTasksByStagingDir = ReleaseArtifactSet.entries.associate { artifactSet ->
            val task = subproject.tasks.register("stageInto${artifactSet.name}").get()
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
}
