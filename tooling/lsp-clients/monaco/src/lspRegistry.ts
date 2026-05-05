/**
 * Refcounted singleton for the KSON LSP worker + bridge.
 *
 * Multiple callers (createKsonEditor, attachKsonLsp) share one language
 * server per page.  The first acquire spins it up; release tears it down
 * when the last consumer goes away.
 *
 * Initialization options are honored only on the first acquire — later
 * callers join the already-initialized server.
 */

import { KsonLspBridge, type KsonLspBridgeOptions, type ServerCapabilities } from './bridge/index.js';
import workerUrl from './worker/ksonServer?worker&url';

export interface LspHandle {
    bridge: KsonLspBridge;
    worker: Worker;
    serverCapabilities: ServerCapabilities;
}

interface RegistryState {
    bridge: KsonLspBridge;
    worker: Worker;
    refCount: number;
    initPromise: Promise<LspHandle>;
}

let state: RegistryState | null = null;

/** Acquire a reference to the shared LSP server.  Lazily creates it. */
export function acquireLsp(
    lspOptions?: KsonLspBridgeOptions['initializationOptions'],
): Promise<LspHandle> {
    if (state) {
        state.refCount++;
        return state.initPromise;
    }

    const worker = new Worker(workerUrl, {
        type: 'module',
        name: 'KsonLanguageServer',
    });
    const bridge = new KsonLspBridge(worker);

    // If initialize rejects we leave `state` set and the rejection propagates;
    // a fresh acquire after a rejection would still join the broken bridge.
    // Acceptable for MVP — initialize is expected to be reliable in practice.
    const initPromise: Promise<LspHandle> = bridge
        .initialize({ initializationOptions: lspOptions })
        .then((result) => ({
            bridge,
            worker,
            serverCapabilities: result.capabilities,
        }));

    state = { bridge, worker, refCount: 1, initPromise };
    return initPromise;
}

/** Release a reference.  When refcount reaches zero the server is disposed. */
export function releaseLsp(): void {
    if (!state) return;
    state.refCount--;
    if (state.refCount > 0) return;

    const { bridge, worker } = state;
    state = null;
    bridge.dispose();
    worker.terminate();
}

/** Test-only: snapshot of the current registry state. */
export function _getRegistryStateForTest(): { refCount: number } | null {
    return state ? { refCount: state.refCount } : null;
}
