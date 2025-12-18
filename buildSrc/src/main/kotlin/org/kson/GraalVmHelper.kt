package org.kson

import org.gradle.api.GradleException
import java.io.File

object GraalVmHelper {
    private val os = System.getProperty("os.name").lowercase()

    /**
    * Gets the GraalVM home directory. Since Gradle is already running with the JDK from
    * jdk.properties (via the jvm.wrapper plugin), we just use Gradle's own JAVA_HOME.
    * @return The GraalVM home directory
    * @throws GradleException if GraalVM JDK is not found
    */
    fun getGraalVMHome(): File {
        val javaHome = File(System.getProperty("java.home"))

        // Verify it's actually GraalVM
        val nativeImageExe = File(javaHome, "bin/native-image${getNativeImageExtension()}")
        if (!nativeImageExe.exists()) {
            throw GradleException(
                "native-image not found at $nativeImageExe. " +
                "The JDK at $javaHome does not appear to be GraalVM. " +
                "Please ensure jdk.properties points to a GraalVM distribution."
            )
        }

        return javaHome
    }

    /**
    * Determines the file extension for the native-image executable based on the operating system.
    * @return A file extension string: ".cmd" for Windows, empty string for macOS/Linux
    */
    fun getNativeImageExtension(): String {
        return if (os.contains("win")) ".cmd" else ""
    }
}