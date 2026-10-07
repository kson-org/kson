import {describe, it} from 'mocha';
import assert from 'assert';
import {CompletionItem, CompletionList, InsertTextFormat, Position} from 'vscode-languageserver';
import {CompletionService} from '../../../core/features/CompletionService.js';
import {createKsonDocument, pos} from '../../TestHelpers.js';

describe('CompletionService', () => {
    const completionService = new CompletionService(false);

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

    it('should return enum value completions while typing a quoted value', () => {
        // Caret right after partial content, before the (auto-closed) close quote: still authoring,
        // so as-you-type enum completion must work.
        const labels = getCompletionLabels('{\n    status: "ac"\n}', pos(1, 15), ENUM_SCHEMA);

        assert.ok(labels, 'Completions should not be null while authoring a quoted value');
        assert.ok(labels.includes('active'));
        assert.ok(labels.includes('inactive'));
        assert.ok(labels.includes('pending'));
    });

    it('should not return value completions after a committed quoted value', () => {
        // Caret past the closing quote of a finished value (`status: "active"|`) — nothing left to
        // choose, so value completions are suppressed.
        const labels = getCompletionLabels('{\n    status: "active"\n}', pos(1, 20), ENUM_SCHEMA);

        assert.strictEqual(labels, null, 'No value completions should be offered after a committed value');
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

    describe('property snippets', () => {
        const SNIPPET_SCHEMA = `{
            type: object
            properties: {
                name: {
                    type: string
                }
                count: {
                    type: integer
                }
                status: {
                    type: string
                    enum: ["active", "inactive"]
                }
            }
        }`;

        // `na|`: a typed key the snippet replaces
        const typedKey = () => createKsonDocument('na', SNIPPET_SCHEMA);

        function item(items: CompletionItem[] | undefined, label: string): CompletionItem {
            const found = items?.find(candidate => candidate.label === label);
            assert.ok(found, `expected a '${label}' completion`);
            return found;
        }

        it('should give clients with snippet support an edit that opens the value', () => {
            const items = new CompletionService(true).getCompletions(typedKey(), pos(0, 2))?.items;

            const name = item(items, 'name');
            assert.strictEqual(name.insertTextFormat, InsertTextFormat.Snippet);
            assert.deepStrictEqual(name.textEdit, {range: {start: pos(0, 0), end: pos(0, 2)}, newText: "name: '$0'"});
        });

        it('should keep a property plain when the tooling offers no snippet for it', () => {
            const items = new CompletionService(true).getCompletions(typedKey(), pos(0, 2))?.items;

            const count = item(items, 'count');
            assert.strictEqual(count.textEdit, undefined);
            assert.strictEqual(count.insertTextFormat, undefined);
        });

        it('should give clients without snippet support the same items with no snippets', () => {
            const withSnippets = new CompletionService(true).getCompletions(typedKey(), pos(0, 2))?.items ?? [];
            const plain = new CompletionService(false).getCompletions(typedKey(), pos(0, 2))?.items;

            assert.ok(withSnippets.some(candidate => candidate.textEdit), 'expected at least one snippet to strip');
            const withoutSnippet = ({textEdit, insertTextFormat, ...rest}: CompletionItem) => rest;
            assert.deepStrictEqual(plain, withSnippets.map(withoutSnippet));
        });

        it('should keep value completions plain for clients with snippet support', () => {
            const items = new CompletionService(true)
                .getCompletions(createKsonDocument('status: ', SNIPPET_SCHEMA), pos(0, 8))?.items;

            assert.deepStrictEqual(items?.map(candidate => candidate.label), ['active', 'inactive']);
            for (const value of items ?? []) {
                assert.strictEqual(value.textEdit, undefined);
                assert.strictEqual(value.insertTextFormat, undefined);
            }
        });
    });
});
