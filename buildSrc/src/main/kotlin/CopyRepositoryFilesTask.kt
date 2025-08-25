import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import kotlin.io.path.copyTo
import java.io.File
import java.nio.file.StandardCopyOption.COPY_ATTRIBUTES
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

open class CopyRepositoryFilesTask : DefaultTask() {
    @Input
    var excludedPath: String = ""

    @OutputDirectory
    val outputDir: File = project.projectDir.resolve("kotlin")

    @TaskAction
    fun run() {
        val repositoryRoot = project.projectDir.parentFile
        val libRustDir = project.projectDir

        // Make sure the output directory is empty
        outputDir.mkdirs()

        // Copy all files tracked by git, except `excludedPath`
        val gitLsFilesProcess = ProcessBuilder("git", "ls-files")
            .directory(repositoryRoot)
            .start()

        val gitTrackedFiles = gitLsFilesProcess.inputStream.bufferedReader().readLines()
        gitLsFilesProcess.waitFor()

        if (gitLsFilesProcess.exitValue() != 0) {
            throw RuntimeException("Failed to execute 'git ls-files'")
        }

        gitTrackedFiles.forEach { relativePath ->
            if (excludedPath.isEmpty() || !relativePath.startsWith(excludedPath)) {
                val sourceFile = repositoryRoot.resolve(relativePath)
                val targetFile = outputDir.resolve(relativePath)

                if (sourceFile.exists() && sourceFile.isFile) {
                    // Create parent directories if they don't exist
                    targetFile.parentFile.mkdirs()

                    // Copy the file, preserving permissions
                    sourceFile.toPath().copyTo(targetFile.toPath(), REPLACE_EXISTING, COPY_ATTRIBUTES)
                }
            }
        }
    }
}
