import org.gradle.api.DefaultTask
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
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
import org.gradle.api.tasks.options.Option
import org.kson.BinaryArtifactPaths
import org.kson.TinyCPreprocessor
import java.io.File
import javax.inject.Inject

abstract class CopyNativeHeaderTask @Inject constructor(
    private val objectFactory: ObjectFactory
) : DefaultTask() {

    @get:Input
    abstract val useDynamicLinking: Property<Boolean>

    @get:Input
    abstract val outputDir: Property<File>

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputHeaderFile: RegularFileProperty

    @get:OutputFile
    abstract val outputHeaderFile: RegularFileProperty

    init {
        val artifactsDir: Provider<Directory> = useDynamicLinking.flatMap { dynamic ->
            val sub = if (dynamic) "releaseShared" else "releaseStatic"
            val prop = objectFactory.directoryProperty()
            prop.set(project.projectDir.resolve("build/bin/nativeKson/$sub"))
            prop
        }

        inputHeaderFile.convention(
            useDynamicLinking.flatMap { dynamic -> artifactsDir.map { it.file(BinaryArtifactPaths.headerFileName(dynamic)) } }
        )

        outputHeaderFile.convention(
            artifactsDir.map { it.file("kson_api_preprocessed.h") }
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
