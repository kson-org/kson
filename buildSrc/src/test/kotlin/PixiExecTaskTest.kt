import org.gradle.api.Project
import org.gradle.internal.os.OperatingSystem
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assume.assumeFalse
import java.io.File
import java.nio.file.Files.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/** the local Pixi install directory the generated wrappers manage, relative to the project directory */
private const val PIXI_INSTALL_DIR = ".pixi"

/** where our fake Pixi logs the arguments it was run with, one line per run */
private const val PIXI_RUNS_FILE = "pixi-runs"

/** what the stale wrapper we plant leaves behind, so we can tell it apart from a regenerated one */
private const val STALE_WRAPPER_MARKER = "stale-wrapper-ran"

/**
 * Exercises [PixiExecTask.exec] against a fake Pixi already installed at the pinned version, so these tests
 * neither hit the network nor install a real Pixi.
 *
 * The generated `pixiw.bat` implements the same logic but needs `cmd.exe` to run, so it is not covered here.
 */
class PixiExecTaskTest {
    @Test
    fun staleWrapperIsRegeneratedBeforeRunningIt() {
        assumePosixShell()
        val project = pixiProject()
        writeStaleWrapper(project)

        execTask(project, "npmInstall", "npm", "install").exec()

        assertNoStaleWrapperRan(project)
        assertEquals(listOf("run npm install"), pixiRuns(project), "Should run the command through the regenerated wrapper")
    }

    @Test
    fun everyPixiTaskInAProjectRegeneratesTheWrapper() {
        assumePosixShell()
        val project = pixiProject()

        execTask(project, "npmInstall", "npm", "install").exec()
        writeStaleWrapper(project)
        execTask(project, "npmTest", "npm", "test").exec()

        assertNoStaleWrapperRan(project)
        assertEquals(listOf("run npm install", "run npm test"), pixiRuns(project), "Both tasks should run their command")
    }

    @Test
    fun missingWrapperIsAnErrorWhenGenerationIsDisabled() {
        val task = execTask(emptyProject(), "npmInstall", "npm", "install") {
            autoGenerateWrapper.set(false)
        }

        val failure = assertFailsWith<IllegalStateException> { task.exec() }

        // the wrapper PixiExecTask looks for is named per platform
        val wrapperPath = if (OperatingSystem.current().isWindows) "pixiw.bat" else "./pixiw"
        assertEquals(
            "Pixi wrapper not found at $wrapperPath. Either create it manually or enable autoGenerateWrapper.",
            failure.message
        )
    }

    /** Skip the calling test where the generated `pixiw` cannot run */
    private fun assumePosixShell() =
        assumeFalse("the generated `pixiw` needs a POSIX shell", OperatingSystem.current().isWindows)

    /** Create a project holding neither a Pixi install nor wrapper scripts */
    private fun emptyProject(): Project = ProjectBuilder.builder()
        .withProjectDir(createTempDirectory("PixiExecTaskTest").toFile())
        .build()

    /** Create a project holding a fake Pixi installed at the pinned version, but no wrapper scripts */
    private fun pixiProject(): Project = emptyProject().also { installFakePixi(it) }

    /**
     * Install a `pixi` reporting the pinned version, so the generated wrapper runs it rather than installing one,
     * and logs every other invocation to [PIXI_RUNS_FILE]
     */
    private fun installFakePixi(project: Project) {
        val pixi = File(project.projectDir, "$PIXI_INSTALL_DIR/bin/pixi")
        pixi.parentFile.mkdirs()
        pixi.writeText("""
            #!/bin/sh
            if [ "${'$'}1" = "--version" ]; then
                echo "pixi ${PixiWrapperTask.PINNED_PIXI_VERSION}"
                exit 0
            fi
            echo "${'$'}@" >> "${pixiRunsFile(project)}"
        """.trimIndent())
        pixi.setExecutable(true)
    }

    /** Plant a `pixiw` which only records that it ran, standing in for one left over from an earlier build */
    private fun writeStaleWrapper(project: Project) {
        val pixiw = File(project.projectDir, "pixiw")
        pixiw.writeText("#!/bin/sh\ntouch \"${File(project.projectDir, STALE_WRAPPER_MARKER)}\"\n")
        pixiw.setExecutable(true)
    }

    /** Register a [PixiExecTask] which runs [command] through the wrapper in [project]'s directory */
    private fun execTask(
        project: Project,
        name: String,
        vararg command: String,
        configure: PixiExecTask.() -> Unit = {}
    ): PixiExecTask =
        project.tasks.pixiExec(name, *command) {
            pixiInstallDir.set(PIXI_INSTALL_DIR)
            workingDirectory.set(project.layout.projectDirectory)
            configure()
        }.get()

    private fun assertNoStaleWrapperRan(project: Project) =
        assertFalse(
            File(project.projectDir, STALE_WRAPPER_MARKER).exists(),
            "The stale `pixiw` should have been overwritten before being run"
        )

    /** The arguments our fake Pixi was run with, one entry per run */
    private fun pixiRuns(project: Project): List<String> {
        val runs = pixiRunsFile(project)
        return if (runs.exists()) runs.readLines() else emptyList()
    }

    private fun pixiRunsFile(project: Project) = File(project.projectDir, PIXI_RUNS_FILE)
}
