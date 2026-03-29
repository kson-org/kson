import { describe, it, expect, beforeEach } from 'vitest';
import { JsonRpcConnection } from './JsonRpcConnection.js';

/**
 * Stub that captures posted messages and lets tests simulate incoming messages.
 */
class WorkerStub {
    readonly posted: unknown[] = [];
    onmessage: ((event: MessageEvent) => void) | null = null;

    postMessage(message: unknown): void {
        this.posted.push(message);
    }

    /** Simulate the worker sending a message to the connection. */
    receive(data: unknown): void {
        this.onmessage?.({ data } as MessageEvent);
    }
}

describe('JsonRpcConnection', () => {
    let worker: WorkerStub;
    let connection: JsonRpcConnection;

    beforeEach(() => {
        worker = new WorkerStub();
        connection = new JsonRpcConnection(worker as unknown as Worker);
    });

    it('sends a JSON-RPC request with incrementing id', () => {
        connection.sendRequest('initialize', { rootUri: null });
        connection.sendRequest('shutdown');

        expect(worker.posted).toEqual([
            { jsonrpc: '2.0', id: 1, method: 'initialize', params: { rootUri: null } },
            { jsonrpc: '2.0', id: 2, method: 'shutdown', params: undefined },
        ]);
    });

    it('resolves when a success response arrives', async () => {
        const promise = connection.sendRequest<string>('textDocument/hover');

        worker.receive({ jsonrpc: '2.0', id: 1, result: 'hello' });

        await expect(promise).resolves.toBe('hello');
    });

    it('rejects when an error response arrives', async () => {
        const promise = connection.sendRequest('textDocument/hover');

        worker.receive({
            jsonrpc: '2.0',
            id: 1,
            error: { code: -32601, message: 'Method not found' },
        });

        await expect(promise).rejects.toThrow('Method not found');
    });

    it('ignores responses for unknown request ids', () => {
        // Should not throw
        worker.receive({ jsonrpc: '2.0', id: 999, result: null });
    });

    it('sends a notification without an id', () => {
        connection.sendNotification('initialized', {});

        expect(worker.posted).toEqual([
            { jsonrpc: '2.0', method: 'initialized', params: {} },
        ]);
    });

    it('dispatches server notifications to registered handlers', () => {
        const received: unknown[] = [];
        connection.onNotification('window/logMessage', (params) => received.push(params));

        worker.receive({ jsonrpc: '2.0', method: 'window/logMessage', params: { type: 3, message: 'hi' } });

        expect(received).toEqual([{ type: 3, message: 'hi' }]);
    });

    it('silently ignores server notifications with no handler', () => {
        // Should not throw
        worker.receive({ jsonrpc: '2.0', method: 'unknown/notification', params: {} });
    });

    it('dispatches server requests and sends back the result', async () => {
        connection.onRequest('workspace/applyEdit', (params) => {
            const p = params as { label: string };
            return { applied: true, label: p.label };
        });

        worker.receive({ jsonrpc: '2.0', id: 42, method: 'workspace/applyEdit', params: { label: 'fmt' } });

        // Give the promise microtask a chance to resolve
        await new Promise((r) => setTimeout(r, 0));

        expect(worker.posted).toEqual([
            { jsonrpc: '2.0', id: 42, result: { applied: true, label: 'fmt' } },
        ]);
    });

    it('sends an error response when a server request handler throws', async () => {
        connection.onRequest('workspace/applyEdit', () => {
            throw new Error('boom');
        });

        worker.receive({ jsonrpc: '2.0', id: 7, method: 'workspace/applyEdit', params: {} });

        await new Promise((r) => setTimeout(r, 0));

        expect(worker.posted).toEqual([
            { jsonrpc: '2.0', id: 7, error: { code: -32603, message: 'Error: boom' } },
        ]);
    });

    it('acknowledges unknown server requests with null to avoid hanging', async () => {
        worker.receive({ jsonrpc: '2.0', id: 99, method: 'unknown/request', params: {} });

        await new Promise((r) => setTimeout(r, 0));

        expect(worker.posted).toEqual([
            { jsonrpc: '2.0', id: 99, result: null },
        ]);
    });

    it('rejects all pending requests on dispose', async () => {
        const p1 = connection.sendRequest('a');
        const p2 = connection.sendRequest('b');

        connection.dispose();

        await expect(p1).rejects.toThrow('Connection disposed');
        await expect(p2).rejects.toThrow('Connection disposed');
    });
});
