import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.security.MessageDigest

/**
 * Writes a checksum manifest of [artifacts] in the format `sha256sum` reads, so that whoever
 * downloads them can run `shasum -a 256 -c SHA256SUMS` and know they got what CI built.
 *
 * Entries name the artifacts by file name only: the manifest is meant to travel beside them.
 */
abstract class Sha256SumsTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val artifacts: ConfigurableFileCollection

    @get:OutputFile
    abstract val sumsFile: RegularFileProperty

    @TaskAction
    fun writeSums() {
        val manifest = artifacts.files
            .sortedBy { it.name }
            .joinToString("") { "${it.sha256()}  ${it.name}\n" }

        sumsFile.get().asFile.writeText(manifest)
        logger.lifecycle("Wrote checksums for ${artifacts.files.size} artifact(s) to ${sumsFile.get().asFile}")
    }
}

private fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().buffered().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) {
                break
            }
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
