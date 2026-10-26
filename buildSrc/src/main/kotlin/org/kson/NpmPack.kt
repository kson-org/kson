package org.kson

import PixiExecTask
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/** Packs npm packages, naming each tarball up front so its task can declare it as an output. */
object NpmPack {
    /** The name `npm pack` gives the tarball of [packageName] at [version] (see libnpmpack's `lib/index.js`). */
    fun tarballName(packageName: String, version: String): String =
        "${packageName.removePrefix("@").replaceFirst('/', '-')}-$version.tgz"

    fun tarballName(packageJson: File): String {
        val manifest = Json.parseToJsonElement(packageJson.readText()).jsonObject
        return tarballName(
            manifest.getValue("name").jsonPrimitive.content,
            manifest.getValue("version").jsonPrimitive.content
        )
    }

    fun packIntoReleaseArtifacts(
        task: PixiExecTask,
        artifactSet: ReleaseArtifactSet,
        packageDir: File,
        tarballName: String,
    ) {
        val tarball = artifactSet.stageInto(task).map { it.file(tarballName).asFile }
        task.command.set(tarball.map {
            listOf("npm", "pack", packageDir.absolutePath, "--pack-destination", it.parent)
        })
        task.outputs.file(tarball)

        // npm names the tarball itself
        task.doLast { check(tarball.get().isFile) { "npm pack did not write ${tarball.get()}" } }
    }
}
