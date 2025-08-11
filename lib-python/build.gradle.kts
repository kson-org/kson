val copyNativeArtifacts = "copyNativeArtifacts"
val test = "test"

tasks {
    register<CopyNativeArtifactsTask>(copyNativeArtifacts) {
        dependsOn(":nativeKsonBinaries")
    }

    register<Exec>(test) {
        dependsOn(copyNativeArtifacts)

        group = "verification"
        commandLine = "uv run pytest".split(" ")

        // Ensure the subprocess can find the kson shared library
        val (libraryPathVariable, libraryPathSeparator) = when {
            org.gradle.internal.os.OperatingSystem.current().isWindows -> Pair("PATH", ";")
            else -> Pair("LD_LIBRARY_PATH", ":")
        }
        var libraryPath = System.getenv(libraryPathVariable) ?: ""
        if (libraryPath.isNotEmpty() && !libraryPath.endsWith(libraryPathSeparator)) {
            libraryPath += libraryPathSeparator
        }
        libraryPath += project.file(project.projectDir)
        environment(libraryPathVariable, libraryPath)

        // Show stdout and stderr
        standardOutput = System.out
        errorOutput = System.err

        // Ensure the task fails if the test command fails
        isIgnoreExitValue = false
    }
}
