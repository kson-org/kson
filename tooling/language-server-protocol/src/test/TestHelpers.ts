import {TextDocument} from 'vscode-languageserver-textdocument';
import {Position} from 'vscode-languageserver';
import {KsonTooling} from 'kson-tooling';
import {KsonDocument} from '../core/document/KsonDocument.js';
import {KsonSchemaDocument} from '../core/document/KsonSchemaDocument.js';

export const TEST_URI = 'file:///test.kson';
export const SCHEMA_URI = 'file:///schema.kson';

/**
 * Create a KsonDocument from raw content, optionally with a schema.
 */
export function createKsonDocument(content: string, schemaContent?: string): KsonDocument {
    const textDoc = TextDocument.create(TEST_URI, 'kson', 1, content);
    const toolingDoc = KsonTooling.getInstance().parse(content);
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
    const toolingDoc = KsonTooling.getInstance().parse(content);
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
