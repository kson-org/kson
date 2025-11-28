import {Hover, Position, MarkupKind} from 'vscode-languageserver';
import {KsonDocument} from '../document/KsonDocument.js';
import {KsonTooling} from 'kson-tooling';

/**
 * Service for providing hover information based on JSON Schema.
 */
export class HoverService {

    /**
     * Get hover information for a position in a document.
     *
     * @param document The document to get hover info for
     * @param position The position in the document
     * @returns Hover information, or null if none available
     */
    getHover(document: KsonDocument, position: Position): Hover | null {
        // Get the schema for this document
        const schemaDocument = document.getSchemaDocument();
        if (!schemaDocument) {
            // No schema configured, no hover info available
            return null;
        }

        // Call the KsonTooling API to get schema hover info at this position
        // The API now accepts line and column directly, avoiding the Location mangling issue
        const tooling = KsonTooling.getInstance();
        const hoverMarkdown = tooling.getSchemaInfoAtLocation(
            document.getText(),
            schemaDocument.getText(),
            position.line,
            position.character
        );

        if (!hoverMarkdown) {
            return null;
        }

        // Return LSP Hover response with markdown content
        return {
            contents: {
                kind: MarkupKind.Markdown,
                value: hoverMarkdown
            }
        };
    }
}
