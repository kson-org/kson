# KSON language server

A [Language Server Protocol](https://microsoft.github.io/language-server-protocol/) server for
[KSON](https://kson.org), which combines the best of JSON and YAML. Any editor or tool that can start a language
server as a command can use it for `.kson` files.

It reports syntax errors, and schema violations in files mapped to a schema. It also provides formatting, semantic
highlighting, hover, completion and go to definition.

## Usage

The `kson-language-server` executable speaks LSP over stdio. Run it with `npx`:

```shell
npx kson-language-server --stdio
```

Or install it, and have your editor start `kson-language-server --stdio`:

```shell
npm install --global kson-language-server
```

## Schemas

To check files against JSON Schemas written in KSON, put a `.kson-schema.kson` in the workspace root. It maps glob
patterns to schema files, both relative to the workspace root:

```kson
schemas:
  - fileMatch: ['config/*.kson']
    schema: 'schemas/config.schema.kson'
```

## Links

- [KSON](https://kson.org)
- [GitHub Repository](https://github.com/kson-org/kson)
- [Report Issues](https://github.com/kson-org/kson/issues)

## License

Apache-2.0
