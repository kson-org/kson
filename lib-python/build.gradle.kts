import org.gradle.internal.os.OperatingSystem

val build = "build"
val copyNativeArtifacts = "copyNativeArtifacts"
val formattingCheck = "formattingCheck"
val test = "test"
val typeCheck = "typeCheck"
val prepareSdistBuildEnvironment = "prepareSdistBuildEnvironment"
val createSdistBuildEnvironment = "createSdistBuildEnvironment"
val buildWheel = "buildWheel"

tasks {
    val uvwPath = if (OperatingSystem.current().isWindows) {
       "cmd /c uvw.bat"
    } else {
        "./uvw"
    }

    register<CopyNativeArtifactsTask>(copyNativeArtifacts) {
        dependsOn(":kson-lib:nativeKsonBinaries")
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

    register<Copy>(prepareSdistBuildEnvironment) {
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

        // Need this file to satisfy the
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

        // Copy lib-python (build files and source)
        from(project.projectDir) {
            into("lib-python")
            include("build.gradle.kts")
            include("src/**")
        }
    }

    register<Exec>(createSdistBuildEnvironment){
        dependsOn(prepareSdistBuildEnvironment)
        group = "build"
        commandLine = "$uvwPath build --sdist".split(" ")
    }

    register<Copy>("copyLicense") {
        from(rootProject.file("LICENSE"))
        into(project.projectDir)
    }

    register<Exec>(buildWheel) {
        dependsOn(copyNativeArtifacts, "copyLicense")
        group = "build"
        description = "Build platform-specific wheel distribution with cibuildwheel"
        commandLine = "$uvwPath run cibuildwheel --platform auto --output-dir dist .".split(" ")
        standardOutput = System.out
        errorOutput = System.err
        isIgnoreExitValue = false

        // Configure cibuildwheel
        environment("CIBW_BUILD", "cp310-*")  // Build for Python 3.10+
        environment("CIBW_SKIP", "*-musllinux_*")  // Skip musl Linux builds
        environment("CIBW_ARCHS", "native")  // Build only for native architecture
        environment("CIBW_TEST_REQUIRES", "pytest")  // Install pytest for testing
        environment("CIBW_TEST_COMMAND", "pytest -v {project}/tests")

        doLast {
            println("Successfully built platform-specific wheel using cibuildwheel")
        }
    }
}
