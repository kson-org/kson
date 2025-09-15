import org.gradle.api.DefaultTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.TaskAction
import org.kson.BinaryArtifactPaths
import org.kson.TinyCPreprocessor
import java.io.File
import kotlin.io.resolve

open class CopyNativeArtifactsTask : DefaultTask() {
    private val sourceBinary: File
    private val sourceHeader: File
    private val outputBinary: File
    private val outputHeader: File

    init {
        val artifactsDir = project.projectDir.parentFile.resolve("kson-lib/build/bin/nativeKson/releaseShared/")
        val binaryFileName = BinaryArtifactPaths.binaryFileName()
        sourceBinary = artifactsDir.resolve(binaryFileName)
        sourceHeader = artifactsDir.resolve(BinaryArtifactPaths.headerFileName())

        val targetDirectory = project.projectDir.resolve("src/kson")
        outputBinary = targetDirectory.resolve(binaryFileName)
        outputHeader = targetDirectory.resolve("kson_api.h")
    }

    @InputFiles
    fun getInputFiles(): List<File> {
        return listOf(sourceBinary, sourceHeader)
    }

    @OutputFiles
    fun getOutputFiles(): List<File> {
        return listOf(outputHeader, outputBinary)
    }

    @TaskAction
    fun run() {
        val binary = sourceBinary.readBytes()
        val preprocessedHeader = TinyCPreprocessor().preprocess(sourceHeader.path)
        outputHeader.writeText(preprocessedHeader)
        outputBinary.writeBytes(binary)
    }
}
