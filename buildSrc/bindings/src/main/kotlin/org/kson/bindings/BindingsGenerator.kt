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
     * @param packageMetadata metadata about Kson's public API
     * @return A string representing a valid program in the target language, providing a high-level
     *         wrapper around Kson's low-level bindings
     */
    fun generate(packageMetadata: SimplePackageMetadata): String
}

class BindGenLanguage(
    val generator: LanguageSpecificBindingsGenerator,
    val targetDir: String,
    val targetFile: String,
    val testCommand: List<String>
)

class BindingsGenerator(val binaryDir: Path, val sourceBinaryDir: Path, val metadataPath: Path, val destinationDir: Path) {
    companion object {
        fun languages(): List<BindGenLanguage> {
            return listOf(
                // See `bindings/src/main/python` for the related non-generated files
                BindGenLanguage(PythonGen(), "python", "lib.py", listOf("uv", "run", "pytest")),

                // See `bindings/src/main/rust` for the related non-generated files
                BindGenLanguage(RustGen(), "rust", "src/generated.rs", listOf("cargo", "test"))
            )
        }

        private fun getHeaderFileName(): String {
            return when {
                Platform.isWindows -> "kson_api.h"
                Platform.isLinux -> "libkson_api.h"
                Platform.isMacOs -> "kson_api.h"
                else -> throw Exception("Unsupported OS")
            }
        }

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
        languages().forEach {
            // First copy the non-generated files for this language
            copyDirectoryRecursively(
                sourceBinaryDir.resolve(it.targetDir),
                destinationDir.resolve(it.targetDir)
            )

            // Now generate the wrapper
            val code = it.generator.generate(packageMetadata)
            destinationDir.resolve("${it.targetDir}/${it.targetFile}").writeText(code)

            // Place the preprocessed .h and binary in the bindings' dir
            destinationDir.resolve("${it.targetDir}/libkson").createDirectories()
            val headerFileName = getHeaderFileName()
            TinyCPreprocessor().preprocess(
                binaryDir.resolve(headerFileName).pathString,
                destinationDir.resolve("${it.targetDir}/libkson/kson.h").pathString
            )
            binaryDir.resolve(Platform.sharedLibraryName)
                .copyTo(destinationDir.resolve("${it.targetDir}/libkson/${Platform.sharedLibraryName}"), true)
        }
    }
}
