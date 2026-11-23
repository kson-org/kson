import {describe, it} from 'mocha';
import assert from 'assert';
import {
    ClientCapabilities,
    Disposable,
    InitializeError,
    InitializeParams,
    InitializeResult,
    ServerRequestHandler,
} from 'vscode-languageserver';
import {ConnectionStub, NOOP_DISPOSABLE} from './ConnectionStub.js';
import {startKsonServer} from '../startKsonServer.js';
import {createCommandExecutor} from '../core/commands/createCommandExecutor.node.js';
import {PULL_DIAGNOSTICS_CAPABILITIES} from './TestHelpers.js';

/**
 * A {@link ConnectionStub} that also accepts the lifecycle and workspace registrations
 * {@link startKsonServer} makes, capturing its initialize handler so tests can run the handshake.
 */
class ServerConnectionStub extends ConnectionStub {
    public initializeHandler: ServerRequestHandler<InitializeParams, InitializeResult, never, InitializeError>;

    override onInitialize(handler: ServerRequestHandler<InitializeParams, InitializeResult, never, InitializeError>): Disposable {
        this.initializeHandler = handler;
        return NOOP_DISPOSABLE;
    }

    // Registered by startKsonServer, but not exercised by these tests
    override onInitialized(): Disposable {
        return NOOP_DISPOSABLE;
    }

    override onRequest(): Disposable {
        return NOOP_DISPOSABLE;
    }

    override onDidChangeWatchedFiles(): Disposable {
        return NOOP_DISPOSABLE;
    }

    override onDidChangeConfiguration(): Disposable {
        return NOOP_DISPOSABLE;
    }

    override listen(): void {
    }

    async requestInitialize(capabilities: ClientCapabilities = {}): Promise<InitializeResult> {
        return await this.initializeHandler(
            {processId: null, rootUri: null, capabilities},
            {} as any, {} as any, undefined
        ) as InitializeResult;
    }
}

describe('startKsonServer', () => {
    it('should declare quotes, newline and space as completion trigger characters', async () => {
        const connection = new ServerConnectionStub();
        startKsonServer(connection, async () => undefined, createCommandExecutor);

        const {capabilities} = await connection.requestInitialize();

        assert.deepStrictEqual(capabilities.completionProvider?.triggerCharacters, ['"', "'", '\n', ' ']);
    });

    it('should advertise pull diagnostics to a client that can pull them', async () => {
        const connection = new ServerConnectionStub();
        startKsonServer(connection, async () => undefined, createCommandExecutor);

        const {capabilities} = await connection.requestInitialize(PULL_DIAGNOSTICS_CAPABILITIES);

        assert.deepStrictEqual(
            capabilities.diagnosticProvider,
            {identifier: 'kson', interFileDependencies: false, workspaceDiagnostics: false}
        );
    });

    it('should not advertise pull diagnostics to a client that cannot pull them', async () => {
        const connection = new ServerConnectionStub();
        startKsonServer(connection, async () => undefined, createCommandExecutor);

        const {capabilities} = await connection.requestInitialize({});

        assert.strictEqual(capabilities.diagnosticProvider, undefined);
    });
});
