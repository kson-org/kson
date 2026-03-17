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

    val prepareSdistBuildEnvironment by register<Copy>("prepareSdistBuildEnvironment") {
        dependsOn("copyLicense", copyNativeArtifacts, ":kson-lib:generateJniBindingsJvm")
        group = "build"
        description = "Prepare kson-sdist directory with necessary Gradle files for sdist"

        val ksonCopyDir = layout.projectDirectory.dir("kson-sdist")

        // Clear existing kson-sdist directory
        doFirst {
            delete(ksonCopyDir)
        }

        // Copy gradlew scripts
        from(rootProject.file("gradlew"))
        from(rootProject.file("gradlew.bat"))
        into(ksonCopyDir)

        // Make gradlew executable
        filePermissions {
            unix("755")
        }

        // Copy gradle wrapper (excluding JDK)
        from(rootProject.file("gradle/wrapper")) {
            into("gradle/wrapper")
        }

        // Need this file to satisfy the `transpileCircleCiConfigTask`
        from(rootProject.file(".circleci/config.kson")){
            into(".circleci")
        }

        // Copy build configuration files
        from(rootProject.file("build.gradle.kts"))
        from(rootProject.file("settings.gradle.kts"))
        from(rootProject.file("gradle.properties"))
        from(rootProject.file("jdk.properties"))

        from(rootProject.file("src")){
            into("src")
            exclude("commonTest/**")
        }
        // Copy buildSrc (excluding build output and JDK)
        from(rootProject.file("buildSrc")) {
            into("buildSrc")
            exclude("build/**")
            exclude(".gradle/**")
            exclude("gradle/jdk/**")
            exclude(".kotlin/**")
            exclude("support/**")
            exclude("out/**")
        }

        // Copy kson-lib source (needed for native artifact build)
        from(rootProject.file("kson-lib")) {
            into("kson-lib")
            exclude("build/**")
            exclude(".gradle/**")
        }

        // Copy lib-python (build files and source, excluding native binaries
        // which are platform-specific and must be built from source)
        // Keep in sync with PLATFORM_NATIVE_LIBRARIES in build_backend.py
        from(project.projectDir) {
            into("lib-python")
            include("build.gradle.kts")
            include("src/**")
            exclude("src/kson/kson.dll")
            exclude("src/kson/libkson.dylib")
            exclude("src/kson/libkson.so")
        }
    }

    register<Exec>("createSdistBuildEnvironment"){
        dependsOn(prepareSdistBuildEnvironment)
        group = "build"
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
