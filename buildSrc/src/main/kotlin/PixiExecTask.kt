import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.os.OperatingSystem
import org.gradle.process.ExecResult
import org.gradle.process.ExecSpec
import java.io.ByteArrayOutputStream

abstract class PixiExecTask : DefaultTask() {
    @get:Input
    abstract val command: ListProperty<String>

    @get:Input
    @get:Optional
    abstract val envVars: MapProperty<String, String>

    @get:InputDirectory
    @get:Optional
    abstract val workingDirectory: DirectoryProperty

    @get:Input
    @get:Optional
    abstract val pixiInstallDir: Property<String>

    @get:Input
    @get:Optional
    abstract val autoGenerateWrapper: Property<Boolean>

    @get:Input
    @get:Optional
    abstract val captureOutput: Property<Boolean>

    init {
        group = "pixi"
        autoGenerateWrapper.convention(true)
        pixiInstallDir.convention("../.pixi")
        captureOutput.convention(false)
    }

    @TaskAction
    fun exec(): ExecResult {
        val pixiwPath = getPixiWrapperPath()
        val pixiwFile = project.file(pixiwPath)

        if (!pixiwFile.exists() && autoGenerateWrapper.get()) {
            logger.lifecycle("Pixi wrapper not found at $pixiwPath, generating...")
            generateWrapper()
        }

        if (!pixiwFile.exists()) {
            throw IllegalStateException("Pixi wrapper not found at $pixiwPath. Either create it manually or enable autoGenerateWrapper.")
        }

        // Always use absolute path to ensure the wrapper is found regardless of working directory
        val fullCommand = mutableListOf(pixiwFile.absolutePath, "run")
        fullCommand.addAll(command.get())

        logger.info("Executing: ${fullCommand.joinToString(" ")}")

        return project.exec { spec: ExecSpec ->
            if (workingDirectory.isPresent) {
                spec.workingDir = workingDirectory.get().asFile
            }
            
            spec.commandLine = fullCommand
            spec.environment(envVars.getOrElse(mapOf()))

            if (captureOutput.get()) {
                spec.standardOutput = ByteArrayOutputStream()
                spec.errorOutput = ByteArrayOutputStream()
            } else {
                spec.standardOutput = System.out
                spec.errorOutput = System.err
            }
        }
    }

    private fun getPixiWrapperPath(): String {
        return if (OperatingSystem.current().isWindows) {
            "pixiw.bat"
        } else {
            "./pixiw"
        }
    }

    private fun generateWrapper() {
        val wrapperTask = project.tasks.pixiWrapper("_tempPixiWrapper") {
            outputDir.set(project.layout.projectDirectory)
            pixiInstallDir.set(this@PixiExecTask.pixiInstallDir)
        }.get()
        wrapperTask.generateWrappers()
    }
}