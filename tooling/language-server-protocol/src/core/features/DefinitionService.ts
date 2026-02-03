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
    getDefinition(document: KsonDocument, position: Position): DefinitionLink[] {
        const tooling = KsonTooling.getInstance();
        const results: DefinitionLink[] = [];

        // Try $ref resolution within the same document
        const refLocations = tooling.resolveRefAtLocation(
            document.getText(),
            position.line,
            position.character
        );
        results.push(...this.convertRangesToDefinitionLinks(refLocations, document.uri));

        // Try document-to-schema navigation (if schema is configured)
        const schemaDocument = document.getSchemaDocument();
        if (schemaDocument) {
            const locations = tooling.getSchemaLocationAtLocation(
                document.getText(),
                schemaDocument.getText(),
                position.line,
                position.character
            );
            results.push(...this.convertRangesToDefinitionLinks(locations, schemaDocument.uri));
        }

        return results;
    }

    /**
     * Convert Range objects to DefinitionLink objects.
     *
     * @param locations The Range objects from KsonTooling
     * @param targetUri The URI of the document containing the definitions
     * @returns Array of DefinitionLink objects
     */
    private convertRangesToDefinitionLinks(locations: any, targetUri: string): DefinitionLink[] {
        return locations.asJsReadonlyArrayView().map((location: any) => {
            const targetRange: Range = {
                start: {
                    line: location.startLine,
                    character: location.startColumn
                },
                end: {
                    line: location.endLine,
                    character: location.endColumn
                }
            };
            const definition: DefinitionLink = {
                targetUri: targetUri,
                targetRange: targetRange,
                targetSelectionRange: targetRange
            }
            return definition;
        });
    }
}
