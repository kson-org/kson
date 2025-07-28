plugins {
    application
    java
    distribution
    kotlin("jvm")
    id("org.graalvm.buildtools.native") version "0.11.0"
}

group = "org.kson"
version = "1.0-SNAPSHOT"

val nativeDefaultLibc = "glibc"
val nativeDefaultTarget = "native" // or `compatibility`, or `x86-64-v3`, etc...
val graalvmVersion = 24
val graalvmChannel = "Oracle Corporation"
val enableEnterprise = "Oracle" in graalvmChannel
val releaseByDefault = false
val debugByDefault = true
val isRelease = (findProperty("native.release") as? String)?.toBoolean() ?: releaseByDefault
val isDebug = (findProperty("native.debug") as? String)?.toBoolean() ?: debugByDefault
val targetLibc = (findProperty("native.libc") as? String) ?: nativeDefaultLibc
val targetNative = (findProperty("native.target") as? String) ?: nativeDefaultTarget

fun MutableMap<String, String>.putKson(prop: String, value: Any) = put("org.kson.$prop", value.toString())

val buildTimeProps = buildMap {
    // JVM properties.
    put("java.awt.headless", "true")

    // `org.kson.version`
    putKson("version", version.toString())

    // `org.kson.release`
    putKson("release", isRelease.toString())

    // `org.kson.debug`
    putKson("debug", isDebug.toString())

    // `org.kson.libc`
    putKson("libc", targetLibc)

    // `org.kson.target`
    putKson("target", targetNative)
}

val graalvmToolchain = javaToolchains.launcherFor {
    languageVersion = JavaLanguageVersion.of(graalvmVersion)
    vendor = JvmVendorSpec.matching(graalvmChannel)
}

val commonNativeArgs = buildList {
    // Link and initialize all types at build time, except otherwise stated via `--initialize-at-run-time`. Persists the
    // heap to the binary for faster startup time.
    add("--link-at-build-time")
    add("--initialize-at-build-time")

    // Don't fall back to hybrid JVM images; instead, tell us where the failure was so we can fix it.
    add("--no-fallback")

    // Since Kson doesn't typically run for long periods of time (yet!), we don't really need to tune garbage collection
    // and can in fact drop it altogether, letting the OS clean up memory on exit.
    add("--gc=epsilon")

    // Set build-time properties.
    buildTimeProps.forEach { (k, v) ->
        add("-D$k=$v")
    }
}

fun nativeImageArgs(
    release: Boolean = releaseByDefault,
    debug: Boolean = debugByDefault,
    shared: Boolean = false,
    libc: String = nativeDefaultLibc,
    target: String = nativeDefaultTarget,
): List<String> = commonNativeArgs + buildList {
    // Name of the final binary.
    addAll(arrayOf("-o", "kson"))

    // Set the target native architecture; options include `compatibility`, which optimizes for supported CPU variety in
    // the final binary. `native` targets the host architecture; other arch options vary but typically line up with your
    // C compiler's supported suite.
    add("-march=$target")

    when (libc) {
        // Musl binaries are always static.
        "musl" -> {
            add("--libc=musl")
            add("--static")
        }

        else -> add("--libc=$libc")
    }

    if (enableEnterprise) {
        add("--enable-sbom=embed,export,cyclonedx")
        add("--emit=build-report")
    }

    // Generate debug information, if instructed, regardless of release mode.
    if (debug) add("-g")

    when {
        release -> add("-Os") // optimize for speed during release
        else -> add("-Ob") // optimize for build speed by default
    }
    when {
        // shared lib args
        shared -> {}

        // flags for entrypoints (main binaries)
        else -> {
            add("--install-exit-handlers")
        }
    }
}

repositories {
    mavenCentral()
}

graalvmNative {
    binaries {
        named("main") {
            javaLauncher = graalvmToolchain
            mainClass = "org.kson.cli.JvmMainKt"

            buildArgs(nativeImageArgs(
                release = isRelease,
                debug = isDebug,
                libc = targetLibc,
                target = targetNative,
            ))
        }
    }
}

dependencies {
    implementation(project(":"))
}
