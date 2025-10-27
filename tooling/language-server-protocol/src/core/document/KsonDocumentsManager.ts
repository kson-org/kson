import {TextDocument} from 'vscode-languageserver-textdocument';
import {Kson} from 'kson';
import {KsonDocument} from "./KsonDocument.js";
import {DocumentUri, TextDocuments, TextDocumentContentChangeEvent} from "vscode-languageserver";

/**
 * A simple hardcoded test schema for MVP demonstration.
 * This schema describes a basic configuration object with name, port, and enabled properties.
 */
const HARDCODED_TEST_SCHEMA = `{
  "type": "object",
  "properties": {
    "name": {
      "type": "string",
      "description": "The name of the service",
      "title": "Service Name"
    },
    "port": {
      "type": "number",
      "description": "The port number the service listens on",
      "title": "Port Number",
      "minimum": 1024,
      "maximum": 65535
    },
    "enabled": {
      "type": "boolean",
      "description": "Whether the service is enabled",
      "title": "Enabled Flag"
    }
  }
}`;

/**
 * Document management for the Kson Language Server.
 * The {@link KsonDocumentsManager} keeps track of all {@link KsonDocument}'s that
 * we are watching. It extends {@link TextDocuments} to handle {@link KsonDocument}
 * instances.
 */
export class KsonDocumentsManager extends TextDocuments<KsonDocument> {

    constructor() {
        super({
            create: (
                uri: DocumentUri,
                languageId: string,
                version: number,
                content: string
            ): KsonDocument => {
                const textDocument = TextDocument.create(uri, languageId, version, content);
                const schemaDocument = TextDocument.create("tmp", languageId, version, HARDCODED_TEST_SCHEMA)
                const parseResult = Kson.getInstance().analyze(content);
                return new KsonDocument(textDocument, parseResult, schemaDocument);
            },
            update: (
                ksonDocument: KsonDocument,
                changes: TextDocumentContentChangeEvent[],
                version: number,
            ): KsonDocument => {
                const textDocument = TextDocument.update(
                    ksonDocument.textDocument,
                    changes,
                    version
                );
                const parseResult = Kson.getInstance().analyze(textDocument.getText());
                return new KsonDocument(
                    textDocument,
                    parseResult,
                    ksonDocument.getSchemaDocument()
                );
            }
        });
    }
}
