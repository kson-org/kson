# KSON plugin for Claude Code

A [Claude Code](https://code.claude.com/docs/en/plugins/code-intelligence) plugin that shows Claude the diagnostics the
[KSON language server](../../language-server-protocol) reports for the `.kson` files it edits: syntax errors, and schema
violations in files that a `.kson-schema.kson` at the project root maps to a schema.

## Requirements

- Node.js, with `npx` on your `PATH`
- `kson-language-server` published to npm at the version [`.lsp.json`](.lsp.json) pins

## Use

From the repository root:

```shell
claude --plugin-dir tooling/lsp-clients/claude-code
```

## Limitations

- `.kson` files only: the server would report errors on `.json` files KSON can't parse, such as a `tsconfig.json` with
  comments.
- Claude Code shows the model diagnostics only when the Bash tool is available.
- Diagnostics for a turn's last edit may arrive after the model has finished.

## Development

The plugin's tests run with the other client tests:

```shell
./gradlew tooling:lsp-clients:npm_run_test
```
