# Release Process Documentation

This file documents the current (and evolving) release process for KSON.

## Release Preparation Process:
When `main` is ready to have a release cut from it:
- Choose an appropriate `X.Y.Z` version number for the release by incrementing the latest tag ([see existing tags here](https://github.com/kson-org/kson/tags)) according to [Semantic Versioning](https://semver.org/) guidelines
- Create a branch `release/X.Y.Z` for this release

#### On the `main` branch:
- Search the codebase for `[[kson-version-num]]` again and update all version numbers to be snapshot/development versions.  Generally this will bump to the next minor version after `X.Y.Z`, ie. `X.(Y+1).0`. Here is a hopefully complete checklist of the artifacts we version and publish:
  * [kson-lib](../kson-lib/build.gradle.kts): `X.(Y+1).0-SNAPSHOT`
  * [KSON Core internals](../build.gradle.kts): `x.(PREVIOUS_NUM+1)-SNAPSHOT` (note this is the special incrementing internal version)
  * [lib-rust](../lib-rust/pixi.toml): `X.(Y+1).0-dev`
  * [lib-python](../lib-python/pyproject.toml): `X.(Y+1).0.dev0`
  * [tooling/cli](../tooling/cli/build.gradle.kts): TODO we do not currently embed a version in the CLI
  * [tooling/lsp-clients](../tooling/lsp-clients/package.json): `X.(Y+1).0-dev.0`
  * [tooling/jetbrains](../tooling/jetbrains/gradle.properties): `X.(Y+1).0-SNAPSHOT`

#### On the `release/X.Y.Z` branch:

- Search the codebase for `[[kson-version-num]]` to find and update all the development/snapshot versions to the new `X.Y.Z` version.  Here's a hopefully complete checklist of the artifacts we version and publish that should marked `[[kson-version-num]]`:
  * [kson-lib](../kson-lib/build.gradle.kts)
  * [KSON Core internals](../build.gradle.kts) (**NOTE:** the root [`build.gradle.kts`](../build.gradle.kts) uses a different versioning scheme and will NOT be set to `X.Y.Z`.  See the comments there for details)
  * [lib-rust](../lib-rust/pixi.toml)
  * [lib-python](../lib-python/pyproject.toml)
  * [tooling/cli](../tooling/cli/build.gradle.kts): TODO we do not currently embed a version in the CLI
  * [tooling/lsp-clients](../tooling/lsp-clients/package.json)
  * [tooling/jetbrains](../tooling/jetbrains/gradle.properties)
- Commit and push the `release-X.Y.Z-prep` branch
- Run CircleCI across ALL supported platforms on the `release-X.Y.Z-prep` branch (only linux builds are run on every pull request)
  * Fix any platform specific issues found (hopefully this is rare... if it is common and painful, we may need to reconsider running cross-platform CI more often)
- Create tag `vX.Y.Z` if/when CircleCI is green for all platforms
- Publish the release according the Publishing process below

## Publishing Process:
- Prepare release notes based on the changes made in this release.  Sample Github comparison URL to see this release's changes: `https://github.com/kson-org/kson/compare/[tag of current version]...release/X.Y.Z`

- TODO flesh out this documentation

#### [kson-lib](../kson-lib/) Publishing Process
* todo doc process
#### [lib-rust](../lib-rust/) Publishing Process
* todo doc process

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
* todo doc process