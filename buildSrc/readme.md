# Kson `buildSrc/` project

This is the Gradle `buildSrc/` project supporting the [Kson project](../readme.md).  See [here for a concise description of `buildSrc/`](https://stackoverflow.com/a/13875350), and see [here for the official docs](https://docs.gradle.org/current/userguide/organizing_gradle_projects.html#sec:build_sources)

This project is completely independent of the parent project&mdash;because it's _part_ of the parent's build&mdash;so it is set up to develop as a standalone project, with a full [`build.gradle.kts`](build.gradle.kts) defined and a fully [gradle wrapper](gradle) installed.

### Development setup

This `buildSrc/` directory is the root of stand-alone Gradle project.  `cd` into it and develop as you would any Gradle project.

* Run `./gradlew check` inside this `buildSrc/` directory to validate everything builds and runs correctly
* **IntelliJ setup:** open the [`buildSrc/build.gradle.kts`](build.gradle.kts) file "as a Project"
