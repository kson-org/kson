# Monaco Editor Integration Examples

Three runnable examples of how to integrate `@kson/monaco-editor` into a
downstream project, covering the three consumer styles the package supports:

| Demo                      | Integration style                                                          | Port |
| ------------------------- | -------------------------------------------------------------------------- | ---- |
| [`library/`](library/)    | Vanilla TS app importing `createKsonEditor` from `@kson/monaco-editor`.    | 5174 |
| [`iframe/`](iframe/)      | Static page dropping in `kson-editor.js` via a `<script>` tag.             | 5175 |
| [`react/`](react/)        | `@monaco-editor/react` app attaching the LSP via the `useKsonLsp` hook.    | 5176 |

Pick the one that matches your stack and copy from it.

## Run

```bash
./gradlew tooling:lsp-clients:npm_run_demoLibrary
./gradlew tooling:lsp-clients:npm_run_demoIframe
./gradlew tooling:lsp-clients:npm_run_demoReact
```

Each task builds `@kson/monaco-editor` first (the iframe demo also builds
`dist-iframe/`), runs `npm install` inside the demo directory, then starts
vite on the port above.

## Layout note

Each demo lives **outside** the npm workspaces array, has its own
`node_modules`, and resolves `@kson/monaco-editor` via `file:../../monaco`
through the package's export map — i.e. the same way a downstream user
installs it. This keeps the examples honest: what works here is what works
when someone adds the package to their own project.
