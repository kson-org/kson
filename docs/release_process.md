# Release Process Documentation

This file documents the current (and evolving) release process for KSON.

## Release Preparation Process:
When `main` is ready to have a release cut from it:
- Choose an appropriate `X.Y.Z` version number for the release by incrementing the latest tag ([see existing tags here](https://github.com/kson-org/kson/tags)) according to [Semantic Versioning](https://semver.org/) guidelines
- Create a branch `release/X.Y.Z` for this release

#### On the `main` branch:
- Search the codebase for `[[kson-version-num]]` again and update all version numbers to be snapshot/development versions.  A hopefully complete checklist of the artifacts we version and publish:
  * [kson-lib](../kson-lib/build.gradle.kts): `X.Y.(Z + 1)-SNAPSHOT`
  * [KSON Core internals](../build.gradle.kts): `x.(V + 1)-SNAPSHOT` (note this is the special incrementing internal version)
  * [lib-rust](../lib-rust/pixi.toml): `X.Y.(Z + 1)-dev`
  * [lib-python](../lib-python/pyproject.toml): `X.Y.(Z + 1).dev0`
  * [tooling/cli](../tooling/cli/build.gradle.kts): TODO we do not currently embed a version in the CLI
  * [tooling/lsp-clients](../tooling/lsp-clients/package.json): `X.Y.(Z + 1)-dev.0`
  * [tooling/jetbrains](../tooling/jetbrains/gradle.properties): `X.Y.(Z + 1)-SNAPSHOT`

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
#### [lib-python](../lib-python/) Publishing Process
* todo doc process
#### [tooling/cli](../tooling/cli/) Publishing Process
* todo doc process
#### [tooling/lsp-clients](../tooling/lsp-clients) Publishing Process
* todo doc process
#### [tooling/jetbrains](../tooling/jetbrains/) Publishing Process
* todo doc process
