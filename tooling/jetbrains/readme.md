# [KSON](https://kson.org) support for [JetBrains IDEs](https://www.jetbrains.com/products/#type=ide)

This [Gradle subproject](https://docs.gradle.org/current/userguide/multi_project_builds.html) implements [KSON](https://kson.org) support for [JetBrains IDEs](https://www.jetbrains.com/products/#type=ide)

### Development

This is a subproject of the main [KSON](../../readme.md) implementation, and so requires no special setup.

**NOTE: all subproject Gradle commands are run from the [project root](../..)**.

Some useful Gradle commands for this subproject:

```bash
# Run IntelliJ IDEA with the development plugin installed
./gradlew :tooling:jetbrains:runIde

# Run the plugin tests
./gradlew :tooling:jetbrains:test

# Verify plugin compatibility
# (https://plugins.jetbrains.com/docs/intellij/verifying-plugin-compatibility.html)
./gradlew :tooling:jetbrains:verifyPlugin
```

### IntelliJ Platform Upgrades and Maintenance

This (sub)project is built from a stripped-down version of the [IntelliJ Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template), so when upgrading to integrate with newer versions of the IntelliJ Platform, we should consult that project for updates and documentation on the latest plugin integration/API guidance

### Running and Debugging

Once your [KSON](../../readme.md) project is loaded into IntelliJ as described in [**Developer setup**](../../readme.md#developer-setup), the `tooling:jetbrains:runIde` Gradle task can be run and debugged from the Gradle pane within IntelliJ
