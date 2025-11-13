import {describe, it, beforeEach, afterEach} from 'mocha';
import * as assert from 'assert';
import {FileSystemSchemaProvider} from '../../../core/schema/FileSystemSchemaProvider';
import {SchemaConfig} from '../../../core/schema/SchemaConfig.js';
import * as fs from 'fs';
import * as path from 'path';
import * as os from 'os';

describe('FileSystemSchemaProvider', () => {
    let tempDir: string | null = null;
    let logs: string[] = [];

    const logger = {
        info: (msg: string) => logs.push(`INFO: ${msg}`),
        warn: (msg: string) => logs.push(`WARN: ${msg}`),
        error: (msg: string) => logs.push(`ERROR: ${msg}`)
    };

    beforeEach(() => {
        logs = [];
    });

    afterEach(() => {
        if (tempDir && fs.existsSync(tempDir)) {
            fs.rmSync(tempDir, {recursive: true, force: true});
            tempDir = null;
        }
    });

    function createWorkspace(): string {
        tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'kson-test-'));
        return tempDir;
    }

    function writeConfig(workspaceDir: string, config: SchemaConfig): void {
        fs.writeFileSync(
            path.join(workspaceDir, '.kson-schema.kson'),
            JSON.stringify(config),
            'utf-8'
        );
    }

    function writeSchema(workspaceDir: string, schemaPath: string, content: string): void {
        const fullPath = path.join(workspaceDir, schemaPath);
        fs.mkdirSync(path.dirname(fullPath), {recursive: true});
        fs.writeFileSync(fullPath, content, 'utf-8');
    }

    describe('constructor', () => {
        it('should create provider without workspace', () => {
            const provider = new FileSystemSchemaProvider(null, logger);
            assert.ok(provider);
            assert.ok(logs.some(msg => msg.includes('No workspace root available')));
        });

        it('should create provider with workspace but no config', () => {
            const workspace = createWorkspace();
            const provider = new FileSystemSchemaProvider(`file://${workspace}`, logger);
            assert.ok(provider);
            assert.ok(logs.some(msg => msg.includes('No .kson-schema.kson found')));
        });

        it('should load config when present', () => {
            const workspace = createWorkspace();
            writeConfig(workspace, {schemas: [{fileMatch: ['*.kson'], schema: 'test.json'}]});

            new FileSystemSchemaProvider(`file://${workspace}`, logger);

            assert.ok(logs.some(msg => msg.includes('Loaded schema configuration with 1 mappings')));
        });

        it('should handle invalid config format', () => {
            const workspace = createWorkspace();
            fs.writeFileSync(path.join(workspace, '.kson-schema.kson'), 'invalid json', 'utf-8');

            new FileSystemSchemaProvider(`file://${workspace}`, logger);

            assert.ok(logs.some(msg => msg.includes('Failed to load .kson-schema.kson')));
        });
    });

    describe('getSchemaForDocument', () => {
        it('should return undefined when no config', () => {
            const workspace = createWorkspace();
            const provider = new FileSystemSchemaProvider(`file://${workspace}`, logger);

            const schema = provider.getSchemaForDocument(`file://${workspace}/test.kson`);

            assert.strictEqual(schema, undefined);
        });

        it('should return schema for matching file', () => {
            const workspace = createWorkspace();
            const schemaContent = JSON.stringify({type: 'object'});

            writeConfig(workspace, {
                schemas: [{fileMatch: ['*.kson'], schema: 'schemas/test.json'}]
            });
            writeSchema(workspace, 'schemas/test.json', schemaContent);

            const provider = new FileSystemSchemaProvider(`file://${workspace}`, logger);
            const schema = provider.getSchemaForDocument(`file://${workspace}/test.kson`);

            assert.ok(schema);
            assert.strictEqual(schema!.getText(), schemaContent);
        });

        it('should return undefined for non-matching file', () => {
            const workspace = createWorkspace();

            writeConfig(workspace, {
                schemas: [{fileMatch: ['config/*.kson'], schema: 'schemas/config.json'}]
            });

            const provider = new FileSystemSchemaProvider(`file://${workspace}`, logger);
            const schema = provider.getSchemaForDocument(`file://${workspace}/other.kson`);

            assert.strictEqual(schema, undefined);
        });

        it('should handle missing schema file', () => {
            const workspace = createWorkspace();

            writeConfig(workspace, {
                schemas: [{fileMatch: ['*.kson'], schema: 'schemas/missing.json'}]
            });

            const provider = new FileSystemSchemaProvider(`file://${workspace}`, logger);
            const schema = provider.getSchemaForDocument(`file://${workspace}/test.kson`);

            assert.strictEqual(schema, undefined);
            assert.ok(logs.some(msg => msg.includes('Schema file not found')));
        });

        it('should support glob patterns', () => {
            const workspace = createWorkspace();
            const schemaContent = JSON.stringify({type: 'object'});

            writeConfig(workspace, {
                schemas: [{fileMatch: ['config/**/*.kson'], schema: 'schemas/config.json'}]
            });
            writeSchema(workspace, 'schemas/config.json', schemaContent);

            const provider = new FileSystemSchemaProvider(`file://${workspace}`, logger);
            const schema = provider.getSchemaForDocument(`file://${workspace}/config/deep/test.kson`);

            assert.ok(schema);
        });

        it('should use first matching schema', () => {
            const workspace = createWorkspace();
            const schema1 = JSON.stringify({title: 'schema1'});
            const schema2 = JSON.stringify({title: 'schema2'});

            writeConfig(workspace, {
                schemas: [
                    {fileMatch: ['*.kson'], schema: 'schemas/first.json'},
                    {fileMatch: ['*.kson'], schema: 'schemas/second.json'}
                ]
            });
            writeSchema(workspace, 'schemas/first.json', schema1);
            writeSchema(workspace, 'schemas/second.json', schema2);

            const provider = new FileSystemSchemaProvider(`file://${workspace}`, logger);
            const schema = provider.getSchemaForDocument(`file://${workspace}/test.kson`);

            assert.ok(schema);
            assert.strictEqual(schema!.getText(), schema1);
        });

        it('should convert KSON schema files to JSON', () => {
            const workspace = createWorkspace();
            const ksonSchema = 'type: "object"';

            writeConfig(workspace, {
                schemas: [{fileMatch: ['*.kson'], schema: 'schemas/test.kson'}]
            });
            writeSchema(workspace, 'schemas/test.kson', ksonSchema);

            const provider = new FileSystemSchemaProvider(`file://${workspace}`, logger);
            const schema = provider.getSchemaForDocument(`file://${workspace}/test.kson`);

            assert.ok(schema);
            const parsed = JSON.parse(schema!.getText());
            assert.strictEqual(parsed.type, 'object');
        });
    });

    describe('reload', () => {
        it('should reload configuration from disk', () => {
            const workspace = createWorkspace();
            writeConfig(workspace, {
                schemas: [{fileMatch: ['*.kson'], schema: 'schemas/test.json'}]
            });

            const provider = new FileSystemSchemaProvider(`file://${workspace}`, logger);
            logs = [];

            // Update config
            writeConfig(workspace, {
                schemas: [
                    {fileMatch: ['*.kson'], schema: 'schemas/test1.json'},
                    {fileMatch: ['*.json'], schema: 'schemas/test2.json'}
                ]
            });

            provider.reload();

            assert.ok(logs.some(msg => msg.includes('Loaded schema configuration with 2 mappings')));
        });

        it('should return updated schema after reload', () => {
            const workspace = createWorkspace();
            const originalSchema = JSON.stringify({title: 'original'});
            const updatedSchema = JSON.stringify({title: 'updated'});

            writeConfig(workspace, {
                schemas: [{fileMatch: ['*.kson'], schema: 'schemas/test.json'}]
            });
            writeSchema(workspace, 'schemas/test.json', originalSchema);

            const provider = new FileSystemSchemaProvider(`file://${workspace}`, logger);

            // Get schema before update
            const schemaBefore = provider.getSchemaForDocument(`file://${workspace}/test.kson`);
            assert.ok(schemaBefore);
            assert.strictEqual(schemaBefore!.getText(), originalSchema);

            // Update schema file
            writeSchema(workspace, 'schemas/test.json', updatedSchema);
            provider.reload();

            // Get schema after reload
            const schemaAfter = provider.getSchemaForDocument(`file://${workspace}/test.kson`);
            assert.ok(schemaAfter);
            assert.strictEqual(schemaAfter!.getText(), updatedSchema);
        });

        it('should pick up new schema mappings after config change', () => {
            const workspace = createWorkspace();

            writeConfig(workspace, {
                schemas: [{fileMatch: ['config/*.kson'], schema: 'schemas/config.json'}]
            });
            writeSchema(workspace, 'schemas/config.json', JSON.stringify({type: 'config'}));

            const provider = new FileSystemSchemaProvider(`file://${workspace}`, logger);

            // Should not match initially
            const schemaBefore = provider.getSchemaForDocument(`file://${workspace}/data.kson`);
            assert.strictEqual(schemaBefore, undefined);

            // Add new mapping
            writeConfig(workspace, {
                schemas: [
                    {fileMatch: ['config/*.kson'], schema: 'schemas/config.json'},
                    {fileMatch: ['*.kson'], schema: 'schemas/data.json'}
                ]
            });
            writeSchema(workspace, 'schemas/data.json', JSON.stringify({type: 'data'}));
            provider.reload();

            // Should match after reload
            const schemaAfter = provider.getSchemaForDocument(`file://${workspace}/data.kson`);
            assert.ok(schemaAfter);
            const parsed = JSON.parse(schemaAfter!.getText());
            assert.strictEqual(parsed.type, 'data');
        });
    });
});