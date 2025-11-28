import {describe, it, beforeEach, afterEach} from 'mocha';
import * as assert from 'assert';
import * as fs from 'fs';
import * as path from 'path';
import * as os from 'os';
import {FileSystemSchemaProvider} from '../../../core/schema/FileSystemSchemaProvider';

describe('FileSystemSchemaProvider', () => {
    let tempDir: string;
    let logger: {
        info: (message: string) => void;
        warn: (message: string) => void;
        error: (message: string) => void;
    };
    let logMessages: string[];

    beforeEach(() => {
        // Create a temporary directory for test files
        tempDir = fs.mkdtempSync(path.join(os.tmpdir(), 'kson-test-'));

        // Create a logger that captures messages
        logMessages = [];
        logger = {
            info: (msg: string) => logMessages.push(`INFO: ${msg}`),
            warn: (msg: string) => logMessages.push(`WARN: ${msg}`),
            error: (msg: string) => logMessages.push(`ERROR: ${msg}`)
        };
    });

    afterEach(() => {
        // Clean up temporary directory
        if (fs.existsSync(tempDir)) {
            fs.rmSync(tempDir, {recursive: true, force: true});
        }
    });

    describe('constructor', () => {
        it('should handle null workspace root', () => {
            const manager = new FileSystemSchemaProvider(null, logger);
            assert.ok(manager);
            assert.ok(logMessages.some(msg => msg.includes('No workspace root available')));
        });

        it('should handle missing .kson-schema.kson file', () => {
            const workspaceUri = `file://${tempDir}`;
            const manager = new FileSystemSchemaProvider(workspaceUri, logger);

            assert.ok(manager);
            assert.ok(logMessages.some(msg => msg.includes('No .kson-schema.kson found')));
        });

        it('should load valid .kson-schema.kson file', () => {
            // Create a valid config file
            const configContent = JSON.stringify({
                schemas: [
                    {
                        fileMatch: ['*.kson'],
                        schema: 'schemas/test.schema.json'
                    }
                ]
            });
            fs.writeFileSync(path.join(tempDir, '.kson-schema.kson'), configContent);

            const workspaceUri = `file://${tempDir}`;
            const manager = new FileSystemSchemaProvider(workspaceUri, logger);

            assert.ok(manager);
            assert.ok(logMessages.some(msg => msg.includes('Loaded schema configuration with 1 mappings')));
        });

        it('should handle invalid JSON in config file', () => {
            // Create an invalid config file
            fs.writeFileSync(path.join(tempDir, '.kson-schema.kson'), 'invalid json {');

            const workspaceUri = `file://${tempDir}`;
            const manager = new FileSystemSchemaProvider(workspaceUri, logger);

            assert.ok(manager);
            assert.ok(logMessages.some(msg => msg.includes('ERROR') && msg.includes('Failed to load')));
        });

        it('should handle invalid schema config format', () => {
            // Create a config with invalid structure
            const configContent = JSON.stringify({
                schemas: 'not an array'
            });
            fs.writeFileSync(path.join(tempDir, '.kson-schema.kson'), configContent);

            const workspaceUri = `file://${tempDir}`;
            const manager = new FileSystemSchemaProvider(workspaceUri, logger);

            assert.ok(manager);
            assert.ok(logMessages.some(msg => msg.includes('ERROR') && msg.includes('Invalid .kson-schema.kson format')));
        });
    });

    describe('getSchemaForDocument', () => {
        it('should return undefined when no workspace root', () => {
            const manager = new FileSystemSchemaProvider(null, logger);
            const schema = manager.getSchemaForDocument('file:///test.kson');

            assert.strictEqual(schema, undefined);
        });

        it('should return undefined when no config loaded', () => {
            const workspaceUri = `file://${tempDir}`;
            const manager = new FileSystemSchemaProvider(workspaceUri, logger);
            const schema = manager.getSchemaForDocument(`file://${tempDir}/test.kson`);

            assert.strictEqual(schema, undefined);
        });

        it('should return schema for matching file pattern', () => {
            // Create schema file
            const schemasDir = path.join(tempDir, 'schemas');
            fs.mkdirSync(schemasDir);
            const schemaContent = JSON.stringify({
                type: 'object',
                properties: {
                    name: {type: 'string'}
                }
            });
            fs.writeFileSync(path.join(schemasDir, 'test.schema.json'), schemaContent);

            // Create config file
            const configContent = JSON.stringify({
                schemas: [
                    {
                        fileMatch: ['*.kson'],
                        schema: 'schemas/test.schema.json'
                    }
                ]
            });
            fs.writeFileSync(path.join(tempDir, '.kson-schema.kson'), configContent);

            const workspaceUri = `file://${tempDir}`;
            const manager = new FileSystemSchemaProvider(workspaceUri, logger);

            const schema = manager.getSchemaForDocument(`file://${tempDir}/test.kson`);

            assert.ok(schema);
            assert.strictEqual(schema.languageId, 'kson');
            assert.ok(schema.getText().includes('"name"'));
        });

        it('should match glob patterns correctly', () => {
            // Create schema file
            const schemasDir = path.join(tempDir, 'schemas');
            fs.mkdirSync(schemasDir);
            const schemaContent = JSON.stringify({type: 'object'});
            fs.writeFileSync(path.join(schemasDir, 'config.schema.json'), schemaContent);

            // Create config file with glob patterns
            const configContent = JSON.stringify({
                schemas: [
                    {
                        fileMatch: ['config/**/*.kson', '**/*.config.kson'],
                        schema: 'schemas/config.schema.json'
                    }
                ]
            });
            fs.writeFileSync(path.join(tempDir, '.kson-schema.kson'), configContent);

            const workspaceUri = `file://${tempDir}`;
            const manager = new FileSystemSchemaProvider(workspaceUri, logger);

            // Test various matching patterns
            const schema1 = manager.getSchemaForDocument(`file://${tempDir}/config/app.kson`);
            assert.ok(schema1, 'Should match config/**/*.kson');

            const schema2 = manager.getSchemaForDocument(`file://${tempDir}/app.config.kson`);
            assert.ok(schema2, 'Should match **/*.config.kson');

            const schema3 = manager.getSchemaForDocument(`file://${tempDir}/other/test.config.kson`);
            assert.ok(schema3, 'Should match **/*.config.kson in subdirectory');

            const schema4 = manager.getSchemaForDocument(`file://${tempDir}/test.kson`);
            assert.strictEqual(schema4, undefined, 'Should not match non-matching pattern');
        });

        it('should return first matching schema when multiple patterns match', () => {
            // Create two schema files
            const schemasDir = path.join(tempDir, 'schemas');
            fs.mkdirSync(schemasDir);
            fs.writeFileSync(path.join(schemasDir, 'first.schema.json'), JSON.stringify({title: 'first'}));
            fs.writeFileSync(path.join(schemasDir, 'second.schema.json'), JSON.stringify({title: 'second'}));

            // Create config with overlapping patterns
            const configContent = JSON.stringify({
                schemas: [
                    {
                        fileMatch: ['*.kson'],
                        schema: 'schemas/first.schema.json'
                    },
                    {
                        fileMatch: ['**/*.kson'],
                        schema: 'schemas/second.schema.json'
                    }
                ]
            });
            fs.writeFileSync(path.join(tempDir, '.kson-schema.kson'), configContent);

            const workspaceUri = `file://${tempDir}`;
            const manager = new FileSystemSchemaProvider(workspaceUri, logger);

            const schema = manager.getSchemaForDocument(`file://${tempDir}/test.kson`);

            assert.ok(schema);
            assert.ok(schema.getText().includes('first'), 'Should use first matching schema');
        });

        it('should return undefined when schema file does not exist', () => {
            // Create config pointing to non-existent schema
            const configContent = JSON.stringify({
                schemas: [
                    {
                        fileMatch: ['*.kson'],
                        schema: 'schemas/nonexistent.schema.json'
                    }
                ]
            });
            fs.writeFileSync(path.join(tempDir, '.kson-schema.kson'), configContent);

            const workspaceUri = `file://${tempDir}`;
            const manager = new FileSystemSchemaProvider(workspaceUri, logger);

            const schema = manager.getSchemaForDocument(`file://${tempDir}/test.kson`);

            assert.strictEqual(schema, undefined);
            assert.ok(logMessages.some(msg => msg.includes('WARN') && msg.includes('Schema file not found')));
        });
    });

    describe('reload', () => {
        it('should reload configuration after file changes', () => {
            // Start with no config file
            const workspaceUri = `file://${tempDir}`;
            const manager = new FileSystemSchemaProvider(workspaceUri, logger);

            let schema = manager.getSchemaForDocument(`file://${tempDir}/test.kson`);
            assert.strictEqual(schema, undefined);

            // Now create a config file
            const schemasDir = path.join(tempDir, 'schemas');
            fs.mkdirSync(schemasDir);
            const schemaContent = JSON.stringify({type: 'object'});
            fs.writeFileSync(path.join(schemasDir, 'test.schema.json'), schemaContent);

            const configContent = JSON.stringify({
                schemas: [
                    {
                        fileMatch: ['*.kson'],
                        schema: 'schemas/test.schema.json'
                    }
                ]
            });
            fs.writeFileSync(path.join(tempDir, '.kson-schema.kson'), configContent);

            // Reload and check again
            manager.reload();
            schema = manager.getSchemaForDocument(`file://${tempDir}/test.kson`);

            assert.ok(schema, 'Schema should be available after reload');
        });
    });

    describe('path handling', () => {
        it('should handle file URIs correctly', () => {
            // Create schema file
            const schemasDir = path.join(tempDir, 'schemas');
            fs.mkdirSync(schemasDir);
            const schemaContent = JSON.stringify({type: 'object'});
            fs.writeFileSync(path.join(schemasDir, 'test.schema.json'), schemaContent);

            // Create config
            const configContent = JSON.stringify({
                schemas: [
                    {
                        fileMatch: ['test.kson'],
                        schema: 'schemas/test.schema.json'
                    }
                ]
            });
            fs.writeFileSync(path.join(tempDir, '.kson-schema.kson'), configContent);

            // Test with file:// URI
            const workspaceUri = `file://${tempDir}`;
            const manager = new FileSystemSchemaProvider(workspaceUri, logger);

            const documentUri = `file://${tempDir}/test.kson`;
            const schema = manager.getSchemaForDocument(documentUri);

            assert.ok(schema, 'Should handle file:// URIs');
        });

        it('should normalize path separators for glob matching', () => {
            // Create schema file
            const schemasDir = path.join(tempDir, 'schemas');
            fs.mkdirSync(schemasDir);
            const schemaContent = JSON.stringify({type: 'object'});
            fs.writeFileSync(path.join(schemasDir, 'test.schema.json'), schemaContent);

            // Create subdirectory
            const configDir = path.join(tempDir, 'config');
            fs.mkdirSync(configDir);

            // Create config with forward slash pattern
            const configContent = JSON.stringify({
                schemas: [
                    {
                        fileMatch: ['config/*.kson'],
                        schema: 'schemas/test.schema.json'
                    }
                ]
            });
            fs.writeFileSync(path.join(tempDir, '.kson-schema.kson'), configContent);

            const workspaceUri = `file://${tempDir}`;
            const manager = new FileSystemSchemaProvider(workspaceUri, logger);

            // Should work regardless of platform path separators
            const documentUri = `file://${path.join(tempDir, 'config', 'app.kson')}`;
            const schema = manager.getSchemaForDocument(documentUri);

            assert.ok(schema, 'Should normalize path separators for matching');
        });
    });
});
