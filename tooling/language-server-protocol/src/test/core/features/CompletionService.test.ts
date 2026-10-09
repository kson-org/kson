import {describe, it} from 'mocha';
import assert from 'assert';
import {
    CompletionContext,
    CompletionItemKind,
    CompletionList,
    CompletionTriggerKind,
    Position
} from 'vscode-languageserver';
import {CompletionService, shouldOfferCompletions} from '../../../core/features/CompletionService.js';
import {createKsonDocument, pos, triggeredBy} from '../../TestHelpers.js';

/** The context of a completion request the user invoked explicitly, as with Ctrl+Space. */
const INVOKED: CompletionContext = {triggerKind: CompletionTriggerKind.Invoked};

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

    function getCompletions(
        content: string,
        position: Position,
        schema?: string,
        context?: CompletionContext
    ): CompletionList | null {
        const document = createKsonDocument(content, schema);
        return completionService.getCompletions(document, position, context);
    }

    function getCompletionLabels(
        content: string,
        position: Position,
        schema?: string,
        context?: CompletionContext
    ): string[] | null {
        const completions = getCompletions(content, position, schema, context);
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

    describe('when a newline or space is typed', () => {
        const SCHEMA = `{
            type: object
            properties: {
                status: {
                    type: string
                    enum: [active, inactive]
                }
                server: {
                    type: object
                    properties: {
                        host: { type: string }
                        port: { type: number }
                    }
                }
            }
        }`;

        it('should offer values once a space is typed after a colon', () => {
            const labels = getCompletionLabels('status: ', pos(0, 8), SCHEMA, triggeredBy(' '));
            assert.deepStrictEqual(labels, ['active', 'inactive']);
        });

        it('should offer property names once a space is typed after a colon', () => {
            // An object may follow inline, as in `server: { host: x }`
            const labels = getCompletionLabels('server: ', pos(0, 8), SCHEMA, triggeredBy(' '));
            assert.deepStrictEqual(labels, ['host', 'port']);
        });

        it('should offer nested property names once Enter is pressed after a colon', () => {
            // Auto-indent has followed the newline
            const labels = getCompletionLabels('server:\n  ', pos(1, 2), SCHEMA, triggeredBy('\n'));
            assert.deepStrictEqual(labels, ['host', 'port']);
        });

        it('should not answer a newline typed after a complete property', () => {
            const labels = getCompletionLabels('status: active\n', pos(1, 0), SCHEMA, triggeredBy('\n'));
            assert.strictEqual(labels, null);

            // Invoking completion at the same caret still offers the remaining property
            const invokedLabels = getCompletionLabels('status: active\n', pos(1, 0), SCHEMA, INVOKED);
            assert.deepStrictEqual(invokedLabels, ['server']);
        });

        it('should not answer a space typed before a value on the same line', () => {
            // Spacing out `status:active`
            const labels = getCompletionLabels('status: active', pos(0, 8), SCHEMA, triggeredBy(' '));
            assert.strictEqual(labels, null);

            // Invoking completion at the same caret still offers the values
            const invokedLabels = getCompletionLabels('status: active', pos(0, 8), SCHEMA, INVOKED);
            assert.deepStrictEqual(invokedLabels, ['active', 'inactive']);
        });

        it('should offer property names once a space is typed inside auto-closed braces', () => {
            // Only the rest of the caret's line needs to be blank, not the rest of the document
            const labels = getCompletionLabels('server: { }\nstatus: active', pos(0, 10), SCHEMA, triggeredBy(' '));
            assert.deepStrictEqual(labels, ['host', 'port']);
        });
    });
});

describe('shouldOfferCompletions', () => {
    it('should offer on a space typed after a colon', () => {
        assert.strictEqual(shouldOfferCompletions('status: ', '', triggeredBy(' ')), true);
    });

    it('should offer on a newline typed after a colon, with or without indentation', () => {
        assert.strictEqual(shouldOfferCompletions('server:\n', '', triggeredBy('\n')), true);
        assert.strictEqual(shouldOfferCompletions('server:\n\t', '', triggeredBy('\n')), true);
    });

    it('should offer on a newline typed after a colon and a space', () => {
        // Enter after `key: `, where the space offered only values
        assert.strictEqual(shouldOfferCompletions('server: \n  ', '', triggeredBy('\n')), true);
    });

    it('should offer on a newline typed after a colon in a CRLF document', () => {
        assert.strictEqual(shouldOfferCompletions('server:\r\n  ', '\r\n', triggeredBy('\n')), true);
    });

    it('should offer on a newline typed after an opening brace, past the auto-indent', () => {
        assert.strictEqual(shouldOfferCompletions('config: {\n  ', '', triggeredBy('\n')), true);
    });

    it('should offer on a space typed after an opening brace', () => {
        assert.strictEqual(shouldOfferCompletions('config: { ', '', triggeredBy(' ')), true);
    });

    it('should offer on a space typed after a comma', () => {
        assert.strictEqual(shouldOfferCompletions('{ name: x, ', '', triggeredBy(' ')), true);
    });

    it('should offer on a space typed after an opening bracket', () => {
        assert.strictEqual(shouldOfferCompletions('tags: [ ', '', triggeredBy(' ')), true);
    });

    it('should offer on a space typed before auto-closed braces and brackets', () => {
        assert.strictEqual(shouldOfferCompletions('config: { ', '}', triggeredBy(' ')), true);
        assert.strictEqual(shouldOfferCompletions('tags: [red, ', ']', triggeredBy(' ')), true);
        // Editors auto-close each nesting level
        assert.strictEqual(shouldOfferCompletions('{ tags: [ ', ']}', triggeredBy(' ')), true);
        assert.strictEqual(shouldOfferCompletions('matrix: [[ ', ']]', triggeredBy(' ')), true);
    });

    it('should offer on a space typed before a closing brace or bracket amid whitespace', () => {
        assert.strictEqual(shouldOfferCompletions('{ name: x, ', ' }', triggeredBy(' ')), true);
        assert.strictEqual(shouldOfferCompletions('tags: [ ', ']  ', triggeredBy(' ')), true);
    });

    it('should not offer on a space typed before a value, as when spacing out `status:active`', () => {
        assert.strictEqual(shouldOfferCompletions('status: ', 'active', triggeredBy(' ')), false);
    });

    it('should not offer on a space typed before a list item, as when spacing out `[red,green]`', () => {
        assert.strictEqual(shouldOfferCompletions('[red, ', 'green]', triggeredBy(' ')), false);
    });

    it('should not offer on a newline typed before a value', () => {
        assert.strictEqual(shouldOfferCompletions('status:\n  ', 'active', triggeredBy('\n')), false);
    });

    it('should not offer on a space typed after a word', () => {
        assert.strictEqual(shouldOfferCompletions('name: John ', '', triggeredBy(' ')), false);
    });

    it('should not offer on a newline typed after a complete property', () => {
        assert.strictEqual(shouldOfferCompletions('name: John\n  ', '', triggeredBy('\n')), false);
    });

    it('should offer on an explicit invocation wherever the caret is', () => {
        assert.strictEqual(shouldOfferCompletions('name: John\n  ', '', INVOKED), true);
        assert.strictEqual(shouldOfferCompletions('server: ', '', INVOKED), true);
        assert.strictEqual(shouldOfferCompletions('status: ', 'active', INVOKED), true);
    });

    it('should offer on a quote trigger wherever the caret is', () => {
        // The editor has auto-closed the quote
        assert.strictEqual(shouldOfferCompletions('name: "', '"', triggeredBy('"')), true);
    });

    it('should offer for a request that carries no context', () => {
        assert.strictEqual(shouldOfferCompletions('name: John\n  ', '', undefined), true);
    });
});
