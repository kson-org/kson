import {describe, it} from 'mocha';
import * as assert from 'assert';
import {DidOpenTextDocumentParams} from 'vscode-languageserver';
import {TextDocument} from 'vscode-languageserver-textdocument';
import {ConnectionStub} from '../../ConnectionStub.js';
import {KsonDocumentsManager} from '../../../core/document/KsonDocumentsManager.js';
import {KsonDocument} from '../../../core/document/KsonDocument.js';
import {SchemaProviderTestStub} from '../../TestHelpers.js';

/**
 * Covers what {@link KsonDocumentsManager} decides when a document is opened, for instance which schema,
 * if any, the resulting {@link KsonDocument} carries.
 */
describe('KsonDocumentsManager', () => {

    const DOCUMENT_URI = 'file:///workspace/document.kson';
    const WORKSPACE_SCHEMA_URI = 'file:///workspace/schema.kson';

    /**
     * Open `document.kson` against a provider offering it the given schema, if any, and return the
     * {@link KsonDocument} the manager built.
     */
    function openDocument(documentContent: string, schema?: TextDocument): KsonDocument {
        const schemaProvider = new SchemaProviderTestStub();
        if (schema) {
            schemaProvider.addSchema(DOCUMENT_URI, schema);
        }

        const documentsManager = new KsonDocumentsManager(schemaProvider);
        const connection = new ConnectionStub();
        documentsManager.listen(connection);

        const params: DidOpenTextDocumentParams = {
            textDocument: {uri: DOCUMENT_URI, languageId: 'kson', version: 1, text: documentContent}
        };
        connection.didOpenHandler(params);

        const ksonDocument = documentsManager.get(DOCUMENT_URI);
        assert.ok(ksonDocument, 'the opened document should be tracked by the manager');
        return ksonDocument;
    }

    describe('schema attachment', () => {
        it('should attach the schema its provider offers', () => {
            const schema = TextDocument.create(WORKSPACE_SCHEMA_URI, 'kson', 1, '{ type: object }');

            const document = openDocument('{ name: "Alice" }', schema);

            assert.strictEqual(document.getSchemaDocument(), schema);
        });

        it('should attach no schema when its provider offers none', () => {
            const document = openDocument('{ name: "Alice" }');

            assert.strictEqual(document.getSchemaDocument(), undefined);
        });
    });
});
