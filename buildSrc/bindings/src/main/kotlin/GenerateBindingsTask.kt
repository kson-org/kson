import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.kson.bindings.BindingsGenerator

open class GenerateBindingsTask : DefaultTask() {
    private val bindingsGenerator: BindingsGenerator

    init {
        val projectRoot = project.projectDir.toPath()

        val sourceBinaryDir = projectRoot.resolve("build/bin/nativeKson/releaseShared/")
        val nonGeneratedSources = projectRoot.resolve("buildSrc/bindings/src/main")
        val metadataPath = projectRoot.resolve("build/generated/ksp/metadata/commonMain/resources/org/kson/public-api.json")
        val destinationDir = projectRoot.resolve("build/generated/bindings")

        bindingsGenerator = BindingsGenerator(
            sourceBinaryDir,
            nonGeneratedSources,
            metadataPath,
            destinationDir,
        )

        description = "Generate language bindings for Kson"
        group = "build"
    }

    @TaskAction
    fun generate() {
        bindingsGenerator.generateAll()
    }
}