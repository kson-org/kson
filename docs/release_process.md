# Release Process Documentation

This file documents the current (and evolving) release process for KSON.

## Release Preparation Process:
When `main` is ready to have a release cut from it:
- Choose an appropriate `X.Y.Z` version number for the release by incrementing the latest tag ([see existing tags here](https://github.com/kson-org/kson/tags)) according to [Semantic Versioning](https://semver.org/) guidelines
- Create a branch `release/X.Y.Z` for this release
- On `main`, bump our pinned dependencies according to the process in [Regular Dependency Maintenance](dependency_maintenance.md) (this may be done in parallel to the release, or right after, but must not be neglected)

#### On the `main` branch:
- Search the codebase for `[[kson-version-num]]` and update all version numbers to be snapshot/development versions for the next version.  Generally this will bump to the next minor version after `X.Y.Z`, ie. `X.(Y+1).0`. Here is a hopefully complete checklist of the artifacts we version and publish (please improve if/when gaps are found!):
  * **Gradle-based projects** use centralized version from [KsonVersion.kt](../buildSrc/src/main/kotlin/org/kson/KsonVersion.kt):
    - Update `BASE_VERSION` to `X.(Y+1).0` - this applies to kson-lib, kson-tooling-lib, tooling/jetbrains, and tooling/cli
    - Snapshot versions use stable `{BASE_VERSION}-SNAPSHOT` for builds, and SHA-qualified `{BASE_VERSION}-{gitSha}-SNAPSHOT` for Maven publishing
  * [KSON Core internals](../build.gradle.kts): `x.(PREVIOUS_NUM+1)` (note this is the special incrementing internal version, update `internalBaseVersion` there)
  * lib-rust: [kson Cargo.toml](../lib-rust/kson/Cargo.toml), [kson-sys Cargo.toml](../lib-rust/kson-sys/Cargo.toml), [kson-sys build script](../lib-rust/kson-sys/build.rs): `X.(Y+1).0-dev`, [pixi.toml](../lib-rust/pixi.toml): `X.(Y+1).0-dev`
  * [lib-python](../lib-python/pyproject.toml): `X.(Y+1).0.dev0`
  * [tooling/lsp-clients](../tooling/lsp-clients/package.json): `X.(Y+1).0-dev.0`
  * [tooling/lsp-clients/vscode](../tooling/lsp-clients/vscode/package.json): `X.(Y+1).0-dev.0`
  * [tooling/lsp-clients/shared](../tooling/lsp-clients/shared/package.json): `X.(Y+1).0-dev.0`
  * [tooling/lsp-clients/monaco](../tooling/lsp-clients/monaco/package.json): `X.(Y+1).0-dev.0`
  * [tooling/language-server-protocol](../tooling/language-server-protocol/package.json): `X.(Y+1).0-dev.0`
- Run build and tests to ensure that `Cargo.lock` and `uv.lock` are updated and to ensure that tests pass:
    ```bash
    (cd buildSrc && ./gradlew check)
    ./gradlew build
    ```
- Manufacture a commit and pull request with these changes.

#### On the `release/X.Y.Z` branch:

- Search the codebase for `[[kson-version-num]]` to find and update all the development/snapshot versions to the new `X.Y.Z` version.  Here's a hopefully complete checklist of the artifacts we version and publish that should marked `[[kson-version-num]]`:
  * **Gradle-based projects** use centralized version from [KsonVersion.kt](../buildSrc/src/main/kotlin/org/kson/KsonVersion.kt):
    - Update `BASE_VERSION` to `X.Y.Z` - this applies to kson-lib, kson-tooling-lib, tooling/jetbrains, and tooling/cli
    - Build with `-Prelease=true` flag to produce release versions (without SNAPSHOT suffix):
      ```bash
      ./gradlew build -Prelease=true
      ```
  * [KSON Core internals](../build.gradle.kts) (**NOTE:** uses a different versioning scheme and will NOT be set to `X.Y.Z`.  See the comments there for details)
  * lib-rust, lib-python, tooling/lsp-clients: These require manual version updates (no `-Prelease` flag support yet):
    - [kson Cargo.toml](../lib-rust/kson/Cargo.toml), [kson-sys Cargo.toml](../lib-rust/kson-sys/Cargo.toml), [kson-sys build script](../lib-rust/kson-sys/build.rs)
    - [lib-python](../lib-python/pyproject.toml)
    - [tooling/lsp-clients](../tooling/lsp-clients/package.json)
    - [tooling/lsp-clients/vscode](../tooling/lsp-clients/vscode/package.json)
    - [tooling/lsp-clients/shared](../tooling/lsp-clients/shared/package.json)
    - [tooling/lsp-clients/monaco](../tooling/lsp-clients/monaco/package.json)
    - [tooling/language-server-protocol](../tooling/language-server-protocol/package.json)
- Commit and push the `release-X.Y.Z-prep` branch
- Run CircleCI across ALL supported platforms on the `release-X.Y.Z-prep` branch (only linux builds are run on every pull request)
  * Fix any platform specific issues found (hopefully this is rare... if it is common and painful, we may need to reconsider running cross-platform CI more often)
- Create tag `vX.Y.Z` if/when CircleCI is green for all platforms
- Publish the release according the Publishing process below

## Publishing Process:
- Prepare release notes based on the changes made in this release.  Sample Github comparison URL to see this release's changes: `https://github.com/kson-org/kson/compare/[tag of current version]...release/X.Y.Z`

- TODO flesh out this documentation

#### [kson-lib](../kson-lib) Publishing Process

The project uses the [Vanniktech Maven Publish plugin](https://github.com/vanniktech/gradle-maven-publish-plugin) to publish to Maven Central Portal. This process publishes both:
- `org.kson:kson` (public API from kson-lib)
- `org.kson:kson-internals` (internal implementation from root build that is deployed with kson-lib)

##### Prerequisites

- You will need a user account on https://central.sonatype.com/
- You will need a GPG key pair with the public key published to https://keyserver.ubuntu.com/

Ensure you have credentials for both of these in your `~/.gradle/gradle.properties` file:

```properties
mavenCentralUsername=<your-username>
mavenCentralPassword=<your-password>

# GPG signing credentials
signing.keyId=<your-key-id>
signing.password=<your-passphrase>
signing.secretKeyRingFile=<path-to-secring.gpg>
```

##### Publishing Steps

1. Ensure you've checked out **the tag to be released and that `git status` is clean**

2. Publish to Maven Central:
   ```bash
   ./gradlew publishAllPublicationsToMavenCentralRepository
   ```

3. Verify the publications are valid and ready to be published: https://central.sonatype.com/publishing/deployments

4. Manually release: we have `automaticRelease = false` as a final gate/protection, so once everything looks good at https://central.sonatype.com/publishing/deployments for this release, click Publish

#### [lib-rust](../lib-rust) Publishing Process

The Rust bindings are published to [crates.io](https://crates.io) as two crates:

- **`kson-sys`** — low-level bindings, generated by Krossover. Its build script links against the
  `kson-lib` native shared library.
- **`kson-rs`** — the idiomatic wrapper, which depends on `kson-sys` **by version**.

Unlike our other artifacts, these crates are not self-contained: `kson-sys`'s
[build script](../lib-rust/kson-sys/build.rs) needs a native `kson-lib` shared library at build
time. When a consumer sets neither `KSON_ROOT_SOURCE_DIR` nor `KSON_PREBUILT_BIN_DIR`, the build
script **downloads a prebuilt library** from the
[kson-binaries](https://github.com/kson-org/kson-binaries) repository:

```
https://github.com/kson-org/kson-binaries/releases/download/kson-lib-{KSON_LIB_VERSION}/kson-lib-shared-{arch}-{os}.tar.gz
```

where `KSON_LIB_VERSION` is the constant in `build.rs` (marked `[[kson-version-num]]`).

**This means a matching `kson-binaries` release must exist before the crates are published.**
There is a three-artifact ordering here that must be respected:

1. `kson-binaries` release `kson-lib-X.Y.Z`
2. `kson-sys` on crates.io
3. `kson-rs` on crates.io

> **Note:** our own `./gradlew check` will **not** catch a missing `kson-binaries` release.
> [lib-rust/build.gradle.kts](../lib-rust/build.gradle.kts) sets `KSON_PREBUILT_BIN_DIR` to the
> locally built native directory, so in-repo builds always use local binaries and never exercise
> the download path. Only external consumers hit it. Step 2 below verifies it explicitly.

##### Prerequisites

- A crates.io account, authenticated locally (`cargo login`), with publish rights on both
  `kson-rs` and `kson-sys`
- Write access to https://github.com/kson-org/kson-binaries
- The [`gh` CLI](https://cli.github.com/), authenticated
- Access to a machine of **each supported platform** to build its native library (or CI artifacts
  from the release build — see step 1)

##### Step 1: Cut the `kson-binaries` release

The native library must be built on each target platform; there is no cross-compilation. The
platforms we ship match the platforms CI builds:

| Asset name | Built on |
| --- | --- |
| `kson-lib-shared-amd64-linux.tar.gz` | `build-linux-amd64` |
| `kson-lib-shared-amd64-windows.tar.gz` | `build-windows-amd64` |
| `kson-lib-shared-arm64-macos.tar.gz` | `build-macos-arm64` |

`build.rs` maps the Rust target triple to these names: `aarch64` → `arm64`, `x86_64` → `amd64`,
and the OS is one of `linux`, `macos`, `windows`. A consumer on a combination we do not ship
(for example `arm64-linux`) will fail at the download step and must set `KSON_ROOT_SOURCE_DIR`
or `KSON_PREBUILT_BIN_DIR` themselves.

> Only `shared` assets are needed. Releases through 0.2.1 also carried `kson-lib-static-*`
> assets; since the GraalVM migration we no longer produce static libraries, and `build.rs`
> only ever requests `shared`.

1. Ensure you've checked out **the tag to be released and that `git status` is clean**

2. On each platform, build the native library and package the output directory:

   ```bash
   ./gradlew :kson-lib:buildWithGraalVmNativeImage

   # adjust the asset name for the platform you are on
   tar -czf kson-lib-shared-amd64-linux.tar.gz \
       -C kson-lib/build/kotlin/compileGraalVmNativeImage .
   ```

   Package the **whole directory**, not just the library. `build.rs` unpacks the archive and
   expects to find:

   - `jni_simplified.h` — bindgen generates the bindings from this header; the build fails
     without it
   - `graal_isolate.h` and `graal_isolate_dynamic.h`
   - the shared library itself: `libkson.so` (Linux), `libkson.dylib` (macOS), or `kson.dll`
     (Windows)

3. Confirm `KSON_LIB_VERSION` in [build.rs](../lib-rust/kson-sys/build.rs) matches the tag you are
   about to create. It is marked `[[kson-version-num]]` and should already have been updated
   during release preparation.

4. Create the release and upload all three assets:

   ```bash
   gh release create kson-lib-X.Y.Z \
       --repo kson-org/kson-binaries \
       --title kson-lib-X.Y.Z \
       kson-lib-shared-amd64-linux.tar.gz \
       kson-lib-shared-amd64-windows.tar.gz \
       kson-lib-shared-arm64-macos.tar.gz
   ```

##### Step 2: Verify the download path

Before publishing, confirm a clean consumer build works — this is the path our own CI never
exercises. With **both** env vars unset, so `build.rs` is forced to download:

```bash
env -u KSON_ROOT_SOURCE_DIR -u KSON_PREBUILT_BIN_DIR \
    cargo build --manifest-path lib-rust/kson/Cargo.toml
```

If this fails to download, the `kson-binaries` release is missing, misnamed, or does not match
`KSON_LIB_VERSION`. Fix it before continuing — once the crates are on crates.io they cannot be
changed, only yanked.

##### Step 3: Publish to crates.io

Order matters: `kson-rs` depends on `kson-sys` by version, so `kson-sys` must be on crates.io
first. `cargo publish` verifies by building, which runs `build.rs` and downloads from the release
created in step 1.

1. Dry-run both crates:

   ```bash
   cargo publish --dry-run --manifest-path lib-rust/kson-sys/Cargo.toml
   cargo publish --dry-run --manifest-path lib-rust/kson/Cargo.toml
   ```

2. Publish `kson-sys`:

   ```bash
   cargo publish --manifest-path lib-rust/kson-sys/Cargo.toml
   ```

3. Wait for the crates.io index to update (usually under a minute), then publish `kson-rs`:

   ```bash
   cargo publish --manifest-path lib-rust/kson/Cargo.toml
   ```

##### Step 4: Verify

- https://crates.io/crates/kson-sys
- https://crates.io/crates/kson-rs

Confirm the new version resolves from outside the repo:

```bash
cargo new /tmp/kson-smoke && cd /tmp/kson-smoke
cargo add kson-rs
cargo build
```

#### [kson-lib npm package](../kson-lib) Publishing Process

The KSON JavaScript/TypeScript library is published to npm as `@kson_org/kson` with support for both browser and Node.js environments.

##### Prerequisites

- You will need an npm account at https://www.npmjs.com/
- You will need publish access to the `@kson_org/kson` package

##### Publishing Steps

1. Ensure you've checked out **the tag to be released and that `git status` is clean**

2. Build the universal JavaScript package:
   ```bash
   ./gradlew buildUniversalJsPackage
   ```

   This builds a package for both the browser and Node.js and bundles it into `kson-lib/build/js-package`

3. Publish to npm:
   ```bash
   cd kson-lib/build/js-package
   npm login
   npm publish --access=public
   ```

4. Verify the package is available at: https://www.npmjs.com/package/@kson_org/kson
#### [lib-python](../lib-python) Publishing Process

The Python package is published to PyPI as `kson-lang` using platform-specific wheels built by CircleCI.

##### Prerequisites

- You will need a PyPI account at https://pypi.org/
- You will need the PyPI API token (stored in MM 1Password)

##### Publishing Steps

1. Ensure you've checked out **the tag to be released and that `git status` is clean**

2. Create the source distribution:
   ```bash
   ./gradlew createDist
   ```

3. Download the pre-built wheels from the CircleCI build for this tag:
   - Download the wheel artifacts from CircleCI (they will download as `.zip` files)
   - Copy all wheels into the `lib-python/dist/` directory
   - Change the file extensions from `.zip` to `.whl`

4. Upload to PyPI using `twine`:
   ```bash
   cd lib-python
   ./uvw run python -m twine upload --repository pypi dist/* --verbose
   ```
   - Use the API Token when prompted

5. Verify the package is available at: https://pypi.org/project/kson-lang/
#### [tooling/cli](../tooling/cli) Publishing Process
* todo doc process
#### [tooling/lsp-clients](../tooling/lsp-clients) Publishing Process

The KSON language support includes VSCode extensions published to both the Visual Studio Code Marketplace and Open VSX Registry.

##### Prerequisites

- GitHub account with access to publish to the marketplaces
- Access to https://marketplace.visualstudio.com
- Access to https://open-vsx.org

##### Publishing Steps

1. Ensure you've checked out **the tag to be released and that `git status` is clean**

2. Build the VSCode extension package:
   ```bash
   ./gradlew clean && ./gradlew npm_run_buildVSCode
   ```

   This creates a VSIX package at `tooling/lsp-clients/vscode/dist/vscode-kson-plugin.vsix`

3. Publish to VS Code Marketplace:
   - Login to https://marketplace.visualstudio.com with GitHub
   - Navigate to the KSON extension
   - Update/upload the extension with the built VSIX file

4. Publish to Open VSX Registry:
   - Login to https://open-vsx.org/user-settings/extensions with GitHub
   - Navigate to the KSON extension
   - Update/upload the extension with the built VSIX file

5. Verify the extensions are available at:
   - VS Code Marketplace: https://marketplace.visualstudio.com/items?itemName=kson.kson
   - Open VSX: https://open-vsx.org/extension/kson/kson
#### [tooling/jetbrains](../tooling/jetbrains) Publishing Process

Note: it is possible to automate this process us some Gradle tasks provided by the [IntelliJ Platform Gradle Plugin](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html), if/when this manual process become onerous.

1. Ensure you have **the tag you wish to release checked out and that your Git status is clean**.

2. Build the plugin distribution:

    ```bash
    ./gradlew :tooling:jetbrains:buildPlugin
    ```

   This creates a ZIP archive ready for deployment in `tooling/jetbrains/build/distributions/KSON-[version].zip`

3. Manually upload to JetBrains Marketplace:
  - Go to https://plugins.jetbrains.com/plugin/28510-kson-language and ensure you are logged in as a "Developer" of of the plugin.
  - Upload the ZIP file from the distributions folder
