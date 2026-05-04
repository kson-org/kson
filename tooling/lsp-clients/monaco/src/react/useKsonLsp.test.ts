// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, createElement, StrictMode } from 'react';
import { createRoot, type Root } from 'react-dom/client';

// Hoisted mock surface so vi.mock factories can reference it.
const mocks = vi.hoisted(() => {
    const dispose = vi.fn();
    const attachKsonLsp = vi.fn();
    return { dispose, attachKsonLsp };
});

vi.mock('../attachKsonLsp.js', () => ({
    attachKsonLsp: mocks.attachKsonLsp,
}));

const { dispose: mockDispose, attachKsonLsp: mockAttach } = mocks;

import { useKsonLsp } from './useKsonLsp.js';

type Editor = Parameters<typeof useKsonLsp>[0];

let container: HTMLDivElement;
let root: Root;

beforeEach(() => {
    mockDispose.mockReset();
    mockAttach.mockReset().mockResolvedValue({ dispose: mockDispose });
    container = document.createElement('div');
    document.body.appendChild(container);
    root = createRoot(container);
});

afterEach(() => {
    act(() => root.unmount());
    container.remove();
    vi.restoreAllMocks();
});

/** Tiny consumer component — calls the hook with whatever editor we hand it. */
function Probe(props: { editor: Editor; options?: Parameters<typeof useKsonLsp>[1] }) {
    useKsonLsp(props.editor, props.options);
    return null;
}

const fakeEditor = (): Editor => ({}) as unknown as Editor;

describe('useKsonLsp', () => {
    it('attaches when the editor is non-null', async () => {
        const editor = fakeEditor();
        await act(async () => {
            root.render(createElement(Probe, { editor }));
        });

        expect(mockAttach).toHaveBeenCalledTimes(1);
        expect(mockAttach).toHaveBeenCalledWith(editor, undefined);
    });

    it('forwards options to attachKsonLsp', async () => {
        const editor = fakeEditor();
        const options = { lspOptions: { enableBundledSchemas: true } };
        await act(async () => {
            root.render(createElement(Probe, { editor, options }));
        });

        expect(mockAttach).toHaveBeenCalledWith(editor, options);
    });

    it('skips attach when the editor is null', async () => {
        await act(async () => {
            root.render(createElement(Probe, { editor: null }));
        });

        expect(mockAttach).not.toHaveBeenCalled();
    });

    it('disposes the handle on unmount', async () => {
        await act(async () => {
            root.render(createElement(Probe, { editor: fakeEditor() }));
        });

        await act(async () => {
            root.unmount();
        });

        expect(mockDispose).toHaveBeenCalledTimes(1);
    });

    it('absorbs StrictMode double-invoke without leaking a refcount', async () => {
        await act(async () => {
            root.render(
                createElement(StrictMode, null, createElement(Probe, { editor: fakeEditor() })),
            );
        });

        await act(async () => {
            root.unmount();
        });

        // StrictMode mounts the effect twice in dev — both attaches must dispose.
        expect(mockAttach).toHaveBeenCalledTimes(2);
        expect(mockDispose).toHaveBeenCalledTimes(2);
    });

    it('disposes the late-arriving handle when unmount races attach', async () => {
        let resolveAttach!: (handle: { dispose: typeof mockDispose }) => void;
        mockAttach.mockReturnValueOnce(
            new Promise((resolve) => {
                resolveAttach = resolve;
            }),
        );

        await act(async () => {
            root.render(createElement(Probe, { editor: fakeEditor() }));
        });

        // Unmount before the attach promise resolves.
        await act(async () => {
            root.unmount();
        });

        // Now the attach completes — the cancelled flag must dispose the orphaned handle.
        await act(async () => {
            resolveAttach({ dispose: mockDispose });
        });

        expect(mockDispose).toHaveBeenCalledTimes(1);
    });
});
