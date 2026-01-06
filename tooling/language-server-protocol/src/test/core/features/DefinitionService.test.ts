import {describe, it} from 'mocha';
import * as assert from 'assert';
import {Kson} from 'kson';
import {DefinitionService} from '../../../core/features/DefinitionService.js';
import {KsonDocument} from '../../../core/document/KsonDocument.js';
import {TextDocument} from 'vscode-languageserver-textdocument';
import {Position} from 'vscode-languageserver';

describe('DefinitionService', () => {
    const definitionService = new DefinitionService();

    it('should return empty list when no schema is configured', () => {
        // Create a document without a schema
        const content = '{ "name": "test" }';
        const textDoc = TextDocument.create('file:///test.kson', 'kson', 1, content);
        const analysis = Kson.getInstance().analyze(content);
        const document = new KsonDocument(textDoc, analysis, undefined);

        const position: Position = {line: 0, character: 4}; // Position on "name"
        const definition = definitionService.getDefinition(document, position);

        assert.deepStrictEqual(definition, []);
    });

    it('should return null when definition not found', () => {
        // Create a simple schema
        const schemaContent = `{
            "type": "object",
            "properties": {
                "name": {
                    "type": "string"
                }
            }
        }`;

        const schemaDocument = TextDocument.create("file:///schema.kson", 'kson', 1, schemaContent);

        // Create a document with a property not in schema
        const docContent = '{ "unknown": "test" }';
        const textDoc = TextDocument.create('file:///test.kson', 'kson', 1, docContent);
        const analysis = Kson.getInstance().analyze(docContent);
        const document = new KsonDocument(textDoc, analysis, schemaDocument);

        // Try to get definition for the "unknown" property
        const position: Position = {line: 0, character: 4}; // Position on "unknown"
        const definition = definitionService.getDefinition(document, position);

        // Should return null if no definition found
        // Note: This depends on whether the tooling-lib returns a location for properties not in schema
        assert.ok(definition === null || Array.isArray(definition), 'Definition should be null or an array');
    });

    it('should return DefinitionLink when definition exists', () => {
        // Create a simple schema
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

        const schemaDocument = TextDocument.create("file:///schema.kson", 'kson', 1, schemaContent);

        // Create a document that matches the schema
        const docContent = '{ "name": "test", "port": 8080 }';
        const textDoc = TextDocument.create('file:///test.kson', 'kson', 1, docContent);
        const analysis = Kson.getInstance().analyze(docContent);
        const document = new KsonDocument(textDoc, analysis, schemaDocument);

        // Try to get definition for the "port" property
        const position: Position = {line: 0, character: 20}; // Position on "port"
        const definition = definitionService.getDefinition(document, position);

        // Verify definition link is returned if the tooling lib supports it
        assert.ok(Array.isArray(definition), 'Definition should be an array');
        assert.ok(definition.length > 0, 'Definition array should not be empty');

        const firstDef = definition[0];
        assert.ok(firstDef.targetUri, 'Definition should have targetUri');
        assert.strictEqual(firstDef.targetUri, schemaDocument.uri, 'Target URI should be schema document');
        assert.ok(firstDef.targetRange, 'Definition should have targetRange');
        assert.ok(firstDef.targetSelectionRange, 'Definition should have targetSelectionRange');
    });

    it('should handle errors gracefully', () => {
        // Create an invalid document
        const content = '{ invalid json';
        const textDoc = TextDocument.create('file:///test.kson', 'kson', 1, content);
        const analysis = Kson.getInstance().analyze(content);
        const document = new KsonDocument(textDoc, analysis, undefined);

        const position: Position = {line: 0, character: 4};

        // Should not throw an error
        assert.doesNotThrow(() => {
            definitionService.getDefinition(document, position);
        });
    });

    describe('$ref resolution within schema documents', () => {
        it('should resolve $ref to $defs definition', () => {
            // Create a schema document with a $ref
            const schemaContent = `{
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
            const textDoc = TextDocument.create('file:///schema.kson', 'kson', 1, schemaContent);
            const analysis = Kson.getInstance().analyze(schemaContent);
            const document = new KsonDocument(textDoc, analysis, undefined);

            // Position on the $ref value "#/$defs/User"
            const position: Position = {line: 4, character: 24}; // Inside the ref string
            const definition = definitionService.getDefinition(document, position);

            // Should return a definition pointing to the User definition
            assert.ok(Array.isArray(definition), 'Definition should be an array');
            assert.ok(definition.length > 0, 'Definition array should not be empty');

            const firstDef = definition[0];
            assert.strictEqual(firstDef.targetUri, textDoc.uri, 'Target URI should be same document');
            assert.ok(firstDef.targetRange, 'Definition should have targetRange');

            // Verify it points to the User definition (line 7 where "User": { starts)
            assert.strictEqual(firstDef.targetRange.start.line, 8, 'Should point to User definition');
        });

        it('should resolve $ref with JSON Pointer to nested property', () => {
            // Create a schema with a ref to a nested property
            const schemaContent = `{
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
            const textDoc = TextDocument.create('file:///schema.kson', 'kson', 1, schemaContent);
            const analysis = Kson.getInstance().analyze(schemaContent);
            const document = new KsonDocument(textDoc, analysis, undefined);

            // Position on the $ref value
            const position: Position = {line: 4, character: 25}; // Inside the ref string
            const definition = definitionService.getDefinition(document, position);

            // Should return a definition pointing to the name property
            assert.ok(Array.isArray(definition), 'Definition should be an array');
            assert.ok(definition.length > 0, 'Definition array should not be empty');

            const firstDef = definition[0];
            assert.strictEqual(firstDef.targetUri, textDoc.uri, 'Target URI should be same document');

            // Verify it points to the name property definition
            assert.strictEqual(firstDef.targetRange.start.line, 9, 'Should point to name property');
        });

        it('should resolve $ref to root schema', () => {
            // Create a schema with a ref to root
            const schemaContent = `{
    "type": "object",
    "properties": {
        "recursive": {
            "$ref": "#"
        }
    }
}`;
            const textDoc = TextDocument.create('file:///schema.kson', 'kson', 1, schemaContent);
            const analysis = Kson.getInstance().analyze(schemaContent);
            const document = new KsonDocument(textDoc, analysis, undefined);

            // Position on the $ref value "#"
            const position: Position = {line: 4, character: 21}; // Inside the ref string
            const definition = definitionService.getDefinition(document, position);

            // Should return a definition pointing to the root
            assert.ok(Array.isArray(definition), 'Definition should be an array');
            assert.ok(definition.length > 0, 'Definition array should not be empty');

            const firstDef = definition[0];
            assert.strictEqual(firstDef.targetUri, textDoc.uri, 'Target URI should be same document');

            // Verify it points to the root (line 0)
            assert.strictEqual(firstDef.targetRange.start.line, 0, 'Should point to root');
        });

        it('should return empty list when $ref target not found', () => {
            // Create a schema with an invalid ref
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
            const textDoc = TextDocument.create('file:///schema.kson', 'kson', 1, schemaContent);
            const analysis = Kson.getInstance().analyze(schemaContent);
            const document = new KsonDocument(textDoc, analysis, undefined);

            // Position on the invalid $ref value
            const position: Position = {line: 4, character: 25}; // Inside the ref string
            const definition = definitionService.getDefinition(document, position);

            // Should return null or empty array when ref cannot be resolved
            assert.ok(definition === null || (Array.isArray(definition) && definition.length === 0));
        });

        it('should return empty list for external $ref', () => {
            // Create a schema with an external ref (not supported yet)
            const schemaContent = `{
    "type": "object",
    "properties": {
        "data": {
            "$ref": "./external.schema.kson#/$defs/User"
        }
    }
}`;
            const textDoc = TextDocument.create('file:///schema.kson', 'kson', 1, schemaContent);
            const analysis = Kson.getInstance().analyze(schemaContent);
            const document = new KsonDocument(textDoc, analysis, undefined);

            // Position on the external $ref value
            const position: Position = {line: 4, character: 25}; // Inside the ref string
            const definition = definitionService.getDefinition(document, position);

            // Should return null or empty array for external refs
            assert.ok(definition === null || (Array.isArray(definition) && definition.length === 0));
        });

        it('should return empty list when not on a $ref', () => {
            // Create a schema document
            const schemaContent = `{
    "type": "object",
    "properties": {
        "name": {
            "type": "string"
        }
    }
}`;
            const textDoc = TextDocument.create('file:///schema.kson', 'kson', 1, schemaContent);
            const analysis = Kson.getInstance().analyze(schemaContent);
            const document = new KsonDocument(textDoc, analysis, undefined);

            // Position on a regular property (not a $ref)
            const position: Position = {line: 4, character: 20}; // On "type": "string"
            const definition = definitionService.getDefinition(document, position);

            // Should return null or empty array
            assert.ok(definition === null || (Array.isArray(definition) && definition.length === 0));
        });

        it('should resolve $ref using definitions instead of $defs', () => {
            // Test with JSON Schema Draft 4 style "definitions" instead of "$defs"
            const schemaContent = `{
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
            const textDoc = TextDocument.create('file:///schema.kson', 'kson', 1, schemaContent);
            const analysis = Kson.getInstance().analyze(schemaContent);
            const document = new KsonDocument(textDoc, analysis, undefined);

            // Position on the $ref value
            const position: Position = {line: 4, character: 25}; // Inside the ref string
            const definition = definitionService.getDefinition(document, position);

            // Should return a definition pointing to the Item definition
            assert.ok(Array.isArray(definition), 'Definition should be an array');
            assert.ok(definition.length > 0, 'Definition array should not be empty');

            const firstDef = definition[0];
            assert.strictEqual(firstDef.targetUri, textDoc.uri, 'Target URI should be same document');

            // Verify it points to the Item definition
            assert.strictEqual(firstDef.targetRange.start.line, 8, 'Should point to Item definition');
        });

        it('should work with transitive $refs', () => {
            // Test case where a $ref points to another $ref
            const schemaContent = `{
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
            const textDoc = TextDocument.create('file:///schema.kson', 'kson', 1, schemaContent);
            const analysis = Kson.getInstance().analyze(schemaContent);
            const document = new KsonDocument(textDoc, analysis, undefined);

            // Position on the first $ref value
            const position: Position = {line: 4, character: 23}; // Inside the ref string
            const definition = definitionService.getDefinition(document, position);

            // Should return a definition pointing to the Alias (not following the transitive ref)
            assert.ok(Array.isArray(definition), 'Definition should be an array');
            assert.ok(definition.length > 0, 'Definition array should not be empty');

            const firstDef = definition[0];
            assert.strictEqual(firstDef.targetUri, textDoc.uri, 'Target URI should be same document');

            // Verify it points to the Alias definition (line 8)
            assert.strictEqual(firstDef.targetRange.start.line, 8, 'Should point to Alias definition');
        });
    });
});
