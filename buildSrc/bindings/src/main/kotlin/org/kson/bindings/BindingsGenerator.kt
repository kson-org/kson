package org.kson.bindings

import kotlinx.serialization.json.Json
import org.kson.metadata.SimplePackageMetadata
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
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
    companion object {
        // We need a handwritten function to recursively copy a directory, because
        // `File.copyRecursively` doesn't allow overwriting files (only the top-level file / dir)
        private fun copyDirectoryRecursively(source: Path, target: Path) {
            Files.walkFileTree(source, object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    val targetDir = target.resolve(source.relativize(dir))
                    Files.createDirectories(targetDir)
                    return FileVisitResult.CONTINUE
                }

                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    val targetFile = target.resolve(source.relativize(file))
                    Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING)
                    return FileVisitResult.CONTINUE
                }
            })
        }
    }

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
            copyDirectoryRecursively(
                sourceBinaryDir.resolve(it.targetDir),
                destinationDir.resolve(it.targetDir)
            )

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