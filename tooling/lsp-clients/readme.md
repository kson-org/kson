## Language Server Protocol Clients

This project contains clients of the [Language Server Protocol](../language-server-protocol). Currently, we have
a [Monaco](./monaco) client and a [VS Code](./vscode) client.
The monaco clients can be run with the following gradlew tasks:

```shell
./gradlew tooling:lsp-clients:npm_run_monaco
./gradlew tooling:lsp-clients:npm_run_vscode
```

## Features

- Syntax highlighting for `.kson` files
- Language server integration
- Auto-formatting
- Diagnostics and error reporting

### Development

The clients start a KSON Language Server. If we are on the browser runtime (Monaco or VS Code web) the server is started
in a webworker. If we are on a node runtime the server is started in its own process.

Since there is a distinction between node and browser runtimes within the VS Code client this client has both a `./node`
and `./browser` directory for the client and server. The Monaco client only uses the browser runtime.

`./shared` contains shared code for both clients. Currently, these are
the [textmate grammar](./shared/extension/config/kson.tmLanguage.json)
, [language configuration](./shared/extension/config/language-configuration.json), and the code
to [start a server](./shared/src/connection/browserConnection.ts) for the browser runtime.
