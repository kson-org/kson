import {describe, it, beforeEach, afterEach} from 'mocha';
import * as assert from 'assert';
import {FileSystemSchemaProvider} from '../../../core/schema/FileSystemSchemaProvider';
import {SchemaConfig} from '../../../core/schema/SchemaConfig.js';
import * as fs from 'fs';
import * as path from 'path';
import * as os from 'os';
import {URI} from "vscode-uri";

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

    function createWorkspace(): URI {
        tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'kson-test-'));
        return URI.file(tempDir)
    }

    function writeConfig(workspaceUri: URI, config: SchemaConfig): void {
        const workspacePath = workspaceUri.fsPath
        fs.writeFileSync(
            path.join(workspacePath, '.kson-schema.kson'),
            JSON.stringify(config),
            'utf-8'
        );
    }

    function writeSchema(workspaceUri: URI, schemaPath: string, content: string): void {
        const workspacePath = workspaceUri.fsPath
        const fullPath = path.join(workspacePath, schemaPath);
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
            const provider = new FileSystemSchemaProvider(workspace, logger);
            assert.ok(provider);
            assert.ok(logs.some(msg => msg.includes('No .kson-schema.kson found')));
        });

        it('should load config when present', () => {
            const workspace = createWorkspace();
            writeConfig(workspace, {schemas: [{fileMatch: ['*.kson'], schema: 'test.json'}]});

            new FileSystemSchemaProvider(workspace, logger);

            assert.ok(logs.some(msg => msg.includes('Loaded schema configuration with 1 mappings')));
        });

        it('should handle invalid config format', () => {
            const workspace = createWorkspace();
            fs.writeFileSync(path.join(workspace.fsPath, '.kson-schema.kson'), 'invalid json', 'utf-8');

            new FileSystemSchemaProvider(workspace, logger);

            assert.ok(logs.some(msg => msg.includes('Failed to load .kson-schema.kson')));
        });
    });

    describe('getSchemaForDocument', () => {
        it('should return undefined when no config', () => {
            const workspace = createWorkspace();
            const provider = new FileSystemSchemaProvider(workspace, logger);

            const schema = provider.getSchemaForDocument(path.join(workspace.fsPath, "test.kson"));

            assert.strictEqual(schema, undefined);
        });

        it('should return schema for matching file', () => {
            const workspace = createWorkspace();
            const schemaContent = JSON.stringify({type: 'object'});

            writeConfig(workspace, {
                schemas: [{fileMatch: ['*.kson'], schema: 'schemas/test.json'}]
            });
            writeSchema(workspace, 'schemas/test.json', schemaContent);

            const provider = new FileSystemSchemaProvider(workspace, logger);
            const schema = provider.getSchemaForDocument(path.join(workspace.fsPath, `test.kson`));

            assert.ok(schema, "schema was undefined");
            assert.strictEqual(schema!.getText(), schemaContent);
        });

        it('should return undefined for non-matching file', () => {
            const workspace = createWorkspace();

            writeConfig(workspace, {
                schemas: [{fileMatch: ['config/*.kson'], schema: 'schemas/config.json'}]
            });

            const provider = new FileSystemSchemaProvider(workspace, logger);
            const schema = provider.getSchemaForDocument(path.join(workspace.fsPath, "other.kson"));

            assert.strictEqual(schema, undefined);
        });

        it('should handle missing schema file', () => {
            const workspace = createWorkspace();

            writeConfig(workspace, {
                schemas: [{fileMatch: ['*.kson'], schema: 'schemas/missing.json'}]
            });

            const provider = new FileSystemSchemaProvider(workspace, logger);
            const schema = provider.getSchemaForDocument(path.join(workspace.fsPath, "test.kson"));

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

            const provider = new FileSystemSchemaProvider(workspace, logger);
            const schema = provider.getSchemaForDocument(path.join(workspace.fsPath, "config/deep/test.kson"));

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

            const provider = new FileSystemSchemaProvider(workspace, logger);
            const schema = provider.getSchemaForDocument(path.join(workspace.fsPath, "test.kson"));

            assert.ok(schema);
            assert.strictEqual(schema!.getText(), schema1);
        });
    });

    describe('reload', () => {
        it('should reload configuration from disk', () => {
            const workspace = createWorkspace();
            writeConfig(workspace, {
                schemas: [{fileMatch: ['*.kson'], schema: 'schemas/test.json'}]
            });

            const provider = new FileSystemSchemaProvider(workspace, logger);
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

            const provider = new FileSystemSchemaProvider(workspace, logger);

            // Get schema before update
            const schemaBefore = provider.getSchemaForDocument(path.join(workspace.fsPath, "test.kson"));
            assert.ok(schemaBefore);
            assert.strictEqual(schemaBefore!.getText(), originalSchema);

            // Update schema file
            writeSchema(workspace, 'schemas/test.json', updatedSchema);
            provider.reload();

            // Get schema after reload
            const schemaAfter = provider.getSchemaForDocument(path.join(workspace.fsPath, "test.kson"));
            assert.ok(schemaAfter);
            assert.strictEqual(schemaAfter!.getText(), updatedSchema);
        });

        it('should pick up new schema mappings after config change', () => {
            const workspace = createWorkspace();

            writeConfig(workspace, {
                schemas: [{fileMatch: ['config/*.kson'], schema: 'schemas/config.json'}]
            });
            writeSchema(workspace, 'schemas/config.json', JSON.stringify({type: 'config'}));

            const provider = new FileSystemSchemaProvider(workspace, logger);

            // Should not match initially
            const schemaBefore = provider.getSchemaForDocument(path.join(workspace.fsPath, "data.kson"));
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
            const schemaAfter = provider.getSchemaForDocument(path.join(workspace.fsPath, "data.kson"));
            assert.ok(schemaAfter);
            const parsed = JSON.parse(schemaAfter!.getText());
            assert.strictEqual(parsed.type, 'data');
        });
    });
});