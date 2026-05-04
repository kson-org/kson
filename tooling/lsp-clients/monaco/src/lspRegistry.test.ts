// @vitest-environment happy-dom
import { afterEach, describe, it, expect, vi } from 'vitest';

// Stub the worker URL — vite's ?worker&url query is not understood by vitest.
vi.mock('./worker/ksonServer?worker&url', () => ({ default: 'kson-worker.js' }));

// Stub the bridge so we can drive its initialize() and observe dispose().
const initialize = vi.fn().mockResolvedValue({ capabilities: { sentinel: true } });
const bridgeDispose = vi.fn();
let lastBridge: { initialize: typeof initialize; dispose: typeof bridgeDispose } | null = null;
vi.mock('./bridge/index.js', () => ({
    KsonLspBridge: class {
        initialize = initialize;
        dispose = bridgeDispose;
        constructor() { lastBridge = this as never; }
    },
}));

// Stub Worker so the constructor doesn't blow up in node/happy-dom.
const workerTerminate = vi.fn();
class WorkerStub {
    terminate = workerTerminate;
    constructor(_url: string, _opts?: unknown) {}
}
vi.stubGlobal('Worker', WorkerStub);

import {
    acquireLsp,
    releaseLsp,
    _getRegistryStateForTest,
} from './lspRegistry.js';

afterEach(() => {
    // Drain any leftover refs so each test starts fresh.
    while (_getRegistryStateForTest()) releaseLsp();
    initialize.mockClear();
    bridgeDispose.mockClear();
    workerTerminate.mockClear();
    lastBridge = null;
});

describe('lspRegistry', () => {
    it('initializes the bridge once and shares it across acquires', async () => {
        const a = await acquireLsp({ enableBundledSchemas: true });
        const b = await acquireLsp();

        expect(initialize).toHaveBeenCalledTimes(1);
        expect(a.bridge).toBe(b.bridge);
        expect(a.worker).toBe(b.worker);
        expect(a.serverCapabilities).toEqual({ sentinel: true });
        expect(_getRegistryStateForTest()?.refCount).toBe(2);
    });

    it('passes initializationOptions only on the first acquire', async () => {
        await acquireLsp({ enableBundledSchemas: true });
        await acquireLsp({ enableBundledSchemas: false });

        expect(initialize).toHaveBeenCalledTimes(1);
        expect(initialize).toHaveBeenCalledWith({
            initializationOptions: { enableBundledSchemas: true },
        });
    });

    it('disposes worker + bridge only when the last reference is released', async () => {
        await acquireLsp();
        await acquireLsp();

        releaseLsp();
        expect(bridgeDispose).not.toHaveBeenCalled();
        expect(workerTerminate).not.toHaveBeenCalled();
        expect(_getRegistryStateForTest()?.refCount).toBe(1);

        releaseLsp();
        expect(bridgeDispose).toHaveBeenCalledTimes(1);
        expect(workerTerminate).toHaveBeenCalledTimes(1);
        expect(_getRegistryStateForTest()).toBeNull();
    });

    it('re-creates the bridge after a full release', async () => {
        await acquireLsp();
        const firstBridge = lastBridge;
        releaseLsp();

        await acquireLsp();
        expect(lastBridge).not.toBe(firstBridge);
        expect(initialize).toHaveBeenCalledTimes(2);
    });

    it('shares the same handle for concurrent acquires', async () => {
        const [a, b] = await Promise.all([acquireLsp(), acquireLsp()]);
        expect(a.bridge).toBe(b.bridge);
        expect(initialize).toHaveBeenCalledTimes(1);
        expect(_getRegistryStateForTest()?.refCount).toBe(2);
    });

    it('release with no active state is a no-op', () => {
        expect(_getRegistryStateForTest()).toBeNull();
        releaseLsp();
        expect(_getRegistryStateForTest()).toBeNull();
    });
});
