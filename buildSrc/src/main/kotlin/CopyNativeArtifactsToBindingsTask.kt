import org.gradle.api.DefaultTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFiles
import org.gradle.api.tasks.TaskAction
import org.kson.BinaryArtifactPaths
import org.kson.TinyCPreprocessor
import java.io.File
import kotlin.io.resolve

open class CopyNativeArtifactsToBindingsTask : DefaultTask() {
    private val sourceBinary: File
    private val sourceHeader: File
    private val outputHeaders: List<File>
    private val outputBinaries: List<File>

    init {
        val projectRoot = project.projectDir.parentFile
        val sourceRoot = projectRoot.resolve("lib-kotlin/build/bin/nativeKson/releaseShared/")

        val binaryFileName = BinaryArtifactPaths.binaryFileName()
        sourceBinary = sourceRoot.resolve(binaryFileName)
        sourceHeader = sourceRoot.resolve(BinaryArtifactPaths.headerFileName())

        // We need to copy the artifacts into each bindings directory
        val outputBinaries = mutableListOf<File>()
        val outputHeaders = mutableListOf<File>()
        KsonBindings.ALL.forEach { bindings ->
            val destinationDir = projectRoot.resolve(bindings.dir)

            outputBinaries.add(destinationDir.resolve(binaryFileName))

            // Always use the same file name for simplicity (instead of reusing the original name,
            // which is platform-dependent
            outputHeaders.add(destinationDir.resolve("kson_api.h"))
        }

        this.outputHeaders = outputHeaders.toList()
        this.outputBinaries = outputBinaries.toList()
    }

    @InputFiles
    fun getInputFiles(): List<File> {
        return listOf(sourceBinary, sourceHeader)
    }

    @OutputFiles
    fun getOutputFiles(): List<File> {
        return outputHeaders + outputBinaries
    }

    @TaskAction
    fun run() {
        val binary = sourceBinary.readBytes()
        val preprocessedHeader = TinyCPreprocessor().preprocess(sourceHeader.path)

        outputHeaders.forEach {
            it.writeText(preprocessedHeader)
        }

        outputBinaries.forEach {
            it.writeBytes(binary)
        }
    }
}