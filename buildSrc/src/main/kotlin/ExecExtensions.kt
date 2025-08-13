import org.gradle.api.tasks.Exec

fun Exec.injectSharedLibraryPath() {
    val (libraryPathVariable, libraryPathSeparator) = when {
        org.gradle.internal.os.OperatingSystem.current().isWindows -> Pair("PATH", ";")
        else -> Pair("LD_LIBRARY_PATH", ":")
    }
    var libraryPath = System.getenv(libraryPathVariable) ?: ""
    if (libraryPath.isNotEmpty() && !libraryPath.endsWith(libraryPathSeparator)) {
        libraryPath += libraryPathSeparator
    }
    libraryPath += this.project.file(this.project.projectDir)
    this.environment(libraryPathVariable, libraryPath)
}