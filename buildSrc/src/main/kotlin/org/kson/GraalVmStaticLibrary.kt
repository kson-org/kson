package org.kson

import org.gradle.api.GradleException
import java.io.File

/**
 * Turns the relocatable object GraalVM native-image writes on its way to a shared library into a
 * static library, `libkson.a`, that a C or Rust program can link without carrying `libkson` beside
 * it at runtime.
 *
 * native-image has no static library output, but every image starts life as one object file,
 * `libkson.o`, which it then links against a handful of static archives from the GraalVM install
 * (the JDK's native code and SubstrateVM's C helpers). That object, those archives and the list of
 * symbols the shared library exports are all we need: pre-link them into a single object with
 * only the exported symbols left global, and archive that. Pre-linking keeps the JDK's internal C
 * symbols out of the consumer's symbol namespace, the same as the shared library does.
 *
 * Which archives and system libraries to use is not repeated here: native-image records both in
 * the object, so they follow whichever GraalVM version built it (see [targetLibraries]).
 */
object GraalVmStaticLibrary {
    /** Where native-image records the static archives it links into the image */
    private const val STATIC_LIBRARIES_KEY = "com.oracle.svm.core.VM.Target.StaticLibraries="

    /** Where native-image records the system libraries the image needs at link time */
    private const val LIBRARIES_KEY = "com.oracle.svm.core.VM.Target.Libraries="

    data class TargetLibraries(
        /** File names of the GraalVM static archives to bundle, e.g. `libjvm.a` */
        val staticLibraries: List<String>,
        /** System libraries as native-image spells them, e.g. `z` or `-framework Foundation` */
        val libraries: List<String>,
    )

    /**
     * Reads the archive and library lists native-image embeds in the image as
     * `com.oracle.svm.core.VM.Target.StaticLibraries=a.a|b.a` and
     * `com.oracle.svm.core.VM.Target.Libraries=dl|z`.
     */
    fun targetLibraries(imageObject: ByteArray): TargetLibraries {
        // ISO-8859-1 maps every byte to one char, so offsets line up and nothing fails to decode
        val text = String(imageObject, Charsets.ISO_8859_1)

        fun valueOf(key: String): List<String> {
            val start = text.indexOf(key)
            if (start < 0) {
                throw GradleException("`$key` not found in the native-image object; has GraalVM changed how it records its link inputs?")
            }
            val valueStart = start + key.length
            val end = text.indexOf('\u0000', valueStart).takeIf { it >= 0 } ?: text.length
            return text.substring(valueStart, end).split('|').filter { it.isNotBlank() }
        }

        return TargetLibraries(valueOf(STATIC_LIBRARIES_KEY), valueOf(LIBRARIES_KEY))
    }

    /**
     * Translates a library as native-image names it into cargo's `rustc-link-lib` syntax:
     * `z` stays `z`, `-framework Foundation` becomes `framework=Foundation`.
     */
    fun cargoLinkLib(library: String): String {
        val framework = library.trim().removePrefix("-framework").trim()
        return if (framework != library.trim()) "framework=$framework" else library.trim()
    }

    /**
     * Reads the symbols the shared library exports from native-image's `exported_symbols.list`.
     * On macOS it is one symbol per line; on Linux it is a linker version script
     * (`{ global: "a"; "b"; local: *; };`), of which we want the `global` names, unquoted.
     */
    fun exportedSymbols(exportedSymbolsList: String): List<String> {
        if (!exportedSymbolsList.contains("global:")) {
            return exportedSymbolsList.lines().map { it.trim() }.filter { it.isNotEmpty() }
        }
        val globals = exportedSymbolsList.substringAfter("global:").substringBefore("local:")
        return globals.split(';', '\n').map { it.trim().removeSurrounding("\"") }.filter { it.isNotEmpty() }
    }

    /**
     * Finds [archiveName] among the directories GraalVM keeps its static archives in for this
     * platform, the same ones native-image passes to the linker with `-L`.
     */
    fun findArchive(graalHome: File, platformDir: String, archiveName: String): File {
        val searchDirs = listOf(
            graalHome.resolve("lib/svm/clibraries/$platformDir"),
            graalHome.resolve("lib/static/$platformDir"),
            // Linux GraalVM keeps the JDK archives per libc; native-image defaults to glibc
            graalHome.resolve("lib/static/$platformDir/glibc"),
        )
        return searchDirs.map { it.resolve(archiveName) }.firstOrNull { it.isFile }
            ?: throw GradleException("$archiveName not found in any of ${searchDirs.joinToString()}")
    }
}
