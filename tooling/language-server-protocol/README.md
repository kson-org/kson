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

## Getting Started

### Prerequisites

* Node.js (v20.0.0 or higher)

### Installation

```bash
npm install
```

### Build

To compile the TypeScript source code, run:

```bash
npm run build
```

### Testing

To run the test suite:

```bash
npm test
```

[1] Visual Studio Code. (2025). *Language Server Extension Guide*. Retrieved
from https://code.visualstudio.com/api/language-extensions/language-server-extension-guide 