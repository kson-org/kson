# React Demo

`@monaco-editor/react` integration: a single `useKsonLsp` hook attaches the
KSON language server to editors created by the React component.
See [`../readme.md`](../readme.md) for the full set of integration examples.

```bash
./gradlew tooling:lsp-clients:npm_run_demoReact
```

Serves on [http://localhost:5176](http://localhost:5176).

## `useKsonLsp` API

```ts
import { useKsonLsp } from '@kson/monaco-editor/react';

useKsonLsp(editor, options?);
```

| Param     | Type                                              | Notes                                                   |
| --------- | ------------------------------------------------- | ------------------------------------------------------- |
| `editor`  | `monaco.editor.IStandaloneCodeEditor \| null`     | Capture from `<Editor onMount>` via `useState`.         |
| `options` | `AttachKsonLspOptions` (optional)                 | Read once on attach; later changes are ignored.         |

```ts
interface AttachKsonLspOptions {
  lspOptions?: {
    bundledSchemas?: Array<{ fileExtension: string; schemaContent: string }>;
    bundledMetaSchemas?: Array<{ schemaId: string; name: string; schemaContent: string }>;
    enableBundledSchemas?: boolean;
  };
}
```

`lspOptions` is forwarded to the language server as `initializationOptions`
on the **first** acquire — it's ignored on subsequent calls because the
shared worker is already initialized. Re-mount the editor if you need to
change schemas.

## Behavior

- Registers the KSON language and Monaco providers (completions, diagnostics,
  hover, semantic tokens, etc.) on the first call.
- Refcounted shared worker: every editor calling the hook bumps a count;
  the worker is torn down only when the last attachment is disposed.
- StrictMode-safe: if the component unmounts before the async attach
  resolves, the eventual handle is disposed instead of leaking a refcount.

## Caller responsibilities

- Call `loader.config({ monaco })` before any `<Editor>` mounts so
  `@monaco-editor/react` reuses your bundled monaco instead of fetching a
  second copy from a CDN.
- Provide a `MonacoEnvironment.getWorker` default branch that returns
  the standard `editor.worker` — KSON tokenization runs there.
- End each kson model's URI in `.kson` (via `<Editor defaultPath="...">`)
  so the LSP recognizes the file extension.
