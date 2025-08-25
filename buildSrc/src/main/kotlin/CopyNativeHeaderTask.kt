import org.gradle.api.DefaultTask
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.kson.BinaryArtifactPaths
import org.kson.TinyCPreprocessor

abstract class CopyNativeHeaderTask : DefaultTask() {

    @get:InputDirectory
    abstract val sourceProjectDir: DirectoryProperty

    @get:Input
    abstract val useDynamicLinking: Property<Boolean>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputHeaderFile: RegularFileProperty

    @get:OutputFile
    abstract val outputHeaderFile: RegularFileProperty

    init {
        val artifactsDir: Provider<Directory> = useDynamicLinking.flatMap { dynamic ->
            val sub = if (dynamic) "releaseShared" else "releaseStatic"
            sourceProjectDir.dir("lib-kotlin/build/bin/nativeKson/$sub")
        }

        inputHeaderFile.convention(
            artifactsDir.map { it.file(BinaryArtifactPaths.headerFileName()) }
        )

        outputHeaderFile.convention(
            outputDir.file("kson_api.h")
        )
    }

    @TaskAction
    fun run() {
        val input = inputHeaderFile.get().asFile
        val output = outputHeaderFile.get().asFile

        val preprocessedHeader = TinyCPreprocessor().preprocess(input.absolutePath)

        output.parentFile.mkdirs()
        output.writeText(preprocessedHeader)
    }
}
