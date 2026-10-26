package org.kson

import PixiExecTask
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import java.io.File
import java.nio.file.Files.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class NpmPackTest {

    @Test
    fun scopedPackagesJoinScopeAndNameWithADash() {
        assertEquals(
            "kson_org-monaco-editor-0.4.0-dev.0.tgz",
            NpmPack.tarballName("@kson_org/monaco-editor", "0.4.0-dev.0")
        )
    }

    @Test
    fun unscopedPackagesKeepTheirName() {
        assertEquals("kson-1.2.3.tgz", NpmPack.tarballName("kson", "1.2.3"))
    }

    @Test
    fun universalJsPackagePacksAsKsonOrgKson() {
        assertEquals("kson_org-kson-1.2.3.tgz", NpmPack.tarballName(UniversalJsPackage.NAME, "1.2.3"))
    }

    @Test
    fun readsNameAndVersionFromThePackageJson() {
        val packageJson = File(createTempDirectory("NpmPackTest").toFile(), "package.json")
        packageJson.writeText(
            """
            {
              "name": "@kson_org/monaco-editor",
              "version": "0.4.0-dev.0",
              "files": ["dist"]
            }
            """.trimIndent()
        )

        assertEquals("kson_org-monaco-editor-0.4.0-dev.0.tgz", NpmPack.tarballName(packageJson))
    }

    @Test
    fun packsIntoTheDirectoryOfTheDeclaredTarballOnceItIsEmptied() {
        val project = emptyProject()
        val packageDir = File(project.projectDir, "js-package")
        val task = packTask(project, packageDir, "kson_org-kson-1.2.3.tgz")

        val tarball = ReleaseArtifactSet.PORTABLE.stagingDir(project).get().file("kson_org-kson-1.2.3.tgz").asFile
        assertEquals(setOf(tarball), task.outputs.files.files)
        assertEquals(
            listOf("npm", "pack", packageDir.absolutePath, "--pack-destination", tarball.parent),
            task.command.get()
        )
        assertEquals(setOf<Any>(":cleanPortableArtifacts"), task.dependsOn)
    }

    @Test
    fun failsWhenNpmWritesAnotherTarball() {
        val project = emptyProject()
        val releaseArtifacts = ReleaseArtifactSet.PORTABLE.stagingDir(project).get().asFile
        releaseArtifacts.mkdirs()
        File(releaseArtifacts, "kson-1.2.3.tgz").createNewFile()

        val failure = assertFailsWith<IllegalStateException> { checkWhatNpmWrote(project, "kson_org-kson-1.2.3.tgz") }
        assertEquals("npm pack did not write ${File(releaseArtifacts, "kson_org-kson-1.2.3.tgz")}", failure.message)
    }

    @Test
    fun passesWhenNpmWritesTheDeclaredTarball() {
        val project = emptyProject()
        val releaseArtifacts = ReleaseArtifactSet.PORTABLE.stagingDir(project).get().asFile
        releaseArtifacts.mkdirs()
        File(releaseArtifacts, "kson_org-kson-1.2.3.tgz").createNewFile()

        checkWhatNpmWrote(project, "kson_org-kson-1.2.3.tgz")
    }

    private fun emptyProject(): Project = ProjectBuilder.builder()
        .withProjectDir(createTempDirectory("NpmPackTest").toFile())
        .build()

    private fun packTask(project: Project, packageDir: File, tarballName: String): PixiExecTask =
        project.tasks.register("packageReleaseNpmTarball", PixiExecTask::class.java) {
            NpmPack.packIntoReleaseArtifacts(it, ReleaseArtifactSet.PORTABLE, packageDir, tarballName)
        }.get()

    /** Runs a task packing [tarballName], leaving out its own action, which runs npm */
    private fun checkWhatNpmWrote(project: Project, tarballName: String) {
        val task = project.tasks.register("packageReleaseNpmTarball", PixiExecTask::class.java).get()
        val runsNpm = task.actions.single()
        NpmPack.packIntoReleaseArtifacts(
            task, ReleaseArtifactSet.PORTABLE, File(project.projectDir, "js-package"), tarballName
        )
        task.actions.filter { it !== runsNpm }.forEach { it.execute(task) }
    }
}
