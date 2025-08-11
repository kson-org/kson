val copyNativeArtifacts = "copyNativeArtifacts"
val test = "test"
val typeCheck = "typeCheck"
val formattingCheck = "formattingCheck"

tasks {
    register<CopyNativeArtifactsTask>(copyNativeArtifacts) {
        dependsOn(":nativeKsonBinaries")
    }

    register<Exec>(test) {
        dependsOn(copyNativeArtifacts)

        group = "verification"
        commandLine = "uv run pytest".split(" ")
        standardOutput = System.out
        errorOutput = System.err
        isIgnoreExitValue = false

        // Ensure the subprocess can find the kson shared library
        injectSharedLibraryPath()
    }

    register<Exec>(typeCheck) {
        group = "verification"
        commandLine = "uv run pyright".split(" ")
        standardOutput = System.out
        errorOutput = System.err
        isIgnoreExitValue = false
    }

    register<Exec>(formattingCheck) {
        group = "verification"
        commandLine = "uv run ruff format --diff".split(" ")
        standardOutput = System.out
        errorOutput = System.err
        isIgnoreExitValue = false
    }

    register<Task>("check") {
        dependsOn(test)
        dependsOn(typeCheck)
        dependsOn(formattingCheck)
    }
}
