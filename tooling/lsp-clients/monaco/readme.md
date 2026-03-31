# @kson/monaco-editor

A Monaco editor with full KSON language support — completions, diagnostics,
hover, go-to-definition, formatting, and semantic highlighting — powered by
the same language server used by the VS Code extension.

Two distribution modes:

- **Library** (`dist/kson-monaco.js`) — ES module for apps that already use
  Monaco and a bundler.
- **Iframe** (`dist-iframe/`) — self-contained, drop-in embed for any page.
  No npm or bundler required.

## Demos

The fastest way to see each mode in action. Both include interactive "Try it"
buttons for every API method.

```bash
# Library demo — imports createKsonEditor from source
./gradlew tooling:lsp-clients:npm_run_demoLibrary

# Iframe demo — uses the pre-built iframe assets via a script tag
./gradlew tooling:lsp-clients:npm_run_buildMonacoIframe
./gradlew tooling:lsp-clients:npm_run_demoIframe
```

## Library API

```
npm install @kson/monaco-editor monaco-editor
```

### `createKsonEditor(container, options?): Promise<KsonEditor>`

Creates a KSON editor inside `container`.  The first call starts a language
server in a Web Worker.

#### `KsonEditorOptions`

| Option          | Type                                            | Description                                                       |
|-----------------|-------------------------------------------------|-------------------------------------------------------------------|
| `value`         | `string`                                        | Initial editor content.                                           |
| `uri`           | `string`                                        | Document URI for LSP identification. Default `'inmemory://kson/document.kson'`. |
| `editorOptions` | `monaco.editor.IStandaloneEditorConstructionOptions` | Forwarded to `monaco.editor.create()`.                       |
| `lspOptions`    | `LspOptions` (see below)                        | Options forwarded to the language server at initialization.       |
| `shared`        | `Pick<KsonEditor, 'bridge' \| 'worker' \| 'serverCapabilities'>` | Reuse the language server from another editor. When set, `lspOptions` is ignored. |

#### `LspOptions`

| Option               | Type                                                     | Description                                   |
|----------------------|----------------------------------------------------------|-----------------------------------------------|
| `bundledSchemas`     | `Array<{ fileExtension: string; schemaContent: string }>` | Maps file extensions to JSON Schema strings.  |
| `bundledMetaSchemas` | `Array<{ schemaId: string; name: string; schemaContent: string }>` | Custom meta-schemas.                |
| `enableBundledSchemas` | `boolean`                                              | Enable the bundled schemas.                   |

Each `bundledSchemas` entry maps a file extension to a JSON Schema (draft-07)
string.  Documents whose URI ends in `.{fileExtension}` get that schema's
validation, completions, and hover.  The most specific extension wins (e.g.
`orchestra.kson` beats `kson` for a `.orchestra.kson` file).

#### `KsonEditor` (return value)

| Property             | Type                                 | Description                                    |
|----------------------|--------------------------------------|------------------------------------------------|
| `editor`             | `monaco.editor.IStandaloneCodeEditor`| The underlying Monaco editor instance.         |
| `bridge`             | `KsonLspBridge`                      | The LSP bridge (pass to `shared` to reuse).    |
| `worker`             | `Worker`                             | The language server Web Worker.                |
| `serverCapabilities` | `ServerCapabilities`                 | Capabilities reported by the language server.  |
| `dispose()`          | `() => void`                         | Dispose the editor, bridge, and worker.        |

Only one language server can be active.  Pass the first editor as `shared`
to connect additional editors to the same server.

#### Additional exports

| Export                  | Description                                          |
|-------------------------|------------------------------------------------------|
| `registerKsonLanguage`  | Register the KSON language with Monaco (called automatically by `createKsonEditor`). |
| `KSON_LANGUAGE_ID`      | The language identifier string (`'kson'`).           |
| `KsonLspBridge`         | The LSP bridge class, for advanced use.              |
| `TabBar`                | The tab bar component used for multi-document navigation. |

## Iframe API

Copy `dist-iframe/` to your static assets.  It contains two files:

- `kson-editor.html` — the self-contained editor page loaded inside an iframe.
- `kson-editor.js` — the parent-side script (`<script src="kson-editor.js">`).

### `KsonEditor.create(container, options?): Promise<KsonEditorClientHandle>`

Creates an iframe-hosted KSON editor inside `container`.

#### `KsonEditorClientOptions`

| Option          | Type                                                  | Description                                           |
|-----------------|-------------------------------------------------------|-------------------------------------------------------|
| `value`         | `string`                                              | Initial KSON content.                                 |
| `uri`           | `string`                                              | Document URI. Default `'inmemory://kson/document.kson'`. |
| `schema`        | `{ fileExtension: string; schemaContent: string }`    | JSON Schema for validation and completions.           |
| `editorOptions` | `Record<string, unknown>`                             | Monaco editor options forwarded to the iframe.        |
| `onChange`      | `(value: string) => void`                             | Called whenever the editor content changes.            |
| `baseUrl`       | `string`                                              | Base URL where `kson-editor.html` lives. Auto-detected from the script's `src` attribute. |

#### `KsonEditorClientHandle` (return value)

| Method               | Description                                               |
|----------------------|-----------------------------------------------------------|
| `getValue()`         | Returns the current content (synchronous, no round-trip). |
| `setValue(value)`    | Replace the editor content.                               |
| `dispose()`          | Remove the iframe and clean up listeners.                 |

## Development

```bash
# Dev server (vite, with HMR)
./gradlew tooling:lsp-clients:npm_run_monaco

# Build library
./gradlew tooling:lsp-clients:npm_run_buildMonaco

# Build iframe
./gradlew tooling:lsp-clients:npm_run_buildMonacoIframe

# Run tests
./gradlew tooling:lsp-clients:npm_run_test
```
