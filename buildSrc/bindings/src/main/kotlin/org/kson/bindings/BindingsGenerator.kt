package org.kson.bindings

import kotlinx.serialization.json.Json
import org.kson.metadata.SimplePackageMetadata
import java.nio.file.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Responsible for generating Kson bindings for a particular programming language
 */
interface LanguageSpecificBindingsGenerator {
    /**
     * Generate Kson bindings
     *
     * Note: since this method takes no arguments, each implementation of
     * [LanguageSpecificBindingsGenerator] should obtain the necessary input data through other
     * means (e.g. in its constructor).
     *
     * @return A string representing a valid program in the target language, providing a high-level
     *         wrapper around Kson's low-level bindings
     */
    fun generate(): String
}

class BindGenLanguage(val generator: LanguageSpecificBindingsGenerator, val targetDir: String, val targetFile: String)

class BindingsGenerator(val binaryDir: Path, val sourceBinaryDir: Path, val metadataPath: Path, val destinationDir: Path) {

    fun generateAll() {
        val packageMetadata = Json.decodeFromString<SimplePackageMetadata>(metadataPath.readText())
        val languages = arrayOf(
            // See `bindings/src/main/python` for the related non-generated files
            BindGenLanguage(PythonGen(packageMetadata), "python", "lib.py"),

            // See `bindings/src/main/rust` for the related non-generated files
            BindGenLanguage(RustGen(packageMetadata), "rust", "src/lib.rs")
        )

        languages.forEach {
            // First copy the non-generated files for this language
            sourceBinaryDir.resolve(it.targetDir).toFile()
                .copyRecursively(target = destinationDir.resolve(it.targetDir).toFile(), overwrite = true)

            // Now generate the wrapper
            val code = it.generator.generate()
            destinationDir.resolve("${it.targetDir}/${it.targetFile}").writeText(code)

            // Place the preprocessed .h and binary in the bindings' dir
            destinationDir.resolve("${it.targetDir}/libkson").createDirectories()
            TinyCPreprocessor().preprocess(
                binaryDir.resolve("kson_api.h").pathString,
                destinationDir.resolve("${it.targetDir}/libkson/kson.h").pathString
            )
            binaryDir.resolve("kson.dll")
                .copyTo(destinationDir.resolve("${it.targetDir}/libkson/kson.dll"), true)
        }
    }
}