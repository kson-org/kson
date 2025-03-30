# [Kson](https://kson.org) support for [JetBrains IDEs](https://www.jetbrains.com/products/#type=ide)

This [Gradle subproject](https://docs.gradle.org/current/userguide/multi_project_builds.html) implements [Kson](https://kson.org) support for [JetBrains IDEs](https://www.jetbrains.com/products/#type=ide)

### Development

This is a subproject of the main [Kson](../../readme.md) implementation, and so requires no special setup.

**NOTE: all subproject Gradle commands are run from the [project root](../..)**.

Some useful Gradle commands for this subproject:

```bash
# Run Intellij IDEA with the development plugin installed
./gradlew :tooling:jetbrains:runIde

# Run the plugin tests
./gradlew :tooling:jetbrains:test

# Verify plugin compatibility
# (https://plugins.jetbrains.com/docs/intellij/api-changes-list.html#verifying-compatibility)
./gradlew :tooling:jetbrains:runPluginVerifier
```

### Intellij Platform Upgrades and Maintenance

This (sub)project is built from a stripped-down version of the [Intellij Platform Plugin Template](https://github.com/JetBrains/intellij-platform-plugin-template), so when upgrading to integrate with newer versions of the Intellij Platform, we should consult that project for updates and documentation on the latest plugin integration/API guidance

### Running and Debugging

Once your [Kson](../../readme.md) project is loaded into intellij as described in [**Development setup**](https://github.com/kson-org/kson#development-setup), the `tooling:jetbrains:runIde` Gradle task can be run and debugged from the Gradle pane within Intellij
