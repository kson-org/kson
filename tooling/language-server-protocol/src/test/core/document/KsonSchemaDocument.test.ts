import {describe, it} from 'mocha';
import * as assert from 'assert';
import {Kson} from 'kson';
import {TextDocument} from 'vscode-languageserver-textdocument';
import {KsonDocument} from '../../../core/document/KsonDocument.js';
import {KsonSchemaDocument, isKsonSchemaDocument} from '../../../core/document/KsonSchemaDocument.js';

describe('KsonSchemaDocument', () => {
    const metaSchemaContent = `{
    "$id": "http://json-schema.org/draft-07/schema#",
    "type": "object"
}`;
    const metaSchemaDocument = TextDocument.create(
        'bundled://metaschema/draft-07.schema.kson', 'kson', 1, metaSchemaContent
    );

    const schemaContent = `{
    "$schema": "http://json-schema.org/draft-07/schema#",
    "type": "object",
    "properties": {
        "name": { "type": "string" }
    }
}`;

    function createSchemaDocument(metaSchema?: TextDocument): KsonSchemaDocument {
        const textDoc = TextDocument.create('file:///my-schema.kson', 'kson', 1, schemaContent);
        const analysis = Kson.getInstance().analyze(schemaContent);
        return new KsonSchemaDocument(textDoc, analysis, metaSchema);
    }

    it('should return the metaschema document', () => {
        const doc = createSchemaDocument(metaSchemaDocument);
        assert.strictEqual(doc.getMetaSchemaDocument(), metaSchemaDocument);
    });

    it('should return undefined metaschema when none provided', () => {
        const doc = createSchemaDocument(undefined);
        assert.strictEqual(doc.getMetaSchemaDocument(), undefined);
    });

    it('should return undefined for getSchemaDocument (no schema passed to parent)', () => {
        const doc = createSchemaDocument(metaSchemaDocument);
        assert.strictEqual(doc.getSchemaDocument(), undefined);
    });

    it('should have all KsonDocument functionality', () => {
        const doc = createSchemaDocument(metaSchemaDocument);

        assert.strictEqual(doc.uri, 'file:///my-schema.kson');
        assert.strictEqual(doc.languageId, 'kson');
        assert.strictEqual(doc.version, 1);
        assert.ok(doc.getText().includes('"$schema"'));
        assert.ok(doc.getAnalysisResult());
        assert.ok(doc.getFullDocumentRange());
        assert.ok(doc.lineCount > 0);
    });

    describe('isKsonSchemaDocument type guard', () => {
        it('should return true for KsonSchemaDocument', () => {
            const doc = createSchemaDocument(metaSchemaDocument);
            assert.strictEqual(isKsonSchemaDocument(doc), true);
        });

        it('should return false for plain KsonDocument', () => {
            const content = '{ "name": "test" }';
            const textDoc = TextDocument.create('file:///test.kson', 'kson', 1, content);
            const analysis = Kson.getInstance().analyze(content);
            const doc = new KsonDocument(textDoc, analysis);
            assert.strictEqual(isKsonSchemaDocument(doc), false);
        });

        it('should return false for KsonDocument with schema', () => {
            const content = '{ "name": "test" }';
            const textDoc = TextDocument.create('file:///test.kson', 'kson', 1, content);
            const analysis = Kson.getInstance().analyze(content);
            const schemaDoc = TextDocument.create('file:///schema.kson', 'kson', 1, '{ "type": "object" }');
            const doc = new KsonDocument(textDoc, analysis, schemaDoc);
            assert.strictEqual(isKsonSchemaDocument(doc), false);
        });
    });
});

describe('KsonDocument.getSchemaId', () => {
    it('should extract $schema from document with $schema field', () => {
        const content = `{
    "$schema": "http://json-schema.org/draft-07/schema#",
    "type": "object"
}`;
        const textDoc = TextDocument.create('file:///test.kson', 'kson', 1, content);
        const analysis = Kson.getInstance().analyze(content);
        const doc = new KsonDocument(textDoc, analysis);

        assert.strictEqual(doc.getSchemaId(), 'http://json-schema.org/draft-07/schema#');
    });

    it('should return undefined when no $schema field', () => {
        const content = '{ "type": "object" }';
        const textDoc = TextDocument.create('file:///test.kson', 'kson', 1, content);
        const analysis = Kson.getInstance().analyze(content);
        const doc = new KsonDocument(textDoc, analysis);

        assert.strictEqual(doc.getSchemaId(), undefined);
    });

    it('should return undefined for non-object document', () => {
        const content = '"just a string"';
        const textDoc = TextDocument.create('file:///test.kson', 'kson', 1, content);
        const analysis = Kson.getInstance().analyze(content);
        const doc = new KsonDocument(textDoc, analysis);

        assert.strictEqual(doc.getSchemaId(), undefined);
    });

    it('should return undefined when $schema is not a string', () => {
        const content = '{ "$schema": 42 }';
        const textDoc = TextDocument.create('file:///test.kson', 'kson', 1, content);
        const analysis = Kson.getInstance().analyze(content);
        const doc = new KsonDocument(textDoc, analysis);

        assert.strictEqual(doc.getSchemaId(), undefined);
    });
});

describe('KsonDocument.extractSchemaId (static)', () => {
    it('should extract $schema from analysis', () => {
        const content = `{ "$schema": "http://json-schema.org/draft-07/schema#" }`;
        const analysis = Kson.getInstance().analyze(content);

        assert.strictEqual(KsonDocument.extractSchemaId(analysis), 'http://json-schema.org/draft-07/schema#');
    });

    it('should return undefined for analysis without $schema', () => {
        const content = '{ "type": "object" }';
        const analysis = Kson.getInstance().analyze(content);

        assert.strictEqual(KsonDocument.extractSchemaId(analysis), undefined);
    });
});
