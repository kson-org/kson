import org.gradle.internal.os.OperatingSystem

tasks {
    val uvwPath = if (OperatingSystem.current().isWindows) {
       "cmd /c uvw.bat"
    } else {
        "./uvw"
    }

    val copyNativeArtifacts by register<CopyNativeArtifactsTask>("copyNativeArtifacts") {
        dependsOn(":kson-lib:buildWithGraalVmNativeImage")
    }

    val build by register<Task>("build") {
        dependsOn(copyNativeArtifacts)
    }

    val test by register<Exec>("test") {
        dependsOn(build)

        group = "verification"
        commandLine = "$uvwPath run pytest".split(" ")
        standardOutput = System.out
        errorOutput = System.err
        isIgnoreExitValue = false
    }

    val validateReadme by register<Exec>("validateReadme") {
        dependsOn(build)

        group = "verification"
        description = "Validates Python code blocks in readme.md"
        commandLine = "$uvwPath run pytest --codeblocks readme.md".split(" ")
        standardOutput = System.out
        errorOutput = System.err
        isIgnoreExitValue = false
    }

    val typeCheck by register<Exec>("typeCheck") {
        group = "verification"
        commandLine = "$uvwPath run pyright".split(" ")
        standardOutput = System.out
        errorOutput = System.err
        isIgnoreExitValue = false
    }

    register<Task>("check") {
        dependsOn(test)
        dependsOn(validateReadme)
        dependsOn(typeCheck)
    }

    register<Exec>("buildSdist") {
        dependsOn("copyLicense")
        group = "build"
        description = "Build the source distribution, which carries no native library and so builds no wheel"
        commandLine = "$uvwPath build --sdist".split(" ")
    }

    register<Copy>("copyLicense") {
        from(rootProject.file("LICENSE"))
        into(project.projectDir)
    }

    register<Exec>("buildWheel") {
        dependsOn(copyNativeArtifacts, "copyLicense")
        group = "build"
        description = "Build platform-specific wheel distribution with cibuildwheel"
        commandLine = "$uvwPath run cibuildwheel --platform auto --output-dir dist .".split(" ")
        standardOutput = System.out
        errorOutput = System.err
        isIgnoreExitValue = false

        doLast {
            println("Successfully built platform-specific wheel using cibuildwheel")
        }
    }
}
