import org.gradle.internal.os.OperatingSystem
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assume.assumeFalse
import java.io.File
import java.nio.file.Files.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

/** the local Pixi install directory the generated wrappers manage, relative to the project directory */
private const val PIXI_INSTALL_DIR = ".pixi"

/** where our stub installer records the `PIXI_VERSION` it was asked to install, one line per install */
private const val INSTALL_REQUESTS_FILE = "install-requests"

/** directory we prepend to `PATH` so the wrapper picks up our stub `curl` */
private const val STUB_BIN_DIR = "stub-bin"

/**
 * Exercises the generated `pixiw` by running it with a stubbed Pixi installer on its `PATH`, so these
 * tests neither hit the network nor install a real Pixi.
 *
 * The generated `pixiw.bat` implements the same logic but needs `cmd.exe` to run, so it is not covered here.
 */
class PixiWrapperTaskTest {
    private val pinnedVersionReport = "pixi ${PixiWrapperTask.PINNED_PIXI_VERSION}"
    private val pinnedVersionRequest = "v${PixiWrapperTask.PINNED_PIXI_VERSION}"

    @Test
    fun missingPixiIsInstalledAtThePinnedVersion() {
        assumePosixShell()
        val projectDir = generateWrapperProject()

        val output = runPixiw(projectDir, "--version")

        assertEquals(pinnedVersionReport, output.lines().last(), "Should run the freshly installed Pixi")
        assertEquals(listOf(pinnedVersionRequest), installRequests(projectDir))
    }

    @Test
    fun pixiOffThePinIsReinstalled() {
        assumePosixShell()
        val projectDir = generateWrapperProject()
        installFakePixi(projectDir, "0.1.0")

        val output = runPixiw(projectDir, "--version")

        assertEquals(pinnedVersionReport, output.lines().last(), "Should converge on the pinned version")
        assertEquals(listOf(pinnedVersionRequest), installRequests(projectDir))
    }

    @Test
    fun pinnedPixiIsRunWithoutReinstalling() {
        assumePosixShell()
        val projectDir = generateWrapperProject()
        installFakePixi(projectDir, PixiWrapperTask.PINNED_PIXI_VERSION)

        val output = runPixiw(projectDir, "--version")

        assertEquals(pinnedVersionReport, output, "Should run the installed Pixi without installing anything")
        assertEquals(emptyList(), installRequests(projectDir))
    }

    /** Skip the calling test where the generated `pixiw` cannot run */
    private fun assumePosixShell() =
        assumeFalse("the generated `pixiw` needs a POSIX shell", OperatingSystem.current().isWindows)

    /**
     * Create a project directory holding freshly generated wrappers and the [stub installer][writeStubInstaller],
     * but no installed Pixi
     */
    private fun generateWrapperProject(): File {
        val projectDir = createTempDirectory("PixiWrapperTaskTest").toFile()
        val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
        project.tasks.register("pixiWrapper", PixiWrapperTask::class.java) { task ->
            task.outputDir.set(projectDir)
            task.pixiInstallDir.set(PIXI_INSTALL_DIR)
        }
        val wrapperTask = project.tasks.getByName("pixiWrapper") as PixiWrapperTask
        wrapperTask.generateWrappers()

        writeStubInstaller(projectDir)
        return projectDir
    }

    /**
     * Write a `curl` stub which serves our own installer instead of `https://pixi.sh/install.sh`: it logs the
     * `PIXI_VERSION` it was asked for to [INSTALL_REQUESTS_FILE] and installs a `pixi` reporting that version
     */
    private fun writeStubInstaller(projectDir: File) {
        val installer = File(projectDir, "stub-install.sh")
        installer.writeText("""
            mkdir -p "${'$'}PIXI_HOME/bin"
            echo "${'$'}PIXI_VERSION" >> "${'$'}PIXI_HOME/$INSTALL_REQUESTS_FILE"
            printf '#!/bin/sh\necho "pixi %s"\n' "${'$'}{PIXI_VERSION#v}" > "${'$'}PIXI_HOME/bin/pixi"
            chmod +x "${'$'}PIXI_HOME/bin/pixi"
        """.trimIndent())

        val curl = File(projectDir, "$STUB_BIN_DIR/curl")
        curl.parentFile.mkdirs()
        curl.writeText("#!/bin/sh\ncat '${installer.absolutePath}'\n")
        curl.setExecutable(true)
    }

    /** Install a `pixi` which reports [version], standing in for a previously installed Pixi */
    private fun installFakePixi(projectDir: File, version: String) {
        val pixi = File(projectDir, "$PIXI_INSTALL_DIR/bin/pixi")
        pixi.parentFile.mkdirs()
        pixi.writeText("#!/bin/sh\necho \"pixi $version\"\n")
        pixi.setExecutable(true)
    }

    /** Run the generated `pixiw` from [projectDir], returning its trimmed output (stderr included) */
    private fun runPixiw(projectDir: File, vararg args: String): String {
        val pixiw = File(projectDir, "pixiw")
        val processBuilder = ProcessBuilder(listOf(pixiw.absolutePath) + args)
            .directory(projectDir)
            .redirectErrorStream(true)
        val stubBin = File(projectDir, STUB_BIN_DIR).absolutePath
        processBuilder.environment()["PATH"] = stubBin + File.pathSeparator + System.getenv("PATH")

        val process = processBuilder.start()
        val output = process.inputStream.bufferedReader().readText().trim()
        assertEquals(0, process.waitFor(), "`pixiw` should exit cleanly, but printed:\n$output")
        return output
    }

    /** The `PIXI_VERSION`s the wrapper asked our stub installer for, one per install it ran */
    private fun installRequests(projectDir: File): List<String> {
        val requests = File(projectDir, "$PIXI_INSTALL_DIR/$INSTALL_REQUESTS_FILE")
        return if (requests.exists()) requests.readLines() else emptyList()
    }
}
