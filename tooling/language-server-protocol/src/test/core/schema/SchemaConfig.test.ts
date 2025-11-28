import {describe, it} from 'mocha';
import * as assert from 'assert';
import {isValidSchemaConfig, SchemaConfig, SchemaMapping} from '../../../core/schema/SchemaConfig.js';

describe('SchemaConfig', () => {
    describe('isValidSchemaConfig', () => {
        it('should return true for valid schema config', () => {
            const validConfig: SchemaConfig = {
                schemas: [
                    {
                        fileMatch: ['config/*.kson'],
                        schema: 'schemas/config.schema.json'
                    }
                ]
            };

            assert.strictEqual(isValidSchemaConfig(validConfig), true);
        });

        it('should return true for valid config with multiple mappings', () => {
            const validConfig: SchemaConfig = {
                schemas: [
                    {
                        fileMatch: ['config/*.kson', '**/*.config.kson'],
                        schema: 'schemas/config.schema.json'
                    },
                    {
                        fileMatch: ['settings.kson'],
                        schema: 'schemas/settings.schema.json'
                    }
                ]
            };

            assert.strictEqual(isValidSchemaConfig(validConfig), true);
        });

        it('should return false for null', () => {
            assert.strictEqual(isValidSchemaConfig(null), false);
        });

        it('should return false for undefined', () => {
            assert.strictEqual(isValidSchemaConfig(undefined), false);
        });

        it('should return false for non-object', () => {
            assert.strictEqual(isValidSchemaConfig('not an object'), false);
            assert.strictEqual(isValidSchemaConfig(123), false);
            assert.strictEqual(isValidSchemaConfig(true), false);
        });

        it('should return false when schemas is not an array', () => {
            const invalidConfig = {
                schemas: 'not an array'
            };

            assert.strictEqual(isValidSchemaConfig(invalidConfig), false);
        });

        it('should return false when schemas is missing', () => {
            const invalidConfig = {};

            assert.strictEqual(isValidSchemaConfig(invalidConfig), false);
        });

        it('should return false when fileMatch is not an array', () => {
            const invalidConfig = {
                schemas: [
                    {
                        fileMatch: 'not an array',
                        schema: 'schemas/config.schema.json'
                    }
                ]
            };

            assert.strictEqual(isValidSchemaConfig(invalidConfig), false);
        });

        it('should return false when fileMatch contains non-string', () => {
            const invalidConfig = {
                schemas: [
                    {
                        fileMatch: ['config/*.kson', 123],
                        schema: 'schemas/config.schema.json'
                    }
                ]
            };

            assert.strictEqual(isValidSchemaConfig(invalidConfig), false);
        });

        it('should return false when schema is not a string', () => {
            const invalidConfig = {
                schemas: [
                    {
                        fileMatch: ['config/*.kson'],
                        schema: 123
                    }
                ]
            };

            assert.strictEqual(isValidSchemaConfig(invalidConfig), false);
        });

        it('should return false when schema is missing', () => {
            const invalidConfig = {
                schemas: [
                    {
                        fileMatch: ['config/*.kson']
                    }
                ]
            };

            assert.strictEqual(isValidSchemaConfig(invalidConfig), false);
        });

        it('should return true for empty schemas array', () => {
            const validConfig = {
                schemas: []
            };

            assert.strictEqual(isValidSchemaConfig(validConfig), true);
        });

        it('should return false when mapping is not an object', () => {
            const invalidConfig = {
                schemas: ['not an object']
            };

            assert.strictEqual(isValidSchemaConfig(invalidConfig), false);
        });
    });
});
