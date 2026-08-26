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

Three artifacts, published **in this order**:

1. `kson-binaries` release `kson-lib-X.Y.Z` — the prebuilt native libraries
2. `kson-sys` on crates.io
3. `kson-rs` on crates.io

Unless `KSON_ROOT_SOURCE_DIR` or `KSON_PREBUILT_BIN_DIR` is set, `kson-sys`'s
[build script](../lib-rust/kson-sys/build.rs) downloads its native library from:

```
https://github.com/kson-org/kson-binaries/releases/download/kson-lib-{KSON_LIB_VERSION}/kson-lib-shared-{arch}-{os}.tar.gz
```

`cargo publish` verifies by building, so step 1 must be complete before step 3.

##### Prerequisites

- **The tag to be released checked out, with `git status` clean.** Steps 1, 2 and 3 all read
  versions from the working tree — `KSON_LIB_VERSION` and the crate versions — so running them
  from `main` or a feature branch publishes or verifies the wrong version.
- A crates.io account, authenticated locally (`cargo login`), with publish rights on both
  `kson-rs` and `kson-sys`
- Write access to https://github.com/kson-org/kson-binaries
- The [`gh` CLI](https://cli.github.com/), authenticated
- A green `build-python-and-test` workflow on the release branch

##### Step 1: Cut the `kson-binaries` release

The native libraries are built by the release CI run and ship inside the Python wheels.
Download those wheels and repackage their native payload.

| CircleCI job | Artifact | Wheel platform tag | Release asset | Library |
| --- | --- | --- | --- | --- |
| `test-python-sdist-linux-amd64` | `python-linux-amd64` | `manylinux_2_34_x86_64` | `kson-lib-shared-amd64-linux.tar.gz` | `libkson.so` |
| `test-python-sdist-macos` | `python-macos` | `macosx_11_0_arm64` | `kson-lib-shared-arm64-macos.tar.gz` | `libkson.dylib` |
| `test-python-sdist-windows` | `python-windows` | `win_amd64` | `kson-lib-shared-amd64-windows.tar.gz` | `kson.dll` |

> Job names have changed between releases; the artifact destinations have not. Confirm
> against the release branch's `.circleci/config.kson`.

Only `shared` assets are needed. `build.rs` derives the asset name from the Rust target triple
(`aarch64` → `arm64`, `x86_64` → `amd64`; OS is `linux`, `macos`, or `windows`). Consumers on a
combination we do not ship must set `KSON_ROOT_SOURCE_DIR` or `KSON_PREBUILT_BIN_DIR` themselves.

1. Confirm `KSON_LIB_VERSION` in [build.rs](../lib-rust/kson-sys/build.rs) matches the tag you
   are about to create. **If they disagree, every downstream `cargo build` 404s.**

2. Download the three wheels from each job's **Artifacts** tab in the CircleCI UI. CircleCI
   serves them as zips, so they save with a `.zip` extension:

   ```bash
   mkdir -p /tmp/kson-binaries && cd /tmp/kson-binaries
   # save the three wheels here, e.g.
   #   kson_lang-X.Y.Z-cp310-abi3-manylinux_2_34_x86_64.zip
   #   kson_lang-X.Y.Z-cp310-abi3-macosx_11_0_arm64.zip
   #   kson_lang-X.Y.Z-cp310-abi3-win_amd64.zip
   ```

   No rename is needed here — the script below accepts `.zip` or `.whl`. (Publishing to PyPI
   *does* require renaming them back to `.whl`; see the lib-python process above.)

3. Repackage each wheel's native payload. A wheel is a zip, so this needs no Python:

   ```bash
   cd /tmp/kson-binaries
   for f in $(find . -maxdepth 1 -type f \( -name '*.whl' -o -name '*.zip' \) | sort); do
     case "$f" in
       *manylinux*x86_64*) asset=kson-lib-shared-amd64-linux;   lib=libkson.so ;;
       *macosx*arm64*)     asset=kson-lib-shared-arm64-macos;   lib=libkson.dylib ;;
       *win_amd64*)        asset=kson-lib-shared-amd64-windows; lib=kson.dll ;;
       *) echo "skipping unrecognized artifact: $f"; continue ;;
     esac
     mkdir -p "stage/$asset"
     unzip -j -q -o "$f" "kson/jni_simplified.h" "kson/$lib" -d "stage/$asset"
     tar -czf "$asset.tar.gz" -C "stage/$asset" .
     echo "$asset.tar.gz: $(tar tzf "$asset.tar.gz" | tr '\n' ' ')"
   done
   ```

   Each archive is flat, rooted at `./`, and contains exactly two files: `jni_simplified.h` and
   the platform's shared library. `jni_simplified.h` is **platform-specific** — take each one
   from its own wheel.

4. Sanity-check a tarball on your own platform before uploading:

   ```bash
   mkdir -p /tmp/kson-verify
   tar -xzf /tmp/kson-binaries/kson-lib-shared-arm64-macos.tar.gz -C /tmp/kson-verify
   env -u KSON_ROOT_SOURCE_DIR KSON_PREBUILT_BIN_DIR=/tmp/kson-verify \
       cargo build --manifest-path lib-rust/kson/Cargo.toml
   ```

5. Create the release and upload all three assets:

   ```bash
   cd /tmp/kson-binaries
   gh release create kson-lib-X.Y.Z \
       --repo kson-org/kson-binaries \
       --title kson-lib-X.Y.Z \
       kson-lib-shared-amd64-linux.tar.gz \
       kson-lib-shared-amd64-windows.tar.gz \
       kson-lib-shared-arm64-macos.tar.gz
   ```

##### Step 2: Verify the download path

No CI job covers this path. With both env vars unset:

```bash
env -u KSON_ROOT_SOURCE_DIR -u KSON_PREBUILT_BIN_DIR \
    cargo build --manifest-path lib-rust/kson/Cargo.toml
```

A failure here means the `kson-binaries` release is missing, misnamed, or disagrees with
`KSON_LIB_VERSION`. Fix it before continuing: published crates can only be yanked, never
changed.

##### Step 3: Publish to crates.io

`kson-sys` must go first — `kson-rs` depends on it by version.

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
