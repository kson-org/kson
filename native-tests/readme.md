# KSON native tests

This subproject compiles the KSON root project's tests to native and runs them.
Tests will automatically run upon `./gradlew check`, but you can also run them
explicitly through `./gradlew :native-tests:nativeTest`. Compilation to native
is handled by GraalVM Native Image.

Ideally, this subproject would not exist at all, and the `nativeTest` command 
would be specified in the root project instead. However, the GraalVM Native
Image plugin (installed through `id("org.graalvm.buildtools.native")`) is
incompatible with the Kotlin Multiplatform one (from `kotlin("multiplatform")`).
Hence the need for a separate build that uses `kotlin("jvm")`. If the situation
changes in the future, we could move the build logic to the top-level
`build.gradle.kts`.
