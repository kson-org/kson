import {describe, it} from 'mocha';
import * as assert from 'assert';
import {TextDocument} from 'vscode-languageserver-textdocument';
import {DocumentUri} from 'vscode-languageserver';
import {CompositeSchemaProvider} from '../../../core/schema/CompositeSchemaProvider';
import {BundledSchemaProvider, BundledMetaSchemaConfig} from '../../../core/schema/BundledSchemaProvider';
import {SchemaProvider, NoOpSchemaProvider} from '../../../core/schema/SchemaProvider';

/**
 * Minimal mock that simulates FileSystemSchemaProvider behavior (URI-based lookup).
 * Only used because FileSystemSchemaProvider requires disk I/O.
 */
class UriSchemaProvider implements SchemaProvider {
    private schemas: Map<string, TextDocument> = new Map();

    addSchema(documentUri: string, schema: TextDocument): void {
        this.schemas.set(documentUri, schema);
    }

    getSchemaForDocument(documentUri: DocumentUri): TextDocument | undefined {
        return this.schemas.get(documentUri);
    }

    reload(): void {}

    isSchemaFile(fileUri: DocumentUri): boolean {
        for (const schema of this.schemas.values()) {
            if (schema.uri === fileUri) return true;
        }
        return false;
    }

    getMetaSchemaForId(_schemaId: string): TextDocument | undefined {
        return undefined;
    }
}

describe('CompositeSchemaProvider', () => {
    let logs: string[] = [];

    const logger = {
        info: (msg: string) => logs.push(`INFO: ${msg}`),
        warn: (msg: string) => logs.push(`WARN: ${msg}`),
        error: (msg: string) => logs.push(`ERROR: ${msg}`)
    };

    beforeEach(() => {
        logs = [];
    });

    describe('constructor', () => {
        it('should create provider with no providers', () => {
            const provider = new CompositeSchemaProvider([], logger);
            assert.ok(provider);
            assert.strictEqual(provider.getProviders().length, 0);
        });

        it('should create provider with multiple providers', () => {
            const p1 = new BundledSchemaProvider([], true, logger);
            const p2 = new BundledSchemaProvider([], true, logger);
            const provider = new CompositeSchemaProvider([p1, p2], logger);

            assert.strictEqual(provider.getProviders().length, 2);
        });
    });

    describe('getSchemaForDocument', () => {
        it('should return undefined when no providers', () => {
            const provider = new CompositeSchemaProvider([], logger);
            const schema = provider.getSchemaForDocument('file:///test.kson');
            assert.strictEqual(schema, undefined);
        });

        it('should return undefined when no provider has schema', () => {
            const p1 = new BundledSchemaProvider([], true, logger);
            const p2 = new BundledSchemaProvider([], true, logger);
            const provider = new CompositeSchemaProvider([p1, p2], logger);

            const schema = provider.getSchemaForDocument('file:///test.kson');
            assert.strictEqual(schema, undefined);
        });

        it('should return schema from first matching provider', () => {
            const uriProvider1 = new UriSchemaProvider();
            const uriProvider2 = new UriSchemaProvider();

            const schema1 = TextDocument.create('file:///schema1.kson', 'kson', 1, '{ "from": "first" }');
            uriProvider1.addSchema('file:///test.kson', schema1);

            const schema2 = TextDocument.create('file:///schema2.kson', 'kson', 1, '{ "from": "second" }');
            uriProvider2.addSchema('file:///test.kson', schema2);

            const provider = new CompositeSchemaProvider([uriProvider1, uriProvider2], logger);
            const result = provider.getSchemaForDocument('file:///test.kson');

            assert.ok(result);
            assert.strictEqual(result!.getText(), '{ "from": "first" }');
        });

        it('should try next provider when first returns undefined', () => {
            const uriProvider1 = new UriSchemaProvider();
            const uriProvider2 = new UriSchemaProvider();

            // uriProvider1 has no schema for test.kson
            const schema2 = TextDocument.create('file:///schema2.kson', 'kson', 1, '{ "from": "second" }');
            uriProvider2.addSchema('file:///test.kson', schema2);

            const provider = new CompositeSchemaProvider([uriProvider1, uriProvider2], logger);
            const result = provider.getSchemaForDocument('file:///test.kson');

            assert.ok(result);
            assert.strictEqual(result!.getText(), '{ "from": "second" }');
        });

        it('should match by file extension via BundledSchemaProvider', () => {
            const emptyProvider = new BundledSchemaProvider([], true, logger);
            const bundledProvider = new BundledSchemaProvider([
                { fileExtension: 'kxt', schemaContent: '{ "type": "object" }' }
            ], true, logger);

            const provider = new CompositeSchemaProvider([emptyProvider, bundledProvider], logger);
            const result = provider.getSchemaForDocument('file:///test.kxt');

            assert.ok(result);
            assert.strictEqual(result!.uri, 'bundled://schema/kxt.schema.kson');
        });

        it('should prioritize file system provider over bundled (typical usage)', () => {
            // File system provider (has schema by URI) - higher priority
            const fileSystemProvider = new UriSchemaProvider();
            const fsSchema = TextDocument.create('file:///workspace/schema.kson', 'kson', 1, '{ "from": "filesystem" }');
            fileSystemProvider.addSchema('file:///test.kxt', fsSchema);

            // Bundled provider (has schema by extension) - lower priority
            const bundledProvider = new BundledSchemaProvider([
                { fileExtension: 'kxt', schemaContent: '{ "from": "bundled" }' }
            ], true, logger);

            const provider = new CompositeSchemaProvider([fileSystemProvider, bundledProvider], logger);
            const result = provider.getSchemaForDocument('file:///test.kxt');

            assert.ok(result);
            assert.strictEqual(result!.getText(), '{ "from": "filesystem" }');
        });

        it('should fall back to bundled when file system has no schema', () => {
            // File system provider with no schema
            const fileSystemProvider = new UriSchemaProvider();

            // Bundled provider has schema
            const bundledProvider = new BundledSchemaProvider([
                { fileExtension: 'kxt', schemaContent: '{ "from": "bundled" }' }
            ], true, logger);

            const provider = new CompositeSchemaProvider([fileSystemProvider, bundledProvider], logger);
            const result = provider.getSchemaForDocument('file:///test.kxt');

            assert.ok(result);
            assert.strictEqual(result!.getText(), '{ "from": "bundled" }');
        });
    });

    describe('getMetaSchemaForId', () => {
        it('should return undefined when no providers', () => {
            const provider = new CompositeSchemaProvider([], logger);
            const result = provider.getMetaSchemaForId('http://json-schema.org/draft-07/schema#');
            assert.strictEqual(result, undefined);
        });

        it('should return metaschema from first matching provider', () => {
            const metaSchemas: BundledMetaSchemaConfig[] = [
                { schemaId: 'http://json-schema.org/draft-07/schema#', name: 'draft-07', schemaContent: '{ "from": "first" }' }
            ];
            const provider1 = new BundledSchemaProvider([], true, logger, metaSchemas);

            const metaSchemas2: BundledMetaSchemaConfig[] = [
                { schemaId: 'http://json-schema.org/draft-07/schema#', name: 'draft-07', schemaContent: '{ "from": "second" }' }
            ];
            const provider2 = new BundledSchemaProvider([], true, logger, metaSchemas2);

            const composite = new CompositeSchemaProvider([provider1, provider2], logger);
            const result = composite.getMetaSchemaForId('http://json-schema.org/draft-07/schema#');

            assert.ok(result);
            assert.strictEqual(result!.getText(), '{ "from": "first" }');
        });

        it('should try next provider when first has no match', () => {
            const provider1 = new BundledSchemaProvider([], true, logger);

            const metaSchemas2: BundledMetaSchemaConfig[] = [
                { schemaId: 'http://json-schema.org/draft-07/schema#', name: 'draft-07', schemaContent: '{ "from": "second" }' }
            ];
            const provider2 = new BundledSchemaProvider([], true, logger, metaSchemas2);

            const composite = new CompositeSchemaProvider([provider1, provider2], logger);
            const result = composite.getMetaSchemaForId('http://json-schema.org/draft-07/schema#');

            assert.ok(result);
            assert.strictEqual(result!.getText(), '{ "from": "second" }');
        });

        it('should return undefined when no provider has match', () => {
            const provider1 = new BundledSchemaProvider([], true, logger);
            const provider2 = new BundledSchemaProvider([], true, logger);

            const composite = new CompositeSchemaProvider([provider1, provider2], logger);
            const result = composite.getMetaSchemaForId('http://json-schema.org/draft-07/schema#');

            assert.strictEqual(result, undefined);
        });

        it('should work with UriSchemaProvider (returns undefined)', () => {
            const uriProvider = new UriSchemaProvider();
            const metaSchemas: BundledMetaSchemaConfig[] = [
                { schemaId: 'http://json-schema.org/draft-07/schema#', name: 'draft-07', schemaContent: '{ "metaschema": true }' }
            ];
            const bundledProvider = new BundledSchemaProvider([], true, logger, metaSchemas);

            const composite = new CompositeSchemaProvider([uriProvider, bundledProvider], logger);
            const result = composite.getMetaSchemaForId('http://json-schema.org/draft-07/schema#');

            assert.ok(result);
            assert.strictEqual(result!.getText(), '{ "metaschema": true }');
        });
    });

    describe('isSchemaFile', () => {
        it('should return false when no providers', () => {
            const provider = new CompositeSchemaProvider([], logger);
            assert.strictEqual(provider.isSchemaFile('file:///schema.kson'), false);
        });

        it('should return true when any provider considers it a schema file', () => {
            const uriProvider = new UriSchemaProvider();
            const bundledProvider = new BundledSchemaProvider([
                { fileExtension: 'kxt', schemaContent: '{}' }
            ], true, logger);

            const provider = new CompositeSchemaProvider([uriProvider, bundledProvider], logger);
            assert.strictEqual(provider.isSchemaFile('bundled://schema/kxt.schema.kson'), true);
        });

        it('should return true for metaschema URIs', () => {
            const bundledProvider = new BundledSchemaProvider([], true, logger);

            const provider = new CompositeSchemaProvider([bundledProvider], logger);
            assert.strictEqual(provider.isSchemaFile('bundled://metaschema/draft-07.schema.kson'), true);
        });

        it('should return false when no provider considers it a schema file', () => {
            const p1 = new BundledSchemaProvider([], true, logger);
            const p2 = new BundledSchemaProvider([], true, logger);

            const provider = new CompositeSchemaProvider([p1, p2], logger);
            assert.strictEqual(provider.isSchemaFile('file:///random.kson'), false);
        });

        it('should check all provider types', () => {
            const uriProvider = new UriSchemaProvider();
            uriProvider.addSchema('file:///test.kson', TextDocument.create('file:///my-schema.kson', 'kson', 1, '{}'));

            const bundledProvider = new BundledSchemaProvider([
                { fileExtension: 'kxt', schemaContent: '{}' }
            ], true, logger);

            const provider = new CompositeSchemaProvider([uriProvider, bundledProvider], logger);

            assert.strictEqual(provider.isSchemaFile('file:///my-schema.kson'), true);
            assert.strictEqual(provider.isSchemaFile('bundled://schema/kxt.schema.kson'), true);
            assert.strictEqual(provider.isSchemaFile('file:///other.kson'), false);
        });
    });

    describe('reload', () => {
        it('should reload all providers', () => {
            let reloadCount = 0;

            class CountingProvider implements SchemaProvider {
                getSchemaForDocument(): TextDocument | undefined { return undefined; }
                reload(): void { reloadCount++; }
                isSchemaFile(): boolean { return false; }
                getMetaSchemaForId(): TextDocument | undefined { return undefined; }
            }

            const provider = new CompositeSchemaProvider([
                new CountingProvider(),
                new CountingProvider(),
                new CountingProvider()
            ], logger);

            provider.reload();

            assert.strictEqual(reloadCount, 3);
        });
    });

    describe('getProviders', () => {
        it('should return readonly array of providers', () => {
            const p1 = new BundledSchemaProvider([], true, logger);
            const p2 = new BundledSchemaProvider([], true, logger);
            const provider = new CompositeSchemaProvider([p1, p2], logger);

            const providers = provider.getProviders();

            assert.strictEqual(providers.length, 2);
            assert.strictEqual(providers[0], p1);
            assert.strictEqual(providers[1], p2);
        });
    });

    describe('integration with NoOpSchemaProvider', () => {
        it('should work with NoOpSchemaProvider as fallback', () => {
            const bundled = new BundledSchemaProvider([], true, logger);
            const noOp = new NoOpSchemaProvider();

            const provider = new CompositeSchemaProvider([bundled, noOp], logger);

            // Should return undefined when bundled has no schema
            const schema = provider.getSchemaForDocument('file:///test.kson');
            assert.strictEqual(schema, undefined);
        });

        it('should return undefined for metaschema with NoOpSchemaProvider', () => {
            const noOp = new NoOpSchemaProvider();
            const provider = new CompositeSchemaProvider([noOp], logger);

            const result = provider.getMetaSchemaForId('http://json-schema.org/draft-07/schema#');
            assert.strictEqual(result, undefined);
        });
    });
});
