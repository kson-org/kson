import {describe, it} from 'mocha';
import assert from 'assert';
import {
    Disposable,
    InitializeError,
    InitializeParams,
    InitializeResult,
    ServerRequestHandler,
} from 'vscode-languageserver';
import {ConnectionStub, NOOP_DISPOSABLE} from './ConnectionStub.js';
import {startKsonServer} from '../startKsonServer.js';
import {createCommandExecutor} from '../core/commands/createCommandExecutor.node.js';

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

    async requestInitialize(): Promise<InitializeResult> {
        return await this.initializeHandler(
            {processId: null, rootUri: null, capabilities: {}},
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
});
