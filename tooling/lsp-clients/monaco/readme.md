# @kson/monaco-editor

A Monaco editor with full KSON language support — completions, diagnostics,
hover, go-to-definition, formatting, and semantic highlighting — powered by
the same language server used by the VS Code extension.

This package is the ESM library distribution, for apps that already use
Monaco and a bundler.

## Install

```
npm install @kson/monaco-editor monaco-editor
```

## API

### Setup

Monaco needs a worker factory for its built-in features (tokenization, diff,
and the json/typescript/css/html language services).  This library does not
auto-install one, so configure `MonacoEnvironment.getWorker` before creating
any editor:

```ts
import editorWorker from 'monaco-editor/esm/vs/editor/editor.worker?worker';

self.MonacoEnvironment = {
    getWorker(_workerId: string, label: string): Worker {
        // Add label-specific branches (json, typescript, ...) here as you need them.
        return new editorWorker();
    },
};
```

The `label` argument is `'editor'` for the default worker that handles KSON
tokenization; Monaco passes other labels (`'json'`, `'typescript'`, …) when
the corresponding language services are used.

The `?worker` import above is Vite syntax.  For webpack/Rspack, use
`new Worker(new URL('monaco-editor/esm/vs/editor/editor.worker.js', import.meta.url))`.
For Next.js, load the editor with `dynamic(..., { ssr: false })` since
Monaco is browser-only.

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
| `enableBundledSchemas` | `boolean`                                              | Master toggle. Defaults to `true` when `bundledSchemas` is non-empty, `false` otherwise. Set explicitly only to override. |

Each `bundledSchemas` entry maps a file extension to a JSON Schema (draft-07)
string.  Documents whose URI ends in `.{fileExtension}` get that schema's
validation, completions, and hover.  The most specific extension wins (e.g.
`special.kson` beats `kson` for a `.special.kson` file).

#### `KsonEditor` (return value)

| Property             | Type                                 | Description                                    |
|----------------------|--------------------------------------|------------------------------------------------|
| `editor`             | `monaco.editor.IStandaloneCodeEditor`| The underlying Monaco editor instance.         |
| `bridge`             | `KsonLspBridge`                      | The LSP bridge (shared with all editors on the page). |
| `worker`             | `Worker`                             | The language server Web Worker.                |
| `serverCapabilities` | `ServerCapabilities`                 | Capabilities reported by the language server.  |
| `dispose()`          | `() => void`                         | Dispose this editor and release its share of the LSP worker (the worker is torn down when the last reference is released — see below). |

Only one language server runs per page.  Additional editors join it
automatically; the worker is torn down when the last editor is disposed.

#### Additional exports

| Export                  | Sub-path                          | Description                                          |
|-------------------------|-----------------------------------|------------------------------------------------------|
| `attachKsonLsp`         | `@kson/monaco-editor`             | Attach the LSP to an editor you already created (e.g. via `@monaco-editor/react`).  See [the React demo](https://github.com/kson-org/kson/tree/main/tooling/lsp-clients/demos/react) for a worked example. |
| `useKsonLsp`            | `@kson/monaco-editor/react`       | React hook over `attachKsonLsp` — handles the StrictMode-safe attach/detach lifecycle for you. |
| `registerKsonLanguage`  | `@kson/monaco-editor`             | Register the KSON language with Monaco (called automatically by `createKsonEditor`). |
| `KSON_LANGUAGE_ID`      | `@kson/monaco-editor`             | The language identifier string (`'kson'`).           |
| `KsonLspBridge`         | `@kson/monaco-editor`             | The LSP bridge class, for advanced use.              |
| `TabBar`                | `@kson/monaco-editor`             | The tab bar component used for multi-document navigation. |

### React (`@monaco-editor/react`)

For apps that already render Monaco via `@monaco-editor/react`, the
`useKsonLsp` hook from `@kson/monaco-editor/react` is a one-hook
integration.

```
npm install @kson/monaco-editor @monaco-editor/react monaco-editor react react-dom
```

Three pieces are required:

```tsx
import { useState } from 'react';
import * as monaco from 'monaco-editor';
import { Editor, loader } from '@monaco-editor/react';
import { useKsonLsp } from '@kson/monaco-editor/react';
import editorWorker from 'monaco-editor/esm/vs/editor/editor.worker?worker';

// 1. Reuse the bundled monaco — otherwise @monaco-editor/react fetches a
// second copy from a CDN and our LSP providers register against the wrong module.
loader.config({ monaco });

// 2. Wire the default-label worker — kson tokenization runs there.
self.MonacoEnvironment = {
    getWorker(_workerId: string, _label: string): Worker {
        return new editorWorker();
    },
};

function MyEditor() {
    const [editor, setEditor] = useState<monaco.editor.IStandaloneCodeEditor | null>(null);
    // 3. Attach the LSP — handles StrictMode and async attach/detach for you.
    useKsonLsp(editor, { lspOptions: { /* bundledSchemas, ... */ } });
    // defaultPath must end in .kson — the LSP keys schemas off the URI extension.
    return <Editor language="kson" defaultPath="config.kson" onMount={setEditor} />;
}
```

`useKsonLsp` calls `attachKsonLsp` under the hood, bakes in the
cancelled-flag dance that guards the unmount-before-attach race, and
disposes the LSP refcount on unmount.  Options are read once when the
editor first becomes non-null; re-mount the editor if you need to
change schemas.  See [`main.tsx`](https://github.com/kson-org/kson/blob/main/tooling/lsp-clients/demos/react/main.tsx) for a runnable example.

## Local development

Demos and dev commands for working in the source repo:

```bash
# Vanilla TS demo — imports createKsonEditor from source
./gradlew tooling:lsp-clients:npm_run_demoVanilla

# React demo — attachKsonLsp inside @monaco-editor/react
./gradlew tooling:lsp-clients:npm_run_demoReact

# Dev server (vite, with HMR)
./gradlew tooling:lsp-clients:npm_run_monaco

# Build library
./gradlew tooling:lsp-clients:npm_run_buildMonaco

# Run tests
./gradlew tooling:lsp-clients:npm_run_test
```
