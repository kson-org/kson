package org.kson.bindings

import kotlinx.serialization.json.Json
import java.nio.file.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.io.path.writeText

interface LanguageSpecificBindingsGenerator {
    fun generate(): String
}

class BindGenLanguage(val generator: LanguageSpecificBindingsGenerator, val targetDir: String, val targetFile: String)

class BindingsGenerator(val binaryDir: Path, val metadataPath: Path, val destinationDir: Path) {

    fun generateAll() {
        val packageMetadata = Json.decodeFromString<SimplePackageMetadata>(metadataPath.readText())
        val languages = arrayOf(
            BindGenLanguage(PythonGen(packageMetadata), "kson-py", "lib.py"),
            BindGenLanguage(RustGen(packageMetadata), "kson-rs", "src/lib.rs")
        )

        languages.forEach {
            val code = it.generator.generate()
            destinationDir.resolve("${it.targetDir}/${it.targetFile}").writeText(code)
            destinationDir.resolve("${it.targetDir}/libkson").createDirectories()

            // Place the preprocessed .h file in the bindings' dir
            TinyCPreprocessor().preprocess(
                binaryDir.resolve("kson_api.h").pathString,
                destinationDir.resolve("${it.targetDir}/libkson/kson.h").pathString)

            // Place the DLL
            binaryDir.resolve("kson.dll").copyTo(destinationDir.resolve("${it.targetDir}/libkson/kson.dll"), true)
        }
    }
}