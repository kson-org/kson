import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.kson.Kson
import org.kson.Result

/**
 * Gradle task that transpiles KSON files to YAML format.
 *
 * This task reads a KSON input file, transpiles it to YAML using the Kson library,
 * and writes the result to an output file. The task is configured with input/output
 * file tracking for Gradle's up-to-date checking.
 *
 * @property ksonFile The input KSON file to transpile
 * @property yamlFile The output YAML file to write the transpiled result to
 *
 * @throws GradleException if the input file does not exist or transpilation fails
 */
abstract class TranspileKsonToYaml : DefaultTask() {
    /**
     * The input KSON file to transpile.
     */
    @get:InputFile
    abstract val ksonFile: RegularFileProperty

    /**
     * The output YAML file to write the transpiled result to.
     */
    @get:OutputFile
    abstract val yamlFile: RegularFileProperty

    init {
        group = "kson-transpilation"
        description = "Transpile KSON to YAML file"
    }

    /**
     * Performs the transpilation from KSON to YAML.
     *
     * Reads the input KSON file, transpiles it using [Kson.toYaml] with embed tags
     * removed, and writes the result to the output YAML file.
     *
     * @throws GradleException if the input file does not exist or transpilation fails
     */
    @TaskAction
    fun transpile() {
        val ksonInput = ksonFile.get().asFile
        val yamlOutput = yamlFile.get().asFile

        if (!ksonInput.exists()) {
            throw GradleException("KSON input file does not exist: ${ksonInput.absolutePath}")
        }

        logger.lifecycle("Transpiling ${ksonInput.name} to ${yamlOutput.name}...")

        val ksonContent = ksonInput.readText()

        when (val result = Kson.toYaml(ksonContent, retainEmbedTags = false)) {
            is Result.Success -> {
                yamlOutput.writeText(
                    """
                    |# !!!!!
                    |# THIS FILE IS AUTO-GENERATED FROM ${ksonInput.name}
                    |# DO NOT EDIT! IT WILL BE OVERWRITTEN ON NEXT BUILD
                    |# TO MODIFY, EDIT ${ksonInput.name} AND RUN ANY GRADLE TASK
                    |# !!!!!
                    |${result.output}
                    """.trimMargin())
                logger.lifecycle("Successfully transpiled to ${yamlOutput.name}")
            }
            is Result.Failure -> {
                val errorMessages = result.errors.joinToString("\n") { error ->
                    "  [${error.severity}] Line ${error.start.line + 1}, Column ${error.start.column + 1}: ${error.message}"
                }
                throw GradleException("Failed to transpile KSON to YAML:\n$errorMessages")
            }
        }
    }
}