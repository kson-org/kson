# Kson Language Server

A Language Server Protocol (LSP) implementation for Kson, written in TypeScript. We support the
[Language Server Protocol (LSP)](https://microsoft.github.io/language-server-protocol/), because it is a standard that allows programming language tooling to be decoupled
from the code editor.

By implementing a language server, this project provides Kson language support that can be used by any LSP-compatible
editor, such as Visual Studio Code, Neovim, or Sublime Text. This approach avoids the need to write a new extension for
each editor and ensures that features are implemented in one place, improving performance and maintainability [1].

## Current Features

* **Real-time Diagnostics:** Identifies syntax errors as you type.
* **Document Formatting:** Automatically formats Kson files.
* **Semantic Highlighting:** Provides rich, context-aware syntax highlighting.

## Local development

Use gradlew (from the repository root) to build and test this LSP implementation. Gradle uses pixi-managed Node.js 24
and pnpm. To run pnpm directly, use `./pixiw run pnpm` in this directory.

### Build

Run `npm_install` any time package.json is updated to regenerate the lock files.

```bash
./gradlew tooling:language-server-protocol:npm_install
```

Note: this also builds the `kson` and `kson-tooling` Kotlin/JS libraries that the project depends on.

To compile the TypeScript source code, run:
```bash
./gradlew tooling:language-server-protocol:npm_run_compile
```

### Testing

To run the test suite:

```bash
./gradlew tooling:language-server-protocol:npm_run_test
```

[1] Visual Studio Code. (2025). *Language Server Extension Guide*. Retrieved
from https://code.visualstudio.com/api/language-extensions/language-server-extension-guide 