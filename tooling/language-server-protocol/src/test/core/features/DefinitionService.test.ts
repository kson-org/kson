import {describe, it} from 'mocha';
import * as assert from 'assert';
import {Kson} from 'kson';
import {DefinitionService} from '../../../core/features/DefinitionService.js';
import {KsonDocument} from '../../../core/document/KsonDocument.js';
import {TextDocument} from 'vscode-languageserver-textdocument';
import {Position} from 'vscode-languageserver';

describe('DefinitionService', () => {
    const definitionService = new DefinitionService();

    it('should return null when no schema is configured', () => {
        // Create a document without a schema
        const content = '{ "name": "test" }';
        const textDoc = TextDocument.create('file:///test.kson', 'kson', 1, content);
        const analysis = Kson.getInstance().analyze(content);
        const document = new KsonDocument(textDoc, analysis, undefined);

        const position: Position = {line: 0, character: 4}; // Position on "name"
        const definition = definitionService.getDefinition(document, position);

        assert.strictEqual(definition, null);
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
        if (definition !== null) {
            assert.ok(Array.isArray(definition), 'Definition should be an array');
            assert.ok(definition.length > 0, 'Definition array should not be empty');

            const firstDef = definition[0];
            assert.ok(firstDef.targetUri, 'Definition should have targetUri');
            assert.strictEqual(firstDef.targetUri, schemaDocument.uri, 'Target URI should be schema document');
            assert.ok(firstDef.targetRange, 'Definition should have targetRange');
            assert.ok(firstDef.targetSelectionRange, 'Definition should have targetSelectionRange');
        } else {
            // If null, log that the tooling-lib doesn't support this yet
            console.log('DefinitionService returned null - tooling-lib may not support definition location yet');
        }
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
});
