import {describe, it} from 'mocha';
import assert from 'assert';
import {Hover, MarkupKind, Position} from 'vscode-languageserver';
import {HoverService} from '../../../core/features/HoverService.js';
import {createKsonDocument, pos} from '../../TestHelpers.js';

const PORT_SCHEMA = `{
    "type": "object",
    "properties": {
        "name": {
            "type": "string",
            "description": "The name of the service",
            "title": "Service Name"
        },
        "port": {
            "type": "number",
            "description": "Port number",
            "minimum": 1024,
            "maximum": 65535
        }
    }
}`;

describe('HoverService', () => {
    const hoverService = new HoverService();

    function getHover(content: string, position: Position, schema?: string): Hover | null {
        const document = createKsonDocument(content, schema);
        return hoverService.getHover(document, position);
    }

    it('should return null when no schema is configured', () => {
        const hover = getHover('{ "name": "test" }', pos(0, 4));
        assert.strictEqual(hover, null);
    });

    it('should return hover info for a schema-matched property', () => {
        const hover = getHover('{ "name": "test", "port": 8080 }', pos(0, 28), PORT_SCHEMA);

        assert.ok(hover, 'Hover should not be null for schema-matched property');
        const markupContent = hover.contents as {kind: string, value: string};
        assert.strictEqual(markupContent.kind, MarkupKind.Markdown);
        assert.ok(markupContent.value.includes('Port number'));
    });

    it('should return hover with markdown format', () => {
        const schemaContent = `{
            type: object
            properties: {
                name: {
                    type: string
                    description: "The name of the service"
                    title: "Service Name"
                }
            }
        }`;

        const hover = getHover('{ name: "test" }', pos(0, 3), schemaContent);

        assert.ok(hover, 'Hover should not be null for schema-matched property');
        const markupContent = hover.contents as {kind: string, value: string};
        assert.strictEqual(markupContent.kind, MarkupKind.Markdown);
    });

    it('should return hover for a nested property', () => {
        const schemaContent = `{
            type: object
            properties: {
                server: {
                    type: object
                    properties: {
                        host: {
                            type: string
                            description: "Server hostname"
                        }
                    }
                }
            }
        }`;

        const docContent = [
            '{',
            '  server: {',
            '    host: "localhost"',
            '  }',
            '}'
        ].join('\n');

        const hover = getHover(docContent, pos(2, 5), schemaContent);

        assert.ok(hover, 'Hover should work for nested properties');
        const content = hover.contents as {kind: string, value: string};
        assert.strictEqual(content.kind, MarkupKind.Markdown);
        assert.ok(content.value.includes('Server hostname'), 'Hover should contain nested property description');
    });

    it('should include description text in hover content', () => {
        const schemaContent = `{
            type: object
            properties: {
                level: {
                    type: string
                    description: "The logging level for output"
                }
            }
        }`;

        const hover = getHover('{ level: "info" }', pos(0, 3), schemaContent);

        assert.ok(hover, 'Hover should not be null');
        const content = hover.contents as {kind: string, value: string};
        assert.ok(content.value.includes('logging level'), 'Hover should contain the schema description');
    });

    it('should show type information in hover', () => {
        const schemaContent = `{
            type: object
            properties: {
                count: {
                    type: number
                    description: "Total count"
                    minimum: 0
                    maximum: 100
                }
            }
        }`;

        const hover = getHover('{ count: 42 }', pos(0, 3), schemaContent);

        assert.ok(hover, 'Hover should show info for typed property');
        const content = hover.contents as {kind: string, value: string};
        assert.strictEqual(content.kind, MarkupKind.Markdown);
        assert.ok(content.value.includes('Total count'), 'Hover should contain the description');
    });

    it('should handle enum values in hover', () => {
        const schemaContent = `{
            type: object
            properties: {
                status: {
                    type: string
                    description: "Current status"
                    enum: ["active", "inactive"]
                }
            }
        }`;

        const hover = getHover('{ status: "active" }', pos(0, 3), schemaContent);

        assert.ok(hover, 'Hover should return info for enum property');
        const content = hover.contents as {kind: string, value: string};
        assert.strictEqual(content.kind, MarkupKind.Markdown);
        assert.ok(content.value.includes('Current status'), 'Hover should contain the description');
    });
});
