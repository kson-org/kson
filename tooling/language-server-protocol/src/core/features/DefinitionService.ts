import {DefinitionLink, Position, Range} from 'vscode-languageserver';
import {KsonDocument} from '../document/KsonDocument.js';
import {isKsonSchemaDocument} from '../document/KsonSchemaDocument.js';
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

        // Try $ref resolution within the same document (uses document as schema)
        const refLocations = tooling.resolveRefAtLocation(
            document.getToolingDocument(),
            position.line,
            position.character
        );
        results.push(...this.convertRangesToDefinitionLinks(refLocations, document.uri));

        // Try document-to-schema navigation (if schema is configured)
        const schemaToolingDoc = isKsonSchemaDocument(document)
            ? document.getMetaSchemaToolingDocument()
            : document.getSchemaToolingDocument();
        if (schemaToolingDoc) {
            const schemaUri = isKsonSchemaDocument(document)
                ? document.getMetaSchemaDocument()!.uri
                : document.getSchemaDocument()!.uri;
            const locations = tooling.getSchemaLocationAtLocation(
                document.getToolingDocument(),
                schemaToolingDoc,
                position.line,
                position.character
            );
            results.push(...this.convertRangesToDefinitionLinks(locations, schemaUri));
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
