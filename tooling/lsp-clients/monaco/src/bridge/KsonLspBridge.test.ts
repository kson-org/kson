// @vitest-environment happy-dom
import { describe, it, expect, beforeEach } from 'vitest';
import { KsonLspBridge, type KsonLspBridgeOptions } from './KsonLspBridge.js';

/**
 * Stub worker that captures posted messages and lets tests respond to them.
 * Mirrors the helper in JsonRpcConnection.test.ts.
 */
class WorkerStub {
    readonly posted: Array<{ jsonrpc: string; id?: number; method: string; params?: unknown }> = [];
    onmessage: ((event: MessageEvent) => void) | null = null;

    postMessage(message: unknown): void {
        this.posted.push(message as never);
    }

    /** Resolve every initialize request that's been posted so far. */
    respondToInitialize(): void {
        for (const msg of this.posted) {
            if (msg.method === 'initialize' && msg.id !== undefined) {
                this.onmessage?.({ data: { jsonrpc: '2.0', id: msg.id, result: { capabilities: {} } } } as MessageEvent);
            }
        }
    }
}

/** Run initialize() with the given options and return the params posted on the wire. */
async function captureInitializeParams(
    options?: KsonLspBridgeOptions,
): Promise<{ initializationOptions?: KsonLspBridgeOptions['initializationOptions'] }> {
    const worker = new WorkerStub();
    const bridge = new KsonLspBridge(worker as unknown as Worker);
    const promise = bridge.initialize(options);
    worker.respondToInitialize();
    await promise;
    const init = worker.posted.find((m) => m.method === 'initialize');
    return init!.params as { initializationOptions?: KsonLspBridgeOptions['initializationOptions'] };
}

describe('KsonLspBridge.initialize — enableBundledSchemas defaulting', () => {
    let worker: WorkerStub;

    beforeEach(() => {
        worker = new WorkerStub();
    });

    it('infers enableBundledSchemas=true when bundledSchemas is non-empty and the flag is omitted', async () => {
        const params = await captureInitializeParams({
            initializationOptions: {
                bundledSchemas: [{ fileExtension: 'kson', schemaContent: '{}' }],
            },
        });

        expect(params.initializationOptions?.enableBundledSchemas).toBe(true);
        expect(params.initializationOptions?.bundledSchemas).toHaveLength(1);
    });

    it('infers enableBundledSchemas=false when bundledSchemas is empty and the flag is omitted', async () => {
        const params = await captureInitializeParams({
            initializationOptions: { bundledSchemas: [] },
        });

        expect(params.initializationOptions?.enableBundledSchemas).toBe(false);
    });

    it('honors an explicit enableBundledSchemas=false even with non-empty bundledSchemas', async () => {
        const params = await captureInitializeParams({
            initializationOptions: {
                bundledSchemas: [{ fileExtension: 'kson', schemaContent: '{}' }],
                enableBundledSchemas: false,
            },
        });

        expect(params.initializationOptions?.enableBundledSchemas).toBe(false);
    });

    it('honors an explicit enableBundledSchemas=true with empty bundledSchemas', async () => {
        const params = await captureInitializeParams({
            initializationOptions: {
                bundledSchemas: [],
                enableBundledSchemas: true,
            },
        });

        expect(params.initializationOptions?.enableBundledSchemas).toBe(true);
    });

    it('omits enableBundledSchemas entirely when initializationOptions is undefined', async () => {
        // No options passed at all — nothing to infer from, so the field stays absent.
        const promise = new KsonLspBridge(worker as unknown as Worker).initialize();
        worker.respondToInitialize();
        await promise;

        const init = worker.posted.find((m) => m.method === 'initialize');
        const params = init!.params as { initializationOptions?: unknown };
        expect(params.initializationOptions).toBeUndefined();
    });

    it('infers enableBundledSchemas=false when bundledSchemas is absent and the flag is omitted', async () => {
        const params = await captureInitializeParams({
            initializationOptions: { bundledMetaSchemas: [] },
        });

        // Inference only fires when initializationOptions is provided; treat absent
        // bundledSchemas as "no schemas" → false.
        expect(params.initializationOptions?.enableBundledSchemas).toBe(false);
    });
});
