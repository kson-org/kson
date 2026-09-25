# Regular Dependency Maintenance

KSON's multi-platform build and test architecture depends on a number of external dependencies, _all_ of which must be pinned to deterministic versions for build stability and reproducibility.

These pinned dependencies must be periodically bumped and the resulting build verified for the new pins. This document describes that process.

## When to perform this maintenance

We perform this maintenance at the opening of each development cycle, right after a `release/X.Y.Z` branch is cut and `main` moves on to the next snapshot version (see [the release process](release_process.md)).

We do it at the opening of a cycle because version bumps break things, sometimes in ways that are not immediately obvious. Bumping early gives us a whole cycle of development and internal usage to surface any issues.

## 1. Bump the pins meant to track upstream

These are the dependencies we will keep relatively current. We'd like to upgrade and build each dependency in its own commit so a later bisect has something precise/informative to land on.

- **Gradle**
  - pinned in: [`gradle-wrapper.properties`](../gradle/wrapper/gradle-wrapper.properties)
  - upstream: [gradle.org/releases](https://gradle.org/releases/)
  - bump with: `./gradlew wrapper --gradle-version <gradle version>`
- **The JDK the whole build runs on**
  - pinned in: [`jdk.properties`](../jdk.properties), which documents the follow-up its edits require
  - upstream: [GraalVM CE releases](https://github.com/graalvm/graalvm-ce-builds/releases)
- **Kotlin**
  - pinned in: [`settings.gradle.kts`](../settings.gradle.kts) and [`buildSrc/build.gradle.kts`](../buildSrc/build.gradle.kts), which both declare it and must agree
  - upstream: [Kotlin releases](https://kotlinlang.org/docs/releases.html)
- **Gradle plugins** (detekt, Dokka, Vanniktech publish, JVM wrapper)
  - pinned in: [`build.gradle.kts`](../build.gradle.kts) and [`buildSrc/build.gradle.kts`](../buildSrc/build.gradle.kts)
  - upstream: [Gradle Plugin Portal](https://plugins.gradle.org)
- **The IntelliJ Platform Gradle Plugin**
  - pinned in: [`tooling/jetbrains/build.gradle.kts`](../tooling/jetbrains/build.gradle.kts)
  - upstream: [its Plugin Portal listing](https://plugins.gradle.org/plugin/org.jetbrains.intellij.platform)
- **Pixi itself**
  - pinned in: `PINNED_PIXI_VERSION` in [`PixiWrapperTask.kt`](../buildSrc/src/main/kotlin/PixiWrapperTask.kt)
  - upstream: [Pixi releases](https://github.com/prefix-dev/pixi/releases)
  - note: the generated `pixiw` wrappers auto-install this version
- **The toolchains Pixi installs** (Rust, Node, pnpm, Python, libclang, MSVC)
  - pinned in: the `pixi.toml` of [`kson-lib`](../kson-lib/pixi.toml), [`lib-rust`](../lib-rust/pixi.toml), [`tooling/cli`](../tooling/cli/pixi.toml), [`tooling/lsp-clients`](../tooling/lsp-clients/pixi.toml) and [`tooling/language-server-protocol`](../tooling/language-server-protocol/pixi.toml)
  - upstream: [conda-forge](https://prefix.dev/channels/conda-forge)
  - bump with: edit the manifest, then re-solve with `pixi lock`
- **npm dependencies**
  - pinned in: the `package.json` files under [`tooling/lsp-clients`](../tooling/lsp-clients) and [`tooling/language-server-protocol`](../tooling/language-server-protocol)
  - check with: `pnpm outdated -r` from each workspace root
- **Cargo dependencies**
  - pinned in: [`lib-rust/kson/Cargo.toml`](../lib-rust/kson/Cargo.toml) and [`lib-rust/kson-sys/Cargo.toml`](../lib-rust/kson-sys/Cargo.toml)
  - check with: `cargo update --dry-run` in each
- **Python dependencies**
  - pinned in: [`lib-python/pyproject.toml`](../lib-python/pyproject.toml)
  - check with: `./uvw lock --upgrade --dry-run` from [`lib-python`](../lib-python)
- **The VS Code build our extension tests drive**
  - pinned in: [`vscodeTestBuild.ts`](../tooling/lsp-clients/vscode/test/vscodeTestBuild.ts)
  - check with: the `curl` documented in that file
- **The upstream conformance suites we generate tests from**
  - pinned in: `jsonTestSuiteSHA` and `schemaTestSuiteSHA` in [`GenerateJsonTestSuiteTask.kt`](../buildSrc/src/main/kotlin/GenerateJsonTestSuiteTask.kt)
  - check with: `git ls-remote <suite url> HEAD`, for the urls in [`TestSuiteGitCheckout.kt`](../buildSrc/src/main/kotlin/org/kson/jsonsuite/TestSuiteGitCheckout.kt)
- **CircleCI orbs and macOS images**
  - pinned in: [`.circleci/config.kson`](../.circleci/config.kson)
  - upstream: [orb registry](https://circleci.com/developer/orbs) and [macOS image list](https://circleci.com/docs/using-macos/)

## 2. Leave the pins that are deliberately behind

Some of our dependencies are deliberately not current: they represent our backwards compatibility commitments to the platforms they target, so they should only ever be bumped very intentionally, as part of a product decision to drop support for an old version.

- **The oldest IntelliJ Platform the plugin supports**
  - declared in: `pluginSinceBuild`, `platformVersion` and `javaVersion` in [`tooling/jetbrains/gradle.properties`](../tooling/jetbrains/gradle.properties)
  - raising it: drops support for older IDEs
  - note: all three move together, since the platform version dictates the minimum JDK
- **The oldest JVM that can consume `org.kson:kson`**
  - declared in: `javaVersion` in [`build.gradle.kts`](../build.gradle.kts)
  - raising it: breaks consumers
  - note: this is the bytecode target we publish, deliberately well below the JDK we build with
- **The oldest Python that can install `kson-lang`**
  - declared in: `requires-python` in [`lib-python/pyproject.toml`](../lib-python/pyproject.toml), matched by the `cimg/python` image in [`.circleci/config.kson`](../.circleci/config.kson)
  - note: CI builds and tests the sdist on that floor on purpose. Move both together, or the guarantee stops being tested

TODO One pin remains unclassified: `test-python-sdist-macos` runs on a much older `xcode` image than `build-macos-arm64` does. Work out whether that is a deliberate oldest-supported-macOS floor or an oversight, then move it into whichever list above it belongs in.

## 3. Sweep for new/missing unpinned dependencies

Audit for new or missed unpinned/floating dependencies that are not yet captured in this doc, and add them where appropriate. We would ideally catch these before they get in, but historically this type of thing has a way of creeping into a project.

Known floats, not yet resolved:

- `windows-server-2022-gui:current` and `cimg/node:lts-browsers` in [`.circleci/config.kson`](../.circleci/config.kson) both track a CircleCI tag that moves under us. The latter is the image `build-linux-amd64` runs on, so it is in the path of every pull request.
- The orbs in that same file are declared to a partial version, `circleci/browser-tools@1.1` and `circleci/windows@5.0`. CircleCI resolves a partial version to the newest release matching it, so each build may run orb code no commit here selected.
- `nodejs = ">=18"` in [`kson-lib/pixi.toml`](../kson-lib/pixi.toml) states a floor where the other Pixi manifests pin a series, so re-solving that lock can move Node without anyone deciding to.
