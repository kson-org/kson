import org.gradle.testfixtures.ProjectBuilder
import java.io.File
import java.nio.file.Files.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class Sha256SumsTaskTest {

    private val stagingDir: File = createTempDirectory("Sha256SumsTaskTest").toFile()

    private fun stage(name: String, content: String): File =
        File(stagingDir, name).apply { writeText(content) }

    private fun sumsFor(vararg artifacts: File): List<String> {
        val project = ProjectBuilder.builder().withProjectDir(stagingDir).build()
        val task = project.tasks.register("sha256Sums", Sha256SumsTask::class.java) {
            it.artifacts.from(*artifacts)
            it.sumsFile.set(File(stagingDir, "SHA256SUMS"))
        }.get()

        task.writeSums()

        return File(stagingDir, "SHA256SUMS").readLines()
    }

    @Test
    fun eachArtifactGetsItsSha256AgainstItsBareFileName() {
        val artifact = stage("kson-cli-arm64-macos.tar.gz", "hello")

        assertEquals(
            // the known SHA-256 of "hello", so a broken digest cannot agree with itself
            listOf("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824  kson-cli-arm64-macos.tar.gz"),
            sumsFor(artifact),
            "entries are `<sha256>  <name>`, the format `shasum -a 256 -c` reads"
        )
    }

    @Test
    fun entriesAreOrderedByNameRatherThanByHowTheyWereStaged() {
        val cli = stage("kson-cli-arm64-macos.tar.gz", "cli")
        val lib = stage("kson-lib-shared-arm64-macos.tar.gz", "lib")

        assertEquals(
            listOf("kson-cli-arm64-macos.tar.gz", "kson-lib-shared-arm64-macos.tar.gz"),
            sumsFor(lib, cli).map { it.substringAfter("  ") }
        )
    }

    @Test
    fun manifestEndsWithANewlineSoItConcatenatesAndDiffsCleanly() {
        val artifact = stage("kson-lib-shared-arm64-macos.tar.gz", "lib")
        sumsFor(artifact)

        assertEquals(true, File(stagingDir, "SHA256SUMS").readText().endsWith("\n"))
    }
}
