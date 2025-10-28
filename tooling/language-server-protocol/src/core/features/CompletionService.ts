import {CompletionItem, CompletionItemKind, CompletionList, Position, MarkupKind} from 'vscode-languageserver';
import {KsonDocument} from '../document/KsonDocument.js';
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
     * @returns Completion list, or null if none available
     */
    getCompletions(document: KsonDocument, position: Position): CompletionList | null {
        // Get the schema for this document
        const schemaDocument = document.getSchemaDocument();
        if (!schemaDocument) {
            // No schema configured, no completions available
            return null;
        }

        // Call the KsonTooling API to get completions at this position
        const tooling = KsonTooling.getInstance();
        const ksonCompletions = tooling.getCompletionsAtLocation(
            document.getText(),
            schemaDocument.getText(),
            position.line,
            position.character
        );

        if (!ksonCompletions || ksonCompletions.length === 0) {
            return null;
        }

        // Convert Kotlin completion items to LSP CompletionItem format
        const items = ksonCompletions.asJsReadonlyArrayView().map(this.toLspCompletionItem);

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