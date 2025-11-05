import {DefinitionLink, Position, Range} from 'vscode-languageserver';
import {KsonDocument} from '../document/KsonDocument.js';
import {KsonTooling} from 'kson-tooling';

/**
 * Service for providing "go to definition" functionality.
 *
 * This service enables users to jump from a symbol in a KSON document
 * to its definition in the associated schema file.
 */
export class DefinitionService {

    /**
     * Get the definition location for a symbol at the given position.
     *
     * @param document The document to find definitions in
     * @param position The position of the symbol
     * @returns Array of DefinitionLink objects, or null if no definition found
     */
    getDefinition(document: KsonDocument, position: Position): DefinitionLink[] | null {
        // Get the schema for this document
        const schemaDocument = document.getSchemaDocument();
        if (!schemaDocument) {
            // No schema configured, no definition available
            return null;
        }

        // Call the KsonTooling API to get the definition location in the schema
        const tooling = KsonTooling.getInstance();
        const location = tooling.getSchemaLocationAtLocation(
            document.getText(),
            schemaDocument.getText(),
            position.line,
            position.character
        );

        if (!location) {
            // No definition found at this position
            return null;
        }

        // Convert the location from kson-tooling-lib to LSP DefinitionLink format
        const targetRange: Range = {
            start: {
                line: location.startLine,
                character: location.startColumn
            },
            end: {
                line: location.endLine,
                character: location.startColumn
            }
        };

        // Return as an array of DefinitionLink
        // We use the same range for both targetRange and targetSelectionRange
        // targetRange is the full range of the definition
        // targetSelectionRange is the range of the identifier within the definition
        return [{
            targetUri: schemaDocument.uri,
            targetRange: targetRange,
            targetSelectionRange: targetRange
        }];
    }
}
