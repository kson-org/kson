import {TextDocument} from 'vscode-languageserver-textdocument';
import {DocumentUri, Position} from 'vscode-languageserver';
import {SchemaProvider} from '../core/schema/SchemaProvider.js';
import {KsonDocument, parseTextDocument} from '../core/document/KsonDocument.js';
import {KsonSchemaDocument} from '../core/document/KsonSchemaDocument.js';

export const TEST_URI = 'file:///test.kson';
export const SCHEMA_URI = 'file:///schema.kson';

/**
 * Create a KsonDocument from raw content, optionally with a schema.
 */
export function createKsonDocument(content: string, schemaContent?: string): KsonDocument {
    const textDoc = TextDocument.create(TEST_URI, 'kson', 1, content);
    const toolingDoc = parseTextDocument(textDoc);
    const schemaDoc = schemaContent
        ? TextDocument.create(SCHEMA_URI, 'kson', 1, schemaContent)
        : undefined;
    return new KsonDocument(textDoc, toolingDoc, schemaDoc);
}

/**
 * Create a KsonSchemaDocument (a schema file that has a metaschema).
 */
export function createKsonSchemaDocument(content: string, metaSchemaContent?: string): KsonSchemaDocument {
    const textDoc = TextDocument.create(SCHEMA_URI, 'kson', 1, content);
    const toolingDoc = parseTextDocument(textDoc);
    const metaSchemaDoc = metaSchemaContent
        ? TextDocument.create('bundled://metaschema/draft-07.schema.kson', 'kson', 1, metaSchemaContent)
        : undefined;
    return new KsonSchemaDocument(textDoc, toolingDoc, metaSchemaDoc);
}

/**
 * Shorthand for creating a Position.
 */
export function pos(line: number, character: number): Position {
    return {line, character};
}

/**
 * Stub {@link SchemaProvider} whose document-to-schema mappings are registered directly, for tests that
 * want a provider's answers without the disk a real one needs.
 */
export class SchemaProviderTestStub implements SchemaProvider {
    private schemas: Map<string, TextDocument> = new Map();

    addSchema(documentUri: DocumentUri, schema: TextDocument): void {
        this.schemas.set(documentUri, schema);
    }

    getSchemaForDocument(documentUri: DocumentUri): TextDocument | undefined {
        return this.schemas.get(documentUri);
    }

    getMetaSchemaForId(_schemaId: string): TextDocument | undefined {
        return undefined;
    }

    reload(): void {}

    isSchemaFile(fileUri: DocumentUri): boolean {
        for (const schema of this.schemas.values()) {
            if (schema.uri === fileUri) return true;
        }
        return false;
    }
}
