import org.gradle.internal.os.OperatingSystem
import org.kson.PythonSdist
import org.kson.ReleaseArtifactSet

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

    val buildSdist = register<Exec>("buildSdist") {
        dependsOn("copyLicense")
        group = "build"
        description = "Build the source distribution, which carries no native library and so builds no wheel"
        commandLine = "$uvwPath build --sdist".split(" ")
        doNotTrackState("Gradle doesn't see the sources uv builds from")
        val sdist = file("dist/${PythonSdist.fileName(file("pyproject.toml"))}")
        outputs.file(sdist)
        // setuptools names the sdist
        doLast { check(sdist.isFile) { "uv build did not write $sdist" } }
    }

    register<Copy>("packageReleaseSdist") {
        group = "distribution"
        description = "Copies the source distribution for release into the root project's release-artifacts/portable directory"

        from(buildSdist)
        into(ReleaseArtifactSet.PORTABLE.stageInto(this))
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
