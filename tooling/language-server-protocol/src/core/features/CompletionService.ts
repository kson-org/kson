import {
    CompletionContext,
    CompletionItem,
    CompletionItemKind,
    CompletionList,
    CompletionTriggerKind,
    Position,
    MarkupKind
} from 'vscode-languageserver';
import {KsonDocument} from '../document/KsonDocument.js';
import {isKsonSchemaDocument} from '../document/KsonSchemaDocument.js';
import {KsonTooling, CompletionItem as KsonCompletionItem, CompletionKind as KsonCompletionKind} from 'kson-tooling';

/**
 * Service for providing code completions based on JSON Schema.
 */
export class CompletionService {

    /**
     * Get completion suggestions for a position in a document.
     *
     * @param document The document to get completions for
     * @param position The position in the document
     * @param context How the request was triggered, if the client said; see {@link shouldOfferCompletions}
     * @returns Completion list, or null if none available
     */
    getCompletions(document: KsonDocument, position: Position, context?: CompletionContext): CompletionList | null {
        const textBeforeCaret = document.getText({start: {line: 0, character: 0}, end: position});
        const restOfLine = document.getText({start: position, end: {line: position.line + 1, character: 0}});
        if (!shouldOfferCompletions(textBeforeCaret, restOfLine, context)) {
            return null;
        }

        const schemaToolingDoc = isKsonSchemaDocument(document)
            ? document.getMetaSchemaToolingDocument()
            : document.getSchemaToolingDocument();
        if (!schemaToolingDoc) {
            return null;
        }

        const tooling = KsonTooling.getInstance();
        const ksonCompletions = tooling.getCompletionsAtLocation(
            document.getToolingDocument(),
            schemaToolingDoc,
            position.line,
            position.character
        );

        // Convert Kotlin completion items to LSP CompletionItem format
        const items = ksonCompletions?.asJsReadonlyArrayView()?.map(this.toLspCompletionItem);

        if (!items || items.length === 0) {
            return null;
        }

        return {
            isIncomplete: false,
            items: items
        };
    }

    /**
     * Convert a Kotlin CompletionItem to an LSP CompletionItem.
     *
     * @param ksonItem The Kotlin completion item
     * @returns LSP completion item
     */
    private toLspCompletionItem(ksonItem: KsonCompletionItem): CompletionItem {
        return {
            label: ksonItem.label,
            kind: mapCompletionKind(ksonItem.kind),
            detail: ksonItem.detail || undefined,
            documentation: ksonItem.documentation ? {
                kind: MarkupKind.Markdown,
                value: ksonItem.documentation
            } : undefined
        };
    }
}

/** The caret sits after `:`, `,`, `{` or `[` with only whitespace and auto-closed brackets around it. */
const CARET_AT_EMPTY_SLOT = {
    before: /[:,{[]\s*$/,
    after: /^[\s}\]]*$/,
};

/**
 * Whether to answer a completion request. A newline or space trigger is answered only at an empty
 * slot, so spacing out existing text such as `status:active` stays quiet. Any other request is
 * answered.
 *
 * @param textBeforeCaret The document text from its start up to the caret
 * @param restOfLine The document text from the caret to the end of its line
 * @param context How the request was triggered, if the client said
 */
export function shouldOfferCompletions(
    textBeforeCaret: string,
    restOfLine: string,
    context: CompletionContext | undefined
): boolean {
    const triggerCharacter = context?.triggerKind === CompletionTriggerKind.TriggerCharacter
        ? context.triggerCharacter
        : undefined;
    if (triggerCharacter !== '\n' && triggerCharacter !== ' ') {
        return true;
    }
    return CARET_AT_EMPTY_SLOT.before.test(textBeforeCaret) && CARET_AT_EMPTY_SLOT.after.test(restOfLine);
}

/**
 * Map Kotlin CompletionKind to LSP CompletionItemKind.
 *
 * @param ksonKind The Kotlin completion kind
 * @returns LSP completion item kind
 */
function mapCompletionKind(ksonKind: KsonCompletionKind): CompletionItemKind {
    switch (ksonKind.name) {
        case 'PROPERTY':
            return CompletionItemKind.Property;
        case 'VALUE':
            return CompletionItemKind.Value;
        default:
            return CompletionItemKind.Text;
    }
}