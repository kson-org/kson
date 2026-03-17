import {describe, it} from 'mocha';
import assert from 'assert';
import {CompletionList, Position} from 'vscode-languageserver';
import {CompletionService} from '../../../core/features/CompletionService.js';
import {createKsonDocument, pos} from '../../TestHelpers.js';

describe('CompletionService', () => {
    const completionService = new CompletionService();

    const ENUM_SCHEMA = `{
        type: object
        properties: {
            status: {
                type: string
                description: "The current status"
                enum: ["active", "inactive", "pending"]
            }
        }
    }`;

    function getCompletions(content: string, position: Position, schema?: string): CompletionList | null {
        const document = createKsonDocument(content, schema);
        return completionService.getCompletions(document, position);
    }

    function getCompletionLabels(content: string, position: Position, schema?: string): string[] | null {
        const completions = getCompletions(content, position, schema);
        if (!completions) return null;
        return completions.items.map(item => item.label);
    }

    it('should return null when no schema is configured', () => {
        const labels = getCompletionLabels('{ name: "test" }', pos(0, 10));
        assert.strictEqual(labels, null);
    });

    it('should return enum value completions for quoted strings', () => {
        const labels = getCompletionLabels('{\n    status: "active"\n}', pos(1, 20), ENUM_SCHEMA);

        assert.ok(labels, 'Completions should not be null');
        assert.ok(labels.includes('active'));
        assert.ok(labels.includes('inactive'));
        assert.ok(labels.includes('pending'));
    });

    it('should return enum value completions for unquoted values', () => {
        const labels = getCompletionLabels('\nstatus:\n  value: key\n', pos(1, 9), ENUM_SCHEMA);

        assert.ok(labels, 'Completions should not be null');
        assert.ok(labels.includes('active'));
        assert.ok(labels.includes('inactive'));
        assert.ok(labels.includes('pending'));
    });

    it('should return boolean value completions', () => {
        const schema = `{
            type: object
            properties: {
                enabled: {
                    type: boolean
                    description: "Whether the feature is enabled"
                }
            }
        }`;

        const labels = getCompletionLabels('{ enabled: true }', pos(0, 13), schema);

        assert.ok(labels, 'Completions should not be null');
        assert.ok(labels.includes('true'));
        assert.ok(labels.includes('false'));
    });

    it('should include documentation in completion items', () => {
        const schema = `{
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

        const completions = getCompletions('{ level: "info" }', pos(0, 11), schema);

        assert.ok(completions, 'Completions should not be null');
        const hasDocumentation = completions.items.some(item =>
            item.documentation &&
            typeof item.documentation === 'object' &&
            'value' in item.documentation
        );
        assert.ok(hasDocumentation, 'At least one completion item should have documentation');
    });

    it('should return completions with isIncomplete set to false', () => {
        const schema = `{
            type: object
            properties: {
                color: {
                    type: string
                    enum: ["red", "blue", "green"]
                }
            }
        }`;

        const completions = getCompletions('{ color: "red" }', pos(0, 11), schema);

        assert.ok(completions, 'Completions should not be null');
        assert.strictEqual(completions.isIncomplete, false, 'Completion list should be marked as complete');
    });

    it('should return null for document without schema even with valid content', () => {
        const labels = getCompletionLabels('{ name: "test", age: 30, active: true }', pos(0, 5));
        assert.strictEqual(labels, null);
    });
});
