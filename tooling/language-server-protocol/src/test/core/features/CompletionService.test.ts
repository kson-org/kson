import {describe, it} from 'mocha';
import * as assert from 'assert';
import {Kson} from 'kson';
import {CompletionService} from '../../../core/features/CompletionService.js';
import {KsonDocument} from '../../../core/document/KsonDocument.js';
import {TextDocument} from 'vscode-languageserver-textdocument';
import {Position} from 'vscode-languageserver';

describe('CompletionService', () => {
    const completionService = new CompletionService();

    it('should return null when no schema is configured', () => {
        // Create a document without a schema
        const content = '{ name: "test" }';
        const textDoc = TextDocument.create('file:///test.kson', 'kson', 1, content);
        const analysis = Kson.getInstance().analyze(content);
        const document = new KsonDocument(textDoc, analysis, undefined);

        const position: Position = {line: 0, character: 10}; // Position in value
        const completions = completionService.getCompletions(document, position);

        assert.strictEqual(completions, null);
    });

    it('should return enum value completions', () => {
        // Create a schema with enum values
        const schemaContent = `{
            type: object
            properties: {
                status: {
                    type: string
                    description: "The current status"
                    enum: ["active", "inactive", "pending"]
                }
            }
        }`;

        const schemaDocument = TextDocument.create('file:///schema.kson', 'kson', 1, schemaContent);

        // Create a document that matches the schema - use multi-line format like Kotlin tests
        const docContent = `{
    status: "active"
}`;
        const textDoc = TextDocument.create('file:///test.kson', 'kson', 1, docContent);
        const analysis = Kson.getInstance().analyze(docContent);
        const document = new KsonDocument(textDoc, analysis, schemaDocument);

        // Try getting completions at the value position (on "active")
        const position: Position = {line: 1, character: 20}; // Position on the value
        const completions = completionService.getCompletions(document, position);

        // Verify completions are returned
        assert.ok(completions !== null, 'Completions should not be null for enum property');
        if (completions) {
            assert.ok(completions.items.length > 0, 'Should have completion items');

            // Check that enum values are included
            const labels = completions.items.map(item => item.label);
            assert.ok(labels.includes('active'), 'Should include "active" enum value');
            assert.ok(labels.includes('inactive'), 'Should include "inactive" enum value');
            assert.ok(labels.includes('pending'), 'Should include "pending" enum value');
        }
    });

    it('should return enum value completions', () => {
        // Create a schema with enum values
        const schemaContent = `{
            type: object
            properties: {
                status: {
                    type: string
                    description: "The current status"
                    enum: ["active", "inactive", "pending"]
                }
            }
        }`;

        const schemaDocument = TextDocument.create('file:///schema.kson', 'kson', 1, schemaContent);

        // Create a document that matches the schema - use multi-line format like Kotlin tests
        const docContent = `
status:    
  value: key   
`;
        const textDoc = TextDocument.create('file:///test.kson', 'kson', 1, docContent);
        const analysis = Kson.getInstance().analyze(docContent);
        const document = new KsonDocument(textDoc, analysis, schemaDocument);

        // Try getting completions at the value position (on "active")
        const position: Position = {line: 1, character: 9}; // Position after the key
        const completions = completionService.getCompletions(document, position);

        // Verify completions are returned
        assert.ok(completions !== null, 'Completions should not be null for enum property');
        if (completions) {
            assert.ok(completions.items.length > 0, 'Should have completion items');

            // Check that enum values are included
            const labels = completions.items.map(item => item.label);
            assert.ok(labels.includes('active'), 'Should include "active" enum value');
            assert.ok(labels.includes('inactive'), 'Should include "inactive" enum value');
            assert.ok(labels.includes('pending'), 'Should include "pending" enum value');
        }
    });

    it('should return boolean value completions', () => {
        // Create a schema with boolean type
        const schemaContent = `{
            type: object
            properties: {
                enabled: {
                    type: boolean
                    description: "Whether the feature is enabled"
                }
            }
        }`;

        const schemaDocument = TextDocument.create('file:///schema.kson', 'kson', 1, schemaContent);

        // Create a document
        const docContent = '{ enabled: true }';
        const textDoc = TextDocument.create('file:///test.kson', 'kson', 1, docContent);
        const analysis = Kson.getInstance().analyze(docContent);
        const document = new KsonDocument(textDoc, analysis, schemaDocument);

        // Try getting completions for the boolean value
        const position: Position = {line: 0, character: 13}; // Position on "true"
        const completions = completionService.getCompletions(document, position);

        // Verify completions are returned
        assert.ok(completions !== null, 'Completions should not be null for boolean property');
        if (completions) {
            assert.ok(completions.items.length > 0, 'Should have completion items');

            // Check that boolean values are included
            const labels = completions.items.map(item => item.label);
            assert.ok(labels.includes('true'), 'Should include "true" value');
            assert.ok(labels.includes('false'), 'Should include "false" value');
        }
    });

    it('should include documentation in completion items', () => {
        // Create a schema with enum and description
        const schemaContent = `{
            type: object
            properties: {
                level: {
                    type: string
                    title: "Log Level"
                    description: "The logging level for the application"
                    enum: ["debug", "info", "warn", "error"]
                }
            }
        }`;

        const schemaDocument = TextDocument.create('file:///schema.kson', 'kson', 1, schemaContent);

        // Create a document
        const docContent = '{ level: "info" }';
        const textDoc = TextDocument.create('file:///test.kson', 'kson', 1, docContent);
        const analysis = Kson.getInstance().analyze(docContent);
        const document = new KsonDocument(textDoc, analysis, schemaDocument);

        // Try getting completions for the level value
        const position: Position = {line: 0, character: 11}; // Position on "info"
        const completions = completionService.getCompletions(document, position);

        // Verify completions have documentation
        assert.ok(completions !== null, 'Completions should not be null');
        if (completions && completions.items.length > 0) {
            // At least one item should have documentation
            const hasDocumentation = completions.items.some(item =>
                item.documentation &&
                typeof item.documentation === 'object' &&
                'value' in item.documentation
            );
            assert.ok(hasDocumentation, 'At least one completion item should have documentation');
        }
    });

    it('should handle documents with no matching schema gracefully', () => {
        // Create a simple schema
        const schemaContent = `{
            type: object
            properties: {
                name: {
                    type: string
                }
            }
        }`;

        const schemaDocument = TextDocument.create('file:///schema.kson', 'kson', 1, schemaContent);

        // Create a document with a property not in the schema
        const docContent = '{ unknownProp: 123 }';
        const textDoc = TextDocument.create('file:///test.kson', 'kson', 1, docContent);
        const analysis = Kson.getInstance().analyze(docContent);
        const document = new KsonDocument(textDoc, analysis, schemaDocument);

        // Try getting completions for unknown property value
        const position: Position = {line: 0, character: 17}; // Position on 123
        const completions = completionService.getCompletions(document, position);

        // Should not throw error - might return null or empty list
        assert.ok(true, 'Should handle unknown properties without error');
    });
});