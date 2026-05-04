// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

// Hoisted mocks so vi.mock factories can reference them.
const mocks = vi.hoisted(() => {
    const registerKsonLanguage = vi.fn();
    const schemaModelsDispose = vi.fn();
    const createBundledSchemaModels = vi.fn();
    const trackingDispose = vi.fn();
    const attachToEditor = vi.fn();
    const fakeBridge = { attachToEditor };
    const fakeServerCapabilities = { sentinel: true };
    const acquireLsp = vi.fn();
    const releaseLsp = vi.fn();
    return {
        registerKsonLanguage,
        schemaModelsDispose,
        createBundledSchemaModels,
        trackingDispose,
        attachToEditor,
        fakeBridge,
        fakeServerCapabilities,
        acquireLsp,
        releaseLsp,
    };
});

vi.mock('./language/ksonLanguage.js', () => ({
    KSON_LANGUAGE_ID: 'kson',
    registerKsonLanguage: mocks.registerKsonLanguage,
}));

vi.mock('./bundledSchemas.js', () => ({
    createBundledSchemaModels: mocks.createBundledSchemaModels,
}));

vi.mock('./lspRegistry.js', () => ({
    acquireLsp: mocks.acquireLsp,
    releaseLsp: mocks.releaseLsp,
}));

const {
    registerKsonLanguage,
    schemaModelsDispose,
    createBundledSchemaModels,
    trackingDispose,
    attachToEditor,
    fakeBridge,
    fakeServerCapabilities,
    acquireLsp,
    releaseLsp,
} = mocks;

import { attachKsonLsp } from './attachKsonLsp.js';

beforeEach(() => {
    registerKsonLanguage.mockReset();
    schemaModelsDispose.mockReset();
    createBundledSchemaModels.mockReset().mockReturnValue({
        created: [],
        dispose: schemaModelsDispose,
    });
    trackingDispose.mockReset();
    attachToEditor.mockReset().mockReturnValue({ dispose: trackingDispose });
    acquireLsp.mockReset().mockResolvedValue({
        bridge: fakeBridge,
        worker: { terminate: () => {} },
        serverCapabilities: fakeServerCapabilities,
    });
    releaseLsp.mockReset();
});

afterEach(() => {
    vi.restoreAllMocks();
});

/** Minimal editor stand-in — attachToEditor is mocked so we don't need a real Monaco surface. */
function fakeEditor() {
    return {} as unknown as Parameters<typeof attachKsonLsp>[0];
}

describe('attachKsonLsp', () => {
    it('acquires the LSP and attaches to the editor exactly once', async () => {
        const editor = fakeEditor();
        await attachKsonLsp(editor, { lspOptions: { enableBundledSchemas: true } });

        expect(acquireLsp).toHaveBeenCalledTimes(1);
        expect(acquireLsp).toHaveBeenCalledWith({ enableBundledSchemas: true });
        expect(attachToEditor).toHaveBeenCalledTimes(1);
        expect(attachToEditor).toHaveBeenCalledWith(editor, 'kson', fakeServerCapabilities);
    });

    it('dispose runs trackingDisposable, schemaModels.dispose, and releaseLsp', async () => {
        const handle = await attachKsonLsp(fakeEditor());

        handle.dispose();

        expect(trackingDispose).toHaveBeenCalledTimes(1);
        expect(schemaModelsDispose).toHaveBeenCalledTimes(1);
        expect(releaseLsp).toHaveBeenCalledTimes(1);
    });

    it('dispose is idempotent — second call is a no-op and refcount drops only once', async () => {
        const handle = await attachKsonLsp(fakeEditor());

        handle.dispose();
        handle.dispose();
        handle.dispose();

        expect(trackingDispose).toHaveBeenCalledTimes(1);
        expect(schemaModelsDispose).toHaveBeenCalledTimes(1);
        expect(releaseLsp).toHaveBeenCalledTimes(1);
    });

    it('releases the LSP when attachToEditor throws after acquire', async () => {
        attachToEditor.mockImplementationOnce(() => {
            throw new Error('Editor has no model');
        });

        await expect(attachKsonLsp(fakeEditor())).rejects.toThrow('Editor has no model');

        // schemaModels was created before the throw — its dispose must run.
        expect(schemaModelsDispose).toHaveBeenCalledTimes(1);
        // refcount must be released so the worker can be torn down.
        expect(releaseLsp).toHaveBeenCalledTimes(1);
    });

    it('releases the LSP when createBundledSchemaModels throws after acquire', async () => {
        createBundledSchemaModels.mockImplementationOnce(() => {
            throw new Error('bad schema');
        });

        await expect(attachKsonLsp(fakeEditor())).rejects.toThrow('bad schema');

        // schemaModels was never constructed — its dispose must NOT run.
        expect(schemaModelsDispose).not.toHaveBeenCalled();
        // attachToEditor never reached.
        expect(attachToEditor).not.toHaveBeenCalled();
        expect(releaseLsp).toHaveBeenCalledTimes(1);
    });
});
