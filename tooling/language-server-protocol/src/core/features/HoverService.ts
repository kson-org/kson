import {Hover, Position, MarkupKind} from 'vscode-languageserver';
import {KsonDocument} from '../document/KsonDocument.js';
import {isKsonSchemaDocument} from '../document/KsonSchemaDocument.js';
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
        // Get the schema ToolingDocument for this document
        const schemaToolingDoc = isKsonSchemaDocument(document)
            ? document.getMetaSchemaToolingDocument()
            : document.getSchemaToolingDocument();
        if (!schemaToolingDoc) {
            return null;
        }

        const tooling = KsonTooling.getInstance();
        const hoverMarkdown = tooling.getSchemaInfoAtLocation(
            document.getToolingDocument(),
            schemaToolingDoc,
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
