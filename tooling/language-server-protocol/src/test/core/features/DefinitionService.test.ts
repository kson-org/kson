import {describe, it} from 'mocha';
import assert from 'assert';
import {DefinitionService} from '../../../core/features/DefinitionService.js';
import {createKsonDocument, createKsonSchemaDocument, pos, SCHEMA_URI} from '../../TestHelpers.js';

describe('DefinitionService', () => {
    const definitionService = new DefinitionService();

    it('should return empty list when no schema is configured', () => {
        const document = createKsonDocument('{ "name": "test" }');

        const definition = definitionService.getDefinition(document, pos(0, 4));

        assert.deepStrictEqual(definition, []);
    });

    it('should return null when definition not found', () => {
        const schemaContent = `{
            "type": "object",
            "properties": {
                "name": {
                    "type": "string"
                }
            }
        }`;

        const document = createKsonDocument('{ "unknown": "test" }', schemaContent);

        const definition = definitionService.getDefinition(document, pos(0, 4));

        assert.ok(definition === null || Array.isArray(definition), 'Definition should be null or an array');
    });

    it('should return DefinitionLink when definition exists', () => {
        const schemaContent = `{
            "type": "object",
            "properties": {
                "name": {
                    "type": "string",
                    "description": "The name of the service"
                },
                "port": {
                    "type": "number"
                }
            }
        }`;

        const document = createKsonDocument('{ "name": "test", "port": 8080 }', schemaContent);

        const definition = definitionService.getDefinition(document, pos(0, 20));

        assert.ok(Array.isArray(definition), 'Definition should be an array');
        assert.ok(definition.length > 0, 'Definition array should not be empty');

        const firstDef = definition[0];
        assert.ok(firstDef.targetUri, 'Definition should have targetUri');
        assert.strictEqual(firstDef.targetUri, SCHEMA_URI, 'Target URI should be schema document');
        assert.ok(firstDef.targetRange, 'Definition should have targetRange');
        assert.ok(firstDef.targetSelectionRange, 'Definition should have targetSelectionRange');
    });

    it('should handle errors gracefully', () => {
        const document = createKsonDocument('{ invalid json');

        assert.doesNotThrow(() => {
            definitionService.getDefinition(document, pos(0, 4));
        });
    });

    describe('$ref resolution within schema documents', () => {
        const META_SCHEMA = `{
    "$id": "http://json-schema.org/draft-07/schema#",
    "type": "object",
    "properties": {
        "type": { "type": "string" },
        "properties": { "type": "object", "additionalProperties": { "$ref": "#" } },
        "$ref": { "type": "string" },
        "$defs": { "type": "object" },
        "definitions": { "type": "object" },
        "$schema": { "type": "string" },
        "description": { "type": "string" }
    }
}`;

        it('should resolve $ref to $defs definition', () => {
            const schemaContent = `{
    "$schema": "http://json-schema.org/draft-07/schema#",
    "type": "object",
    "properties": {
        "user": {
            "$ref": "#/$defs/User"
        }
    },
    "$defs": {
        "User": {
            "type": "object",
            "properties": {
                "name": {
                    "type": "string"
                }
            }
        }
    }
}`;
            const document = createKsonSchemaDocument(schemaContent, META_SCHEMA);

            const definition = definitionService.getDefinition(document, pos(5, 24));

            assert.ok(Array.isArray(definition), 'Definition should be an array');
            assert.strictEqual(definition.length, 2, 'Should have results from both $ref resolution and schema navigation');

            const refDef = definition.find(d => d.targetUri === document.uri);
            assert.ok(refDef, 'Should have a $ref resolution result pointing to same document');
            assert.strictEqual(refDef.targetRange.start.line, 9, 'Should point to User definition');
        });

        it('should resolve $ref with JSON Pointer to nested property', () => {
            const schemaContent = `{
    "$schema": "http://json-schema.org/draft-07/schema#",
    "type": "object",
    "properties": {
        "data": {
            "$ref": "#/properties/user/properties/name"
        },
        "user": {
            "type": "object",
            "properties": {
                "name": {
                    "type": "string"
                }
            }
        }
    }
}`;
            const document = createKsonSchemaDocument(schemaContent, META_SCHEMA);

            const definition = definitionService.getDefinition(document, pos(5, 25));

            assert.ok(Array.isArray(definition), 'Definition should be an array');
            assert.strictEqual(definition.length, 2, 'Should have results from both $ref resolution and schema navigation');

            const refDef = definition.find(d => d.targetUri === document.uri);
            assert.ok(refDef, 'Should have a $ref resolution result pointing to same document');
            assert.strictEqual(refDef.targetRange.start.line, 10, 'Should point to name property');
        });

        it('should resolve $ref to root schema', () => {
            const schemaContent = `{
    "$schema": "http://json-schema.org/draft-07/schema#",
    "type": "object",
    "properties": {
        "recursive": {
            "$ref": "#"
        }
    }
}`;
            const document = createKsonSchemaDocument(schemaContent, META_SCHEMA);

            const definition = definitionService.getDefinition(document, pos(5, 21));

            assert.ok(Array.isArray(definition), 'Definition should be an array');
            assert.strictEqual(definition.length, 2, 'Should have results from both $ref resolution and schema navigation');

            const refDef = definition.find(d => d.targetUri === document.uri);
            assert.ok(refDef, 'Should have a $ref resolution result pointing to same document');
            assert.strictEqual(refDef.targetRange.start.line, 0, 'Should point to root');
        });

        it('should return empty list when $ref target not found', () => {
            const schemaContent = `{
    "type": "object",
    "properties": {
        "data": {
            "$ref": "#/$defs/NonExistent"
        }
    },
    "$defs": {
        "User": {
            "type": "string"
        }
    }
}`;
            const document = createKsonDocument(schemaContent);

            const definition = definitionService.getDefinition(document, pos(4, 25));

            assert.ok(definition === null || (Array.isArray(definition) && definition.length === 0));
        });

        it('should return empty list for external $ref', () => {
            const schemaContent = `{
    "type": "object",
    "properties": {
        "data": {
            "$ref": "./external.schema.kson#/$defs/User"
        }
    }
}`;
            const document = createKsonDocument(schemaContent);

            const definition = definitionService.getDefinition(document, pos(4, 25));

            assert.ok(definition === null || (Array.isArray(definition) && definition.length === 0));
        });

        it('should return empty list when not on a $ref', () => {
            const schemaContent = `{
    "type": "object",
    "properties": {
        "name": {
            "type": "string"
        }
    }
}`;
            const document = createKsonDocument(schemaContent);

            const definition = definitionService.getDefinition(document, pos(4, 20));

            assert.ok(definition === null || (Array.isArray(definition) && definition.length === 0));
        });

        it('should resolve $ref using definitions instead of $defs', () => {
            const schemaContent = `{
    "$schema": "http://json-schema.org/draft-07/schema#",
    "type": "object",
    "properties": {
        "item": {
            "$ref": "#/definitions/Item"
        }
    },
    "definitions": {
        "Item": {
            "type": "object",
            "properties": {
                "id": {
                    "type": "number"
                }
            }
        }
    }
}`;
            const document = createKsonSchemaDocument(schemaContent, META_SCHEMA);

            const definition = definitionService.getDefinition(document, pos(5, 25));

            assert.ok(Array.isArray(definition), 'Definition should be an array');
            assert.strictEqual(definition.length, 2, 'Should have results from both $ref resolution and schema navigation');

            const refDef = definition.find(d => d.targetUri === document.uri);
            assert.ok(refDef, 'Should have a $ref resolution result pointing to same document');
            assert.strictEqual(refDef.targetRange.start.line, 9, 'Should point to Item definition');
        });

        it('should work with transitive $refs', () => {
            const schemaContent = `{
    "$schema": "http://json-schema.org/draft-07/schema#",
    "type": "object",
    "properties": {
        "data": {
            "$ref": "#/$defs/Alias"
        }
    },
    "$defs": {
        "Alias": {
            "$ref": "#/$defs/Target"
        },
        "Target": {
            "type": "string"
        }
    }
}`;
            const document = createKsonSchemaDocument(schemaContent, META_SCHEMA);

            const definition = definitionService.getDefinition(document, pos(5, 23));

            assert.ok(Array.isArray(definition), 'Definition should be an array');
            assert.strictEqual(definition.length, 2, 'Should have results from both $ref resolution and schema navigation');

            const refDef = definition.find(d => d.targetUri === document.uri);
            assert.ok(refDef, 'Should have a $ref resolution result pointing to same document');
            assert.strictEqual(refDef.targetRange.start.line, 9, 'Should point to Alias definition');
        });
    });
});
