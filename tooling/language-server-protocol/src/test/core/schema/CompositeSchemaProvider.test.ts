import {describe, it} from 'mocha';
import * as assert from 'assert';
import {TextDocument} from 'vscode-languageserver-textdocument';
import {DocumentUri} from 'vscode-languageserver';
import {CompositeSchemaProvider} from '../../../core/schema/CompositeSchemaProvider';
import {SchemaProvider, NoOpSchemaProvider} from '../../../core/schema/SchemaProvider';

/**
 * Mock schema provider that returns a predefined schema for specific URIs.
 */
class MockSchemaProvider implements SchemaProvider {
    private schemas: Map<string, TextDocument> = new Map();
    private schemaFiles: Set<string> = new Set();

    addSchema(documentUri: string, schema: TextDocument): void {
        this.schemas.set(documentUri, schema);
    }

    addSchemaByLanguageId(languageId: string, schema: TextDocument): void {
        this.schemas.set(`lang:${languageId}`, schema);
    }

    markAsSchemaFile(uri: string): void {
        this.schemaFiles.add(uri);
    }

    getSchemaForDocument(documentUri: DocumentUri, languageId?: string): TextDocument | undefined {
        // Check by languageId first
        if (languageId) {
            const langSchema = this.schemas.get(`lang:${languageId}`);
            if (langSchema) return langSchema;
        }
        // Then check by URI
        return this.schemas.get(documentUri);
    }

    reload(): void {
        // No-op for mock
    }

    isSchemaFile(fileUri: DocumentUri): boolean {
        return this.schemaFiles.has(fileUri);
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

    function createSchema(uri: string, content: string): TextDocument {
        return TextDocument.create(uri, 'kson', 1, content);
    }

    describe('constructor', () => {
        it('should create provider with no providers', () => {
            const provider = new CompositeSchemaProvider([], logger);
            assert.ok(provider);
            assert.strictEqual(provider.getProviders().length, 0);
        });

        it('should create provider with multiple providers', () => {
            const mock1 = new MockSchemaProvider();
            const mock2 = new MockSchemaProvider();
            const provider = new CompositeSchemaProvider([mock1, mock2], logger);

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
            const mock1 = new MockSchemaProvider();
            const mock2 = new MockSchemaProvider();
            const provider = new CompositeSchemaProvider([mock1, mock2], logger);

            const schema = provider.getSchemaForDocument('file:///test.kson');
            assert.strictEqual(schema, undefined);
        });

        it('should return schema from first matching provider', () => {
            const mock1 = new MockSchemaProvider();
            const mock2 = new MockSchemaProvider();

            const schema1 = createSchema('file:///schema1.kson', '{ "from": "first" }');
            mock1.addSchema('file:///test.kson', schema1);

            const schema2 = createSchema('file:///schema2.kson', '{ "from": "second" }');
            mock2.addSchema('file:///test.kson', schema2);

            const provider = new CompositeSchemaProvider([mock1, mock2], logger);
            const result = provider.getSchemaForDocument('file:///test.kson');

            assert.ok(result);
            assert.strictEqual(result!.getText(), '{ "from": "first" }');
        });

        it('should try next provider when first returns undefined', () => {
            const mock1 = new MockSchemaProvider();
            const mock2 = new MockSchemaProvider();

            // mock1 has no schema for test.kson
            const schema2 = createSchema('file:///schema2.kson', '{ "from": "second" }');
            mock2.addSchema('file:///test.kson', schema2);

            const provider = new CompositeSchemaProvider([mock1, mock2], logger);
            const result = provider.getSchemaForDocument('file:///test.kson');

            assert.ok(result);
            assert.strictEqual(result!.getText(), '{ "from": "second" }');
        });

        it('should pass languageId to providers', () => {
            const mock1 = new MockSchemaProvider();
            const mock2 = new MockSchemaProvider();

            const schema = createSchema('bundled://schema/test-lang', '{ "type": "object" }');
            mock2.addSchemaByLanguageId('test-lang', schema);

            const provider = new CompositeSchemaProvider([mock1, mock2], logger);
            const result = provider.getSchemaForDocument('file:///test.kson', 'test-lang');

            assert.ok(result);
            assert.strictEqual(result!.uri, 'bundled://schema/test-lang');
        });

        it('should prioritize file system provider over bundled (typical usage)', () => {
            // Simulate file system provider (has schema by URI)
            const fileSystemProvider = new MockSchemaProvider();
            const fsSchema = createSchema('file:///workspace/schema.kson', '{ "from": "filesystem" }');
            fileSystemProvider.addSchema('file:///test.kson', fsSchema);

            // Simulate bundled provider (has schema by languageId)
            const bundledProvider = new MockSchemaProvider();
            const bundledSchema = createSchema('bundled://schema/kson', '{ "from": "bundled" }');
            bundledProvider.addSchemaByLanguageId('kson', bundledSchema);

            // File system first (higher priority)
            const provider = new CompositeSchemaProvider([fileSystemProvider, bundledProvider], logger);
            const result = provider.getSchemaForDocument('file:///test.kson', 'kson');

            assert.ok(result);
            assert.strictEqual(result!.getText(), '{ "from": "filesystem" }');
        });

        it('should fall back to bundled when file system has no schema', () => {
            // File system provider with no schema
            const fileSystemProvider = new MockSchemaProvider();

            // Bundled provider has schema
            const bundledProvider = new MockSchemaProvider();
            const bundledSchema = createSchema('bundled://schema/kson', '{ "from": "bundled" }');
            bundledProvider.addSchemaByLanguageId('kson', bundledSchema);

            const provider = new CompositeSchemaProvider([fileSystemProvider, bundledProvider], logger);
            const result = provider.getSchemaForDocument('file:///test.kson', 'kson');

            assert.ok(result);
            assert.strictEqual(result!.getText(), '{ "from": "bundled" }');
        });
    });

    describe('isSchemaFile', () => {
        it('should return false when no providers', () => {
            const provider = new CompositeSchemaProvider([], logger);
            assert.strictEqual(provider.isSchemaFile('file:///schema.kson'), false);
        });

        it('should return true when any provider considers it a schema file', () => {
            const mock1 = new MockSchemaProvider();
            const mock2 = new MockSchemaProvider();

            mock2.markAsSchemaFile('file:///schema.kson');

            const provider = new CompositeSchemaProvider([mock1, mock2], logger);
            assert.strictEqual(provider.isSchemaFile('file:///schema.kson'), true);
        });

        it('should return false when no provider considers it a schema file', () => {
            const mock1 = new MockSchemaProvider();
            const mock2 = new MockSchemaProvider();

            const provider = new CompositeSchemaProvider([mock1, mock2], logger);
            assert.strictEqual(provider.isSchemaFile('file:///random.kson'), false);
        });

        it('should check all provider types', () => {
            const mock1 = new MockSchemaProvider();
            mock1.markAsSchemaFile('file:///schema1.kson');

            const mock2 = new MockSchemaProvider();
            mock2.markAsSchemaFile('bundled://schema/lang');

            const provider = new CompositeSchemaProvider([mock1, mock2], logger);

            assert.strictEqual(provider.isSchemaFile('file:///schema1.kson'), true);
            assert.strictEqual(provider.isSchemaFile('bundled://schema/lang'), true);
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
            const mock1 = new MockSchemaProvider();
            const mock2 = new MockSchemaProvider();
            const provider = new CompositeSchemaProvider([mock1, mock2], logger);

            const providers = provider.getProviders();

            assert.strictEqual(providers.length, 2);
            assert.strictEqual(providers[0], mock1);
            assert.strictEqual(providers[1], mock2);
        });
    });

    describe('integration with NoOpSchemaProvider', () => {
        it('should work with NoOpSchemaProvider as fallback', () => {
            const mock = new MockSchemaProvider();
            const noOp = new NoOpSchemaProvider();

            const provider = new CompositeSchemaProvider([mock, noOp], logger);

            // Should return undefined when mock has no schema
            const schema = provider.getSchemaForDocument('file:///test.kson');
            assert.strictEqual(schema, undefined);
        });
    });
});
