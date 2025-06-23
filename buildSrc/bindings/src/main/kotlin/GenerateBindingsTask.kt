import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.kson.bindings.BindingsGenerator

open class GenerateBindingsTask : DefaultTask() {
    private val bindingsGenerator: BindingsGenerator

    init {
        val projectRoot = project.projectDir.toPath()

        val destinationDir = projectRoot.resolve("tooling/bindings")
        val sourceBinaryDir = projectRoot.resolve("build/bin/nativeKson/releaseShared/")
        val metadataPath = projectRoot.resolve("build/generated/ksp/metadata/commonMain/resources/org/kson/public-api.json")

        bindingsGenerator = BindingsGenerator(
            sourceBinaryDir,
            metadataPath,
            destinationDir,
        )

        dependsOn("kspCommonMainKotlinMetadata", "nativeKsonBinaries")
    }

    @TaskAction
    fun generate() {
        bindingsGenerator.generateAll()
    }
}