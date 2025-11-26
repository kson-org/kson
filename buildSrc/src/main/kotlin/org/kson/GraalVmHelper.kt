package org.kson

import org.gradle.api.GradleException
import org.gradle.api.Project
import java.io.File

object GraalVmHelper {
    val os = System.getProperty("os.name").lowercase()

    /**
    * Locates the GraalVM JDK installation directory within the gradle/jdk folder.
    * The JDK is automatically downloaded by the Gradle wrapper based on jdk.properties.
    * Handles platform-specific directory structures (e.g., macOS's Contents/Home subdirectory).
    * @return The GraalVM home directory
    * @throws GradleException if GraalVM JDK is not found
    */
    fun getGraalVMHome(rootProject: Project): File {
        val graalvmDir = File("${rootProject.projectDir}/gradle/jdk")

        val graalvmTopLevelDir = graalvmDir.listFiles()?.find {
            it.isDirectory && it.name.contains("graalvm")
        } ?: throw GradleException("GraalVM JDK not found in $graalvmDir (no top-level dir present). Run './gradlew' to download it.")

        // In MacOS the graalvm directory is unpacked inside a graalvm directory, try to find it
        val graalvmUnpackDir = graalvmTopLevelDir.listFiles()?.find {
            it.isDirectory && it.name.contains("graalvm")
        } ?: graalvmTopLevelDir  // Fall back to top-level if no nested dir found

        // Special handling for macOS: use Contents/Home subdirectory if it exists
        return if ((os.contains("mac") || os.contains("darwin")) && File("$graalvmUnpackDir/Contents/Home").exists()) {
            File("$graalvmUnpackDir/Contents/Home")
        } else {
            graalvmUnpackDir
        }
    }

    /**
    * Determines the file extension for the native-image executable based on the operating system.
    * @return A file extension string: ".cmd" for Windows, empty string for macOS/Linux
    */
    fun getNativeImageExtension(): String {
        return if (os.contains("win")) ".cmd" else ""
    }
}