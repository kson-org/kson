// @vitest-environment happy-dom
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

// In-memory model store keyed by URI string.  Each createModel returns a unique
// object with a dispose spy so tests can assert per-model lifecycle.
interface FakeModel {
    uri: { toString(): string; scheme: string };
    getValue(): string;
    dispose: ReturnType<typeof vi.fn>;
}
const modelsByUri = new Map<string, FakeModel>();

// Minimal stand-in for monaco.Uri.parse — splits scheme so isBundledSchemaUri works.
function parseUri(uri: string): { toString(): string; scheme: string } {
    const scheme = uri.split(':', 1)[0];
    return { toString: () => uri, scheme };
}

vi.mock('monaco-editor', () => ({
    Uri: {
        parse: parseUri,
    },
    editor: {
        getModel: (uri: { toString(): string }) => modelsByUri.get(uri.toString()) ?? null,
        createModel: (value: string, _languageId: string, uri: { toString(): string; scheme: string }) => {
            const model: FakeModel = {
                uri,
                getValue: () => value,
                dispose: vi.fn(() => modelsByUri.delete(uri.toString())),
            };
            modelsByUri.set(uri.toString(), model);
            return model;
        },
    },
}));

const openReadOnlyDocument = vi.fn();
const fakeBridge = { openReadOnlyDocument } as unknown as Parameters<typeof createBundledSchemaModels>[1];

import { createBundledSchemaModels, isBundledSchemaUri } from './bundledSchemas.js';

beforeEach(() => {
    modelsByUri.clear();
    openReadOnlyDocument.mockClear();
});

afterEach(() => {
    modelsByUri.clear();
});

describe('createBundledSchemaModels', () => {
    it('creates a model for each bundled schema and metaschema', () => {
        const result = createBundledSchemaModels(
            {
                bundledSchemas: [
                    { fileExtension: 'kson', schemaContent: 'schema-a' },
                    { fileExtension: 'foo', schemaContent: 'schema-b' },
                ],
                bundledMetaSchemas: [
                    { schemaId: 'meta-1', name: 'meta', schemaContent: 'meta-content' },
                ],
            },
            fakeBridge,
        );

        expect(result.created).toHaveLength(3);
        expect([...modelsByUri.keys()]).toEqual([
            'bundled://schema/kson.schema.kson',
            'bundled://schema/foo.schema.kson',
            'bundled://metaschema/meta.schema.kson',
        ]);
    });

    it('skips creating a model when one already exists for the URI', () => {
        const opts = {
            bundledSchemas: [{ fileExtension: 'kson', schemaContent: 'first' }],
        };

        const a = createBundledSchemaModels(opts, fakeBridge);
        const b = createBundledSchemaModels(opts, fakeBridge);

        expect(a.created).toHaveLength(1);
        expect(b.created).toHaveLength(0);
        expect(modelsByUri.size).toBe(1);
    });

    it('disposes only the models the call created', () => {
        const opts = {
            bundledSchemas: [{ fileExtension: 'kson', schemaContent: 'first' }],
        };

        const a = createBundledSchemaModels(opts, fakeBridge);
        const b = createBundledSchemaModels(opts, fakeBridge);
        const aModel = a.created[0];

        // b's call did not create the model; its dispose() should not touch it.
        b.dispose();
        expect(aModel.dispose).not.toHaveBeenCalled();
        expect(modelsByUri.has('bundled://schema/kson.schema.kson')).toBe(true);

        // a created the model; its dispose() should drop it.
        a.dispose();
        expect(aModel.dispose).toHaveBeenCalledTimes(1);
        expect(modelsByUri.has('bundled://schema/kson.schema.kson')).toBe(false);
    });

    it('opens read-only documents only for newly-created models', () => {
        const opts = {
            bundledSchemas: [{ fileExtension: 'kson', schemaContent: 'content' }],
        };

        createBundledSchemaModels(opts, fakeBridge);
        expect(openReadOnlyDocument).toHaveBeenCalledTimes(1);
        expect(openReadOnlyDocument).toHaveBeenCalledWith(
            'bundled://schema/kson.schema.kson',
            'content',
        );

        openReadOnlyDocument.mockClear();
        // Second call sees the existing model — should not re-open.
        createBundledSchemaModels(opts, fakeBridge);
        expect(openReadOnlyDocument).not.toHaveBeenCalled();
    });

    it('handles undefined options and empty arrays', () => {
        const r1 = createBundledSchemaModels(undefined, fakeBridge);
        expect(r1.created).toEqual([]);

        const r2 = createBundledSchemaModels({}, fakeBridge);
        expect(r2.created).toEqual([]);
    });
});

describe('isBundledSchemaUri', () => {
    it('returns true for bundled schema and metaschema URIs', () => {
        expect(isBundledSchemaUri(parseUri('bundled://schema/kson.schema.kson'))).toBe(true);
        expect(isBundledSchemaUri(parseUri('bundled://metaschema/config.schema.kson'))).toBe(true);
    });

    it('returns false for non-bundled URIs', () => {
        expect(isBundledSchemaUri(parseUri('inmemory://kson/document.kson'))).toBe(false);
        expect(isBundledSchemaUri(parseUri('file:///tmp/example.kson'))).toBe(false);
    });
});
