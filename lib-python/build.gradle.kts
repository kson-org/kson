import org.gradle.internal.os.OperatingSystem

val build = "build"
val copyNativeArtifacts = "copyNativeArtifacts"
val formattingCheck = "formattingCheck"
val test = "test"
val typeCheck = "typeCheck"

tasks {
    val uvwPath = if (OperatingSystem.current().isWindows) {
        "cmd /c uvw.bat"
    } else {
        "./uvw"
    }

    register<CopyNativeArtifactsTask>(copyNativeArtifacts) {
        dependsOn(":lib-kotlin:nativeKsonBinaries")
    }

    register<Task>(build) {
        dependsOn(copyNativeArtifacts)
    }

    register<Exec>(test) {
        dependsOn(build)

        group = "verification"
        commandLine = "$uvwPath run pytest".split(" ")
        standardOutput = System.out
        errorOutput = System.err
        isIgnoreExitValue = false

        // Ensure the subprocess can find the kson shared library
        injectSharedLibraryPath()
    }

    register<Exec>(typeCheck) {
        group = "verification"
        commandLine = "$uvwPath run pyright".split(" ")
        standardOutput = System.out
        errorOutput = System.err
        isIgnoreExitValue = false
    }

    register<Exec>(formattingCheck) {
        group = "verification"
        commandLine = "$uvwPath run ruff format --diff".split(" ")
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
