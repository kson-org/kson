import {describe, it} from 'mocha';
import * as assert from 'assert';
import {BundledSchemaProvider, BundledSchemaConfig} from '../../../core/schema/BundledSchemaProvider';

describe('BundledSchemaProvider', () => {
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
        it('should create provider with no schemas', () => {
            const provider = new BundledSchemaProvider([], true, logger);
            assert.ok(provider);
            assert.strictEqual(provider.getAvailableLanguageIds().length, 0);
            assert.ok(logs.some(msg => msg.includes('initialized with 0 schemas')));
        });

        it('should create provider with schemas', () => {
            const schemas: BundledSchemaConfig[] = [
                { languageId: 'test-lang', schemaContent: '{ "type": "object" }' }
            ];
            const provider = new BundledSchemaProvider(schemas, true, logger);

            assert.ok(provider);
            assert.strictEqual(provider.getAvailableLanguageIds().length, 1);
            assert.ok(provider.hasBundledSchema('test-lang'));
            assert.ok(logs.some(msg => msg.includes('Loaded bundled schema for language: test-lang')));
        });

        it('should create provider with multiple schemas', () => {
            const schemas: BundledSchemaConfig[] = [
                { languageId: 'lang-a', schemaContent: '{ "type": "object" }' },
                { languageId: 'lang-b', schemaContent: '{ "type": "array" }' }
            ];
            const provider = new BundledSchemaProvider(schemas, true, logger);

            assert.strictEqual(provider.getAvailableLanguageIds().length, 2);
            assert.ok(provider.hasBundledSchema('lang-a'));
            assert.ok(provider.hasBundledSchema('lang-b'));
        });

        it('should respect enabled flag', () => {
            const schemas: BundledSchemaConfig[] = [
                { languageId: 'test-lang', schemaContent: '{ "type": "object" }' }
            ];
            const provider = new BundledSchemaProvider(schemas, false, logger);

            assert.strictEqual(provider.isEnabled(), false);
            assert.ok(logs.some(msg => msg.includes('enabled: false')));
        });
    });

    describe('getSchemaForDocument', () => {
        it('should return undefined when disabled', () => {
            const schemas: BundledSchemaConfig[] = [
                { languageId: 'test-lang', schemaContent: '{ "type": "object" }' }
            ];
            const provider = new BundledSchemaProvider(schemas, false, logger);

            const schema = provider.getSchemaForDocument('file:///test.kson', 'test-lang');
            assert.strictEqual(schema, undefined);
        });

        it('should return undefined when no languageId provided', () => {
            const schemas: BundledSchemaConfig[] = [
                { languageId: 'test-lang', schemaContent: '{ "type": "object" }' }
            ];
            const provider = new BundledSchemaProvider(schemas, true, logger);

            const schema = provider.getSchemaForDocument('file:///test.kson');
            assert.strictEqual(schema, undefined);
        });

        it('should return undefined for unknown languageId', () => {
            const schemas: BundledSchemaConfig[] = [
                { languageId: 'test-lang', schemaContent: '{ "type": "object" }' }
            ];
            const provider = new BundledSchemaProvider(schemas, true, logger);

            const schema = provider.getSchemaForDocument('file:///test.kson', 'unknown-lang');
            assert.strictEqual(schema, undefined);
        });

        it('should return schema for matching languageId', () => {
            const schemaContent = '{ "type": "object" }';
            const schemas: BundledSchemaConfig[] = [
                { languageId: 'test-lang', schemaContent }
            ];
            const provider = new BundledSchemaProvider(schemas, true, logger);

            const schema = provider.getSchemaForDocument('file:///test.kson', 'test-lang');
            assert.ok(schema);
            assert.strictEqual(schema!.getText(), schemaContent);
            assert.strictEqual(schema!.uri, 'bundled://schema/test-lang');
        });

        it('should ignore documentUri and use languageId', () => {
            const schemaContent = '{ "type": "object" }';
            const schemas: BundledSchemaConfig[] = [
                { languageId: 'test-lang', schemaContent }
            ];
            const provider = new BundledSchemaProvider(schemas, true, logger);

            // Different document URIs should return same schema for same languageId
            const schema1 = provider.getSchemaForDocument('file:///a.kson', 'test-lang');
            const schema2 = provider.getSchemaForDocument('file:///b.kson', 'test-lang');

            assert.ok(schema1);
            assert.ok(schema2);
            assert.strictEqual(schema1!.uri, schema2!.uri);
        });
    });

    describe('isSchemaFile', () => {
        it('should return true for bundled schema URIs', () => {
            const provider = new BundledSchemaProvider([], true, logger);

            assert.strictEqual(provider.isSchemaFile('bundled://schema/test-lang'), true);
            assert.strictEqual(provider.isSchemaFile('bundled://schema/other'), true);
        });

        it('should return false for non-bundled URIs', () => {
            const provider = new BundledSchemaProvider([], true, logger);

            assert.strictEqual(provider.isSchemaFile('file:///test.kson'), false);
            assert.strictEqual(provider.isSchemaFile('untitled:///test.kson'), false);
        });
    });

    describe('setEnabled', () => {
        it('should toggle enabled state', () => {
            const schemas: BundledSchemaConfig[] = [
                { languageId: 'test-lang', schemaContent: '{ "type": "object" }' }
            ];
            const provider = new BundledSchemaProvider(schemas, true, logger);

            assert.strictEqual(provider.isEnabled(), true);

            provider.setEnabled(false);
            assert.strictEqual(provider.isEnabled(), false);

            // Should not return schema when disabled
            const schema = provider.getSchemaForDocument('file:///test.kson', 'test-lang');
            assert.strictEqual(schema, undefined);

            provider.setEnabled(true);
            assert.strictEqual(provider.isEnabled(), true);

            // Should return schema when re-enabled
            const schema2 = provider.getSchemaForDocument('file:///test.kson', 'test-lang');
            assert.ok(schema2);
        });
    });

    describe('reload', () => {
        it('should be a no-op (bundled schemas are immutable)', () => {
            const schemas: BundledSchemaConfig[] = [
                { languageId: 'test-lang', schemaContent: '{ "type": "object" }' }
            ];
            const provider = new BundledSchemaProvider(schemas, true, logger);

            // reload should not throw or change anything
            provider.reload();

            assert.strictEqual(provider.getAvailableLanguageIds().length, 1);
            assert.ok(provider.hasBundledSchema('test-lang'));
        });
    });

    describe('hasBundledSchema', () => {
        it('should return true for configured languages', () => {
            const schemas: BundledSchemaConfig[] = [
                { languageId: 'lang-a', schemaContent: '{}' },
                { languageId: 'lang-b', schemaContent: '{}' }
            ];
            const provider = new BundledSchemaProvider(schemas, true, logger);

            assert.strictEqual(provider.hasBundledSchema('lang-a'), true);
            assert.strictEqual(provider.hasBundledSchema('lang-b'), true);
            assert.strictEqual(provider.hasBundledSchema('lang-c'), false);
        });
    });

    describe('getAvailableLanguageIds', () => {
        it('should return all configured language IDs', () => {
            const schemas: BundledSchemaConfig[] = [
                { languageId: 'alpha', schemaContent: '{}' },
                { languageId: 'beta', schemaContent: '{}' },
                { languageId: 'gamma', schemaContent: '{}' }
            ];
            const provider = new BundledSchemaProvider(schemas, true, logger);

            const ids = provider.getAvailableLanguageIds();
            assert.strictEqual(ids.length, 3);
            assert.ok(ids.includes('alpha'));
            assert.ok(ids.includes('beta'));
            assert.ok(ids.includes('gamma'));
        });
    });
});
