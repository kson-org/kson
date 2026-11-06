import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.os.OperatingSystem
import org.gradle.process.ExecOperations
import org.kson.GraalVmHelper
import org.kson.GraalVmStaticLibrary
import javax.inject.Inject

/**
 * Builds `libkson.a` from the files a shared native-image build leaves in its temp directory; see
 * [GraalVmStaticLibrary] for how and why. Writes into [outputDir]:
 *
 * - `libkson.a`
 * - `libkson.link-libs`: the system libraries to link alongside it, one per line in cargo's
 *   `rustc-link-lib` syntax, for `lib-rust/kson-sys/build.rs`
 * - [headers], copied as-is
 *
 * macOS and Linux only: Windows has no `ld -r` step in the toolchain native-image uses there.
 */
abstract class BuildGraalVmStaticLibraryTask : DefaultTask() {
    @get:Inject
    abstract val execOperations: ExecOperations

    /** The directory passed to native-image as `-H:TempDirectory` */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val nativeImageTempDir: DirectoryProperty

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val headers: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun build() {
        val os = OperatingSystem.current()
        if (!os.isMacOsX && !os.isLinux) {
            throw GradleException("A static kson-lib can only be built on macOS and Linux")
        }

        // native-image writes its files into a fresh `SVM-<timestamp>` directory under the temp dir
        val svmDirs = nativeImageTempDir.get().asFile.listFiles { f -> f.isDirectory && f.name.startsWith("SVM-") }.orEmpty()
        val svmDir = svmDirs.singleOrNull()
            ?: throw GradleException("expected exactly one SVM-* directory in ${nativeImageTempDir.get()}, found ${svmDirs.size}; rerun buildWithGraalVmNativeImage")
        val imageObject = svmDir.resolve("libkson.o")
        val exportedSymbolsList = svmDir.resolve("exported_symbols.list")

        val targetLibraries = GraalVmStaticLibrary.targetLibraries(imageObject.readBytes())
        val graalHome = GraalVmHelper.getGraalVMHome()
        val platformDir = "${if (os.isMacOsX) "darwin" else "linux"}-${graalArch()}"
        val archives = targetLibraries.staticLibraries.map {
            GraalVmStaticLibrary.findArchive(graalHome, platformDir, it)
        }

        val out = outputDir.get().asFile
        out.deleteRecursively()
        out.mkdirs()
        val workDir = temporaryDir
        val prelinked = workDir.resolve("kson-prelinked.o")
        val staticLibrary = out.resolve("libkson.a")

        if (os.isMacOsX) {
            // ld64 hides every global not on the list, in the same step that resolves the archives
            exec("ld", "-r", "-arch", macArch(), "-exported_symbols_list", exportedSymbolsList.path,
                "-o", prelinked.path, imageObject.path, *archives.map { it.path }.toTypedArray())
            exec("libtool", "-static", "-o", staticLibrary.path, prelinked.path)
        } else {
            val combined = workDir.resolve("kson-combined.o")
            // Some of the inputs carry no .note.GNU-stack, which would mark the result as needing an
            // executable stack, and lld (rustc's default linker on x86_64 Linux) refuses to link that
            exec("ld", "-r", "-z", "noexecstack", "-o", combined.path, imageObject.path, *archives.map { it.path }.toTypedArray())
            val keepList = workDir.resolve("exported-symbols.txt")
            keepList.writeText(GraalVmStaticLibrary.exportedSymbols(exportedSymbolsList.readText()).joinToString("\n", postfix = "\n"))
            // The image heap is read-only but full of absolute pointers. In a shared library GNU ld
            // patches those with text relocations, which lld (rustc's default on x86_64 Linux) won't
            // emit into a PIE, so mark the section writable and let them be ordinary data relocations.
            exec("objcopy", "--keep-global-symbols=${keepList.path}",
                "--set-section-flags", ".svm_heap=alloc,load,contents,data", combined.path, prelinked.path)
            exec("ar", "rcs", staticLibrary.path, prelinked.path)
        }

        out.resolve("libkson.link-libs").writeText(
            targetLibraries.libraries.joinToString("\n", postfix = "\n") { GraalVmStaticLibrary.cargoLinkLib(it) }
        )
        headers.forEach { it.copyTo(out.resolve(it.name), overwrite = true) }
    }

    private fun exec(vararg command: String) {
        execOperations.exec { it.commandLine(*command) }
    }

    /** GraalVM's spelling of this machine's architecture in its `lib/**/<os>-<arch>` directories */
    private fun graalArch(): String = when (val arch = System.getProperty("os.arch")) {
        "aarch64", "arm64" -> "aarch64"
        "x86_64", "amd64" -> "amd64"
        else -> throw GradleException("Unsupported CPU architecture: $arch")
    }

    /** The `-arch` value macOS's `ld` expects for this machine */
    private fun macArch(): String = if (graalArch() == "aarch64") "arm64" else "x86_64"
}
