import * as assert from 'assert';
import * as fs from 'fs';
import * as path from 'path';
import * as os from 'os';
import { describe, it, beforeEach, afterEach } from 'mocha';
import { RemoveSchemaCommand } from '../../../core/commands/RemoveSchemaCommand.js';
import { SCHEMA_CONFIG_FILENAME } from '../../../core/schema/SchemaConfig.js';
import { Kson, Result } from 'kson';

describe('RemoveSchemaCommand', () => {
    let testWorkspaceRoot: string;
    let schemaConfigPath: string;

    beforeEach(() => {
        // Create a temporary directory for testing
        testWorkspaceRoot = fs.mkdtempSync(path.join(os.tmpdir(), 'kson-test-'));
        schemaConfigPath = path.join(testWorkspaceRoot, SCHEMA_CONFIG_FILENAME);
    });

    afterEach(() => {
        // Clean up temporary directory
        if (fs.existsSync(testWorkspaceRoot)) {
            fs.rmSync(testWorkspaceRoot, { recursive: true, force: true });
        }
    });

    /**
     * Helper to create a schema config file with given associations
     */
    function createSchemaConfig(schemas: Array<{ fileMatch: string[], schema: string }>): void {
        const config = {
            schemas: schemas
        };
        fs.writeFileSync(schemaConfigPath, JSON.stringify(config, null, 2), 'utf-8');
    }

    /**
     * Helper to read the schema config file
     * Note: After RemoveSchemaCommand writes the file, it's in KSON format
     */
    function readSchemaConfig(): any {
        if (!fs.existsSync(schemaConfigPath)) {
            return null;
        }
        const content = fs.readFileSync(schemaConfigPath, 'utf-8');

        // Try to parse as KSON first (what RemoveSchemaCommand writes)
        const ksonResult = Kson.getInstance().toJson(content);
        if (ksonResult instanceof Result.Success) {
            return JSON.parse(ksonResult.output);
        }

        // Fallback to JSON (what our test helper creates)
        return JSON.parse(content);
    }

    it('should remove schema association for a file', () => {
        // Setup: Create config with one association
        createSchemaConfig([
            { fileMatch: ['test.kson'], schema: 'schema.kson' }
        ]);

        // Execute
        const result = RemoveSchemaCommand.execute({
            documentUri: `file://${path.join(testWorkspaceRoot, 'test.kson')}`,
            workspaceRoot: testWorkspaceRoot
        });

        // Assert
        assert.strictEqual(result.success, true);
        assert.ok(result.message.includes('Removed schema association'));

        // Verify config file was deleted (no schemas left)
        assert.strictEqual(fs.existsSync(schemaConfigPath), false);
    });

    it('should delete config file when removing the last association', () => {
        // Setup: Create config with one association
        createSchemaConfig([
            { fileMatch: ['test.kson'], schema: 'schema.kson' }
        ]);

        // Execute
        const result = RemoveSchemaCommand.execute({
            documentUri: `file://${path.join(testWorkspaceRoot, 'test.kson')}`,
            workspaceRoot: testWorkspaceRoot
        });

        // Assert
        assert.strictEqual(result.success, true);
        assert.strictEqual(fs.existsSync(schemaConfigPath), false, 'Config file should be deleted');
    });

    it('should keep config file when removing one of multiple associations', () => {
        // Setup: Create config with two associations
        createSchemaConfig([
            { fileMatch: ['test1.kson'], schema: 'schema1.kson' },
            { fileMatch: ['test2.kson'], schema: 'schema2.kson' }
        ]);

        // Execute: Remove first association
        const result = RemoveSchemaCommand.execute({
            documentUri: `file://${path.join(testWorkspaceRoot, 'test1.kson')}`,
            workspaceRoot: testWorkspaceRoot
        });

        // Assert
        assert.strictEqual(result.success, true);

        // Verify config file still exists
        assert.strictEqual(fs.existsSync(schemaConfigPath), true, 'Config file should still exist');

        // Verify only second association remains
        const config = readSchemaConfig();
        assert.strictEqual(config.schemas.length, 1);
        assert.deepStrictEqual(config.schemas[0].fileMatch, ['test2.kson']);
        assert.strictEqual(config.schemas[0].schema, 'schema2.kson');
    });

    it('should fail when no workspace root is provided', () => {
        const result = RemoveSchemaCommand.execute({
            documentUri: 'file:///test.kson',
            workspaceRoot: null
        });

        assert.strictEqual(result.success, false);
        assert.ok(result.message.includes('No workspace root'));
    });

    it('should fail when config file does not exist', () => {
        // Don't create any config file
        const result = RemoveSchemaCommand.execute({
            documentUri: `file://${path.join(testWorkspaceRoot, 'test.kson')}`,
            workspaceRoot: testWorkspaceRoot
        });

        assert.strictEqual(result.success, false);
        assert.ok(result.message.includes('No schema configuration file found'));
    });

    it('should fail when file has no schema association', () => {
        // Setup: Create config with association for a different file
        createSchemaConfig([
            { fileMatch: ['other.kson'], schema: 'schema.kson' }
        ]);

        // Try to remove association for a file that doesn't have one
        const result = RemoveSchemaCommand.execute({
            documentUri: `file://${path.join(testWorkspaceRoot, 'test.kson')}`,
            workspaceRoot: testWorkspaceRoot
        });

        assert.strictEqual(result.success, false);
        assert.ok(result.message.includes('No schema association found'));

        // Verify original association is still there
        const config = readSchemaConfig();
        assert.strictEqual(config.schemas.length, 1);
        assert.deepStrictEqual(config.schemas[0].fileMatch, ['other.kson']);
    });

    it('should handle paths with forward slashes', () => {
        // Setup: Create config with forward slash path
        createSchemaConfig([
            { fileMatch: ['subfolder/test.kson'], schema: 'schema.kson' }
        ]);

        // Execute
        const result = RemoveSchemaCommand.execute({
            documentUri: `file://${path.join(testWorkspaceRoot, 'subfolder', 'test.kson')}`,
            workspaceRoot: testWorkspaceRoot
        });

        // Assert
        assert.strictEqual(result.success, true);
        assert.strictEqual(fs.existsSync(schemaConfigPath), false);
    });

    it('should handle paths with backslashes on Windows', () => {
        // Setup: Create config with normalized forward slash path
        createSchemaConfig([
            { fileMatch: ['subfolder/test.kson'], schema: 'schema.kson' }
        ]);

        // Execute with path that would have backslashes on Windows
        const testPath = path.join(testWorkspaceRoot, 'subfolder', 'test.kson');
        const result = RemoveSchemaCommand.execute({
            documentUri: `file://${testPath}`,
            workspaceRoot: testWorkspaceRoot
        });

        // Assert
        assert.strictEqual(result.success, true);
        assert.strictEqual(fs.existsSync(schemaConfigPath), false);
    });

    it('should only remove exact file matches, not pattern matches', () => {
        // Setup: Create config with both exact match and pattern
        createSchemaConfig([
            { fileMatch: ['test.kson'], schema: 'schema1.kson' },
            { fileMatch: ['**/*.kson'], schema: 'schema2.kson' }
        ]);

        // Execute: Try to remove exact match
        const result = RemoveSchemaCommand.execute({
            documentUri: `file://${path.join(testWorkspaceRoot, 'test.kson')}`,
            workspaceRoot: testWorkspaceRoot
        });

        // Assert: Should remove only the exact match
        assert.strictEqual(result.success, true);

        const config = readSchemaConfig();
        assert.strictEqual(config.schemas.length, 1);
        assert.deepStrictEqual(config.schemas[0].fileMatch, ['**/*.kson']);
    });
});