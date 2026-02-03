import {describe, it} from 'mocha';
import * as assert from 'assert';
import {BundledSchemaProvider, BundledSchemaConfig, BundledMetaSchemaConfig} from '../../../core/schema/BundledSchemaProvider';

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
            assert.strictEqual(provider.getAvailableFileExtensions().length, 0);
            assert.ok(logs.some(msg => msg.includes('initialized with 0 schemas and 0 metaschemas')));
        });

        it('should create provider with schemas', () => {
            const schemas: BundledSchemaConfig[] = [
                { fileExtension: 'kxt', schemaContent: '{ "type": "object" }' }
            ];
            const provider = new BundledSchemaProvider(schemas, true, logger);

            assert.ok(provider);
            assert.strictEqual(provider.getAvailableFileExtensions().length, 1);
            assert.ok(provider.hasBundledSchema('kxt'));
            assert.ok(logs.some(msg => msg.includes('Loaded bundled schema for extension: kxt')));
        });

        it('should create provider with multiple schemas', () => {
            const schemas: BundledSchemaConfig[] = [
                { fileExtension: 'ext-a', schemaContent: '{ "type": "object" }' },
                { fileExtension: 'ext-b', schemaContent: '{ "type": "array" }' }
            ];
            const provider = new BundledSchemaProvider(schemas, true, logger);

            assert.strictEqual(provider.getAvailableFileExtensions().length, 2);
            assert.ok(provider.hasBundledSchema('ext-a'));
            assert.ok(provider.hasBundledSchema('ext-b'));
        });

        it('should create provider with metaschemas', () => {
            const metaSchemas: BundledMetaSchemaConfig[] = [
                { schemaId: 'http://json-schema.org/draft-07/schema#', name: 'draft-07', schemaContent: '{ "type": "object" }' }
            ];
            const provider = new BundledSchemaProvider([], true, logger, metaSchemas);

            assert.ok(provider);
            assert.ok(logs.some(msg => msg.includes('Loaded bundled metaschema: draft-07')));
            assert.ok(logs.some(msg => msg.includes('1 metaschemas')));
        });

        it('should respect enabled flag', () => {
            const schemas: BundledSchemaConfig[] = [
                { fileExtension: 'kxt', schemaContent: '{ "type": "object" }' }
            ];
            const provider = new BundledSchemaProvider(schemas, false, logger);

            assert.strictEqual(provider.isEnabled(), false);
            assert.ok(logs.some(msg => msg.includes('enabled: false')));
        });
    });

    describe('getSchemaForDocument', () => {
        it('should return undefined when disabled', () => {
            const schemas: BundledSchemaConfig[] = [
                { fileExtension: 'kxt', schemaContent: '{ "type": "object" }' }
            ];
            const provider = new BundledSchemaProvider(schemas, false, logger);

            const schema = provider.getSchemaForDocument('file:///test.kxt');
            assert.strictEqual(schema, undefined);
        });

        it('should return undefined when no file extension in URI', () => {
            const schemas: BundledSchemaConfig[] = [
                { fileExtension: 'kxt', schemaContent: '{ "type": "object" }' }
            ];
            const provider = new BundledSchemaProvider(schemas, true, logger);

            const schema = provider.getSchemaForDocument('file:///test');
            assert.strictEqual(schema, undefined);
        });

        it('should return undefined for unknown file extension', () => {
            const schemas: BundledSchemaConfig[] = [
                { fileExtension: 'kxt', schemaContent: '{ "type": "object" }' }
            ];
            const provider = new BundledSchemaProvider(schemas, true, logger);

            const schema = provider.getSchemaForDocument('file:///test.unknown');
            assert.strictEqual(schema, undefined);
        });

        it('should return schema for matching file extension', () => {
            const schemaContent = '{ "type": "object" }';
            const schemas: BundledSchemaConfig[] = [
                { fileExtension: 'kxt', schemaContent }
            ];
            const provider = new BundledSchemaProvider(schemas, true, logger);

            const schema = provider.getSchemaForDocument('file:///test.kxt');
            assert.ok(schema);
            assert.strictEqual(schema!.getText(), schemaContent);
            assert.strictEqual(schema!.uri, 'bundled://schema/kxt.schema.kson');
        });

        it('should return same schema for different paths with same extension', () => {
            const schemaContent = '{ "type": "object" }';
            const schemas: BundledSchemaConfig[] = [
                { fileExtension: 'kxt', schemaContent }
            ];
            const provider = new BundledSchemaProvider(schemas, true, logger);

            const schema1 = provider.getSchemaForDocument('file:///a.kxt');
            const schema2 = provider.getSchemaForDocument('file:///b.kxt');

            assert.ok(schema1);
            assert.ok(schema2);
            assert.strictEqual(schema1!.uri, schema2!.uri);
        });

        it('should match multi-dot extensions correctly', () => {
            const ksonSchema = '{ "type": "kson" }';
            const orchestraSchema = '{ "type": "orchestra" }';
            const schemas: BundledSchemaConfig[] = [
                { fileExtension: 'kson', schemaContent: ksonSchema },
                { fileExtension: 'orchestra.kson', schemaContent: orchestraSchema }
            ];
            const provider = new BundledSchemaProvider(schemas, true, logger);

            // Simple .kson file should match 'kson' extension
            const simpleSchema = provider.getSchemaForDocument('file:///test.kson');
            assert.ok(simpleSchema);
            assert.strictEqual(simpleSchema!.getText(), ksonSchema);

            // Multi-dot .orchestra.kson file should match the longer 'orchestra.kson' extension
            const orchestraResult = provider.getSchemaForDocument('file:///my-config.orchestra.kson');
            assert.ok(orchestraResult);
            assert.strictEqual(orchestraResult!.getText(), orchestraSchema);
        });

        it('should prefer longer extension when multiple match', () => {
            const schemas: BundledSchemaConfig[] = [
                { fileExtension: 'kson', schemaContent: '{ "short": true }' },
                { fileExtension: 'config.kson', schemaContent: '{ "long": true }' }
            ];
            const provider = new BundledSchemaProvider(schemas, true, logger);

            // File ending in .config.kson should match the longer extension
            const schema = provider.getSchemaForDocument('file:///app.config.kson');
            assert.ok(schema);
            assert.strictEqual(schema!.getText(), '{ "long": true }');
        });
    });

    describe('getMetaSchemaForId', () => {
        it('should return metaschema when ID matches', () => {
            const metaSchemaContent = '{ "$id": "http://json-schema.org/draft-07/schema#" }';
            const metaSchemas: BundledMetaSchemaConfig[] = [
                { schemaId: 'http://json-schema.org/draft-07/schema#', name: 'draft-07', schemaContent: metaSchemaContent }
            ];
            const provider = new BundledSchemaProvider([], true, logger, metaSchemas);

            const result = provider.getMetaSchemaForId('http://json-schema.org/draft-07/schema#');
            assert.ok(result);
            assert.strictEqual(result!.getText(), metaSchemaContent);
            assert.strictEqual(result!.uri, 'bundled://metaschema/draft-07.schema.kson');
        });

        it('should return undefined when no matching ID', () => {
            const metaSchemas: BundledMetaSchemaConfig[] = [
                { schemaId: 'http://json-schema.org/draft-07/schema#', name: 'draft-07', schemaContent: '{}' }
            ];
            const provider = new BundledSchemaProvider([], true, logger, metaSchemas);

            const result = provider.getMetaSchemaForId('http://json-schema.org/draft-04/schema#');
            assert.strictEqual(result, undefined);
        });

        it('should return undefined when disabled', () => {
            const metaSchemas: BundledMetaSchemaConfig[] = [
                { schemaId: 'http://json-schema.org/draft-07/schema#', name: 'draft-07', schemaContent: '{}' }
            ];
            const provider = new BundledSchemaProvider([], false, logger, metaSchemas);

            const result = provider.getMetaSchemaForId('http://json-schema.org/draft-07/schema#');
            assert.strictEqual(result, undefined);
        });

        it('should return undefined when no metaschemas configured', () => {
            const provider = new BundledSchemaProvider([], true, logger);

            const result = provider.getMetaSchemaForId('http://json-schema.org/draft-07/schema#');
            assert.strictEqual(result, undefined);
        });
    });

    describe('isSchemaFile', () => {
        it('should return true for bundled schema URIs', () => {
            const provider = new BundledSchemaProvider([], true, logger);

            assert.strictEqual(provider.isSchemaFile('bundled://schema/test-lang.schema.kson'), true);
            assert.strictEqual(provider.isSchemaFile('bundled://schema/other.schema.kson'), true);
        });

        it('should return true for bundled metaschema URIs', () => {
            const provider = new BundledSchemaProvider([], true, logger);

            assert.strictEqual(provider.isSchemaFile('bundled://metaschema/draft-07.schema.kson'), true);
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
                { fileExtension: 'kxt', schemaContent: '{ "type": "object" }' }
            ];
            const provider = new BundledSchemaProvider(schemas, true, logger);

            assert.strictEqual(provider.isEnabled(), true);

            provider.setEnabled(false);
            assert.strictEqual(provider.isEnabled(), false);

            // Should not return schema when disabled
            const schema = provider.getSchemaForDocument('file:///test.kxt');
            assert.strictEqual(schema, undefined);

            provider.setEnabled(true);
            assert.strictEqual(provider.isEnabled(), true);

            // Should return schema when re-enabled
            const schema2 = provider.getSchemaForDocument('file:///test.kxt');
            assert.ok(schema2);
        });

        it('should toggle metaschema availability', () => {
            const metaSchemas: BundledMetaSchemaConfig[] = [
                { schemaId: 'http://json-schema.org/draft-07/schema#', name: 'draft-07', schemaContent: '{}' }
            ];
            const provider = new BundledSchemaProvider([], true, logger, metaSchemas);

            assert.ok(provider.getMetaSchemaForId('http://json-schema.org/draft-07/schema#'));

            provider.setEnabled(false);
            assert.strictEqual(provider.getMetaSchemaForId('http://json-schema.org/draft-07/schema#'), undefined);

            provider.setEnabled(true);
            assert.ok(provider.getMetaSchemaForId('http://json-schema.org/draft-07/schema#'));
        });
    });

    describe('reload', () => {
        it('should be a no-op (bundled schemas are immutable)', () => {
            const schemas: BundledSchemaConfig[] = [
                { fileExtension: 'kxt', schemaContent: '{ "type": "object" }' }
            ];
            const provider = new BundledSchemaProvider(schemas, true, logger);

            // reload should not throw or change anything
            provider.reload();

            assert.strictEqual(provider.getAvailableFileExtensions().length, 1);
            assert.ok(provider.hasBundledSchema('kxt'));
        });
    });

    describe('hasBundledSchema', () => {
        it('should return true for configured extensions', () => {
            const schemas: BundledSchemaConfig[] = [
                { fileExtension: 'ext-a', schemaContent: '{}' },
                { fileExtension: 'ext-b', schemaContent: '{}' }
            ];
            const provider = new BundledSchemaProvider(schemas, true, logger);

            assert.strictEqual(provider.hasBundledSchema('ext-a'), true);
            assert.strictEqual(provider.hasBundledSchema('ext-b'), true);
            assert.strictEqual(provider.hasBundledSchema('ext-c'), false);
        });
    });

    describe('getAvailableFileExtensions', () => {
        it('should return all configured file extensions', () => {
            const schemas: BundledSchemaConfig[] = [
                { fileExtension: 'alpha', schemaContent: '{}' },
                { fileExtension: 'beta', schemaContent: '{}' },
                { fileExtension: 'gamma', schemaContent: '{}' }
            ];
            const provider = new BundledSchemaProvider(schemas, true, logger);

            const extensions = provider.getAvailableFileExtensions();
            assert.strictEqual(extensions.length, 3);
            assert.ok(extensions.includes('alpha'));
            assert.ok(extensions.includes('beta'));
            assert.ok(extensions.includes('gamma'));
        });
    });
});
