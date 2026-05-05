# @kson/monaco-editor

A Monaco editor with full KSON language support — completions, diagnostics,
hover, go-to-definition, formatting, and semantic highlighting — powered by
the same language server used by the VS Code extension.

Two distribution modes:

- **Library** (`dist/index.js`, with `dist/react/index.js` for the React hook) —
  ES modules for apps that already use Monaco and a bundler.
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

# React demo — attachKsonLsp inside @monaco-editor/react
./gradlew tooling:lsp-clients:npm_run_demoReact
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
| `lspOptions`    | `LspOptions` (see below)                        | Options forwarded to the language server at initialization. Honored on the first editor; ignored on subsequent ones (they join the running server). |

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
| `bridge`             | `KsonLspBridge`                      | The LSP bridge (shared with all editors on the page). |
| `worker`             | `Worker`                             | The language server Web Worker.                |
| `serverCapabilities` | `ServerCapabilities`                 | Capabilities reported by the language server.  |
| `dispose()`          | `() => void`                         | Dispose the editor, bridge, and worker.        |

Only one language server runs per page.  Additional editors join it
automatically; the worker is torn down when the last editor is disposed.

#### Additional exports

| Export                  | Sub-path                          | Description                                          |
|-------------------------|-----------------------------------|------------------------------------------------------|
| `attachKsonLsp`         | `@kson/monaco-editor`             | Attach the LSP to an editor you already created (e.g. via `@monaco-editor/react`).  See `demos/react/` for a worked example. |
| `useKsonLsp`            | `@kson/monaco-editor/react`       | React hook over `attachKsonLsp` — handles the StrictMode-safe attach/detach lifecycle for you. |
| `registerKsonLanguage`  | `@kson/monaco-editor`             | Register the KSON language with Monaco (called automatically by `createKsonEditor`). |
| `KSON_LANGUAGE_ID`      | `@kson/monaco-editor`             | The language identifier string (`'kson'`).           |
| `KsonLspBridge`         | `@kson/monaco-editor`             | The LSP bridge class, for advanced use.              |
| `TabBar`                | `@kson/monaco-editor`             | The tab bar component used for multi-document navigation. |

### React (`@monaco-editor/react`)

For apps that already render Monaco via `@monaco-editor/react`, the
`useKsonLsp` hook from `@kson/monaco-editor/react` is a one-line
drop-in.  Three pieces are required:

```tsx
import * as monaco from 'monaco-editor';
import { Editor, loader } from '@monaco-editor/react';
import { useKsonLsp } from '@kson/monaco-editor/react';
import editorWorker from 'monaco-editor/esm/vs/editor/editor.worker?worker';

// 1. Reuse the bundled monaco — otherwise @monaco-editor/react fetches a
// second copy from a CDN and our LSP providers register against the wrong module.
loader.config({ monaco });

// 2. Wire the default-label worker — kson tokenization runs there.
self.MonacoEnvironment = { getWorker: () => new editorWorker() };

function MyEditor() {
    const [editor, setEditor] = useState<monaco.editor.IStandaloneCodeEditor | null>(null);
    // 3. Attach the LSP — handles StrictMode and async attach/detach for you.
    useKsonLsp(editor, { lspOptions: { /* bundledSchemas, ... */ } });
    return <Editor language="kson" onMount={setEditor} />;
}
```

`useKsonLsp` calls `attachKsonLsp` under the hood, bakes in the
cancelled-flag dance that guards the unmount-before-attach race, and
disposes the LSP refcount on unmount.  Options are read once when the
editor first becomes non-null; re-mount the editor if you need to
change schemas.  See `demos/react/main.tsx` for a runnable example
(`./gradlew tooling:lsp-clients:npm_run_demoReact`).

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
