import assert from 'assert';
import { DocumentSymbolService } from '../../../core/features/DocumentSymbolService.js';
import { KsonDocument } from '../../../core/document/KsonDocument.js';
import { TextDocument } from 'vscode-languageserver-textdocument';
import { Kson } from 'kson';
import { SymbolKind } from 'vscode-languageserver';
import {IndexedDocumentSymbols} from "../../../core/features/IndexedDocumentSymbols";

describe('DocumentSymbolService', () => {
    const documentSymbolService = new DocumentSymbolService();
    const kson = Kson.getInstance();

    function createKsonDocument(content: string): KsonDocument {
        const textDocument = TextDocument.create(
            'file:///test.kson',
            'kson',
            1,
            content
        );
        const analysis = kson.analyze(content);
        return new KsonDocument(textDocument, analysis);
    }

    it('should return empty array for invalid document', () => {
        const document = createKsonDocument('{ invalid json }');
        const symbols = documentSymbolService.getDocumentSymbols(document.getAnalysisResult().ksonValue);
        
        assert.deepStrictEqual(symbols, []);
    });

    it('should create symbols for simple object', () => {
        const content = `{
            "name": "John",
            "age": 30,
            "active": true
        }`;
        const document = createKsonDocument(content);
        const symbols = documentSymbolService.getDocumentSymbols(document.getAnalysisResult().ksonValue);
        
        assert.strictEqual(symbols.length, 1);
        assert.strictEqual(symbols[0].name, 'root');
        assert.strictEqual(symbols[0].kind, SymbolKind.Object);
        assert.strictEqual(symbols[0].children!.length, 3);
        
        const [nameSymbol, ageSymbol, activeSymbol] = symbols[0].children!;
        
        assert.strictEqual(nameSymbol.name, 'name');
        assert.strictEqual(nameSymbol.kind, SymbolKind.Key);
        assert.strictEqual(nameSymbol.detail, 'key');
        assert.strictEqual(nameSymbol.children!.length, 1);
        assert.strictEqual(nameSymbol.children![0].kind, SymbolKind.String);
        assert.strictEqual(nameSymbol.children![0].detail, 'John');
        
        assert.strictEqual(ageSymbol.name, 'age');
        assert.strictEqual(ageSymbol.kind, SymbolKind.Key);
        assert.strictEqual(ageSymbol.detail, 'key');
        assert.strictEqual(ageSymbol.children!.length, 1);
        assert.strictEqual(ageSymbol.children![0].kind, SymbolKind.Number);
        assert.strictEqual(ageSymbol.children![0].detail, '30');
        
        assert.strictEqual(activeSymbol.name, 'active');
        assert.strictEqual(activeSymbol.kind, SymbolKind.Key);
        assert.strictEqual(activeSymbol.detail, 'key');
        assert.strictEqual(activeSymbol.children!.length, 1);
        assert.strictEqual(activeSymbol.children![0].kind, SymbolKind.Boolean);
        assert.strictEqual(activeSymbol.children![0].detail, 'true');
    });

    it('should create symbols for nested objects', () => {
        const content = `{
            "user": {
                "name": "Jane",
                "settings": {
                    "theme": "dark"
                }
            }
        }`;
        const document = createKsonDocument(content);
        const symbols = documentSymbolService.getDocumentSymbols(document.getAnalysisResult().ksonValue);
        
        assert.strictEqual(symbols.length, 1);
        const rootSymbol = symbols[0];
        assert.strictEqual(rootSymbol.children!.length, 1);
        
        const userSymbol = rootSymbol.children![0];
        assert.strictEqual(userSymbol.name, 'user');
        assert.strictEqual(userSymbol.kind, SymbolKind.Key);
        assert.strictEqual(userSymbol.detail, 'key');
        assert.strictEqual(userSymbol.children!.length, 1);
        
        const userObjectSymbol = userSymbol.children![0];
        assert.strictEqual(userObjectSymbol.kind, SymbolKind.Object);
        assert.strictEqual(userObjectSymbol.children!.length, 2);
        
        const nameSymbol = userObjectSymbol.children![0];
        assert.strictEqual(nameSymbol.name, 'name');
        assert.strictEqual(nameSymbol.kind, SymbolKind.Key);
        
        const settingsSymbol = userObjectSymbol.children![1];
        assert.strictEqual(settingsSymbol.name, 'settings');
        assert.strictEqual(settingsSymbol.kind, SymbolKind.Key);
        assert.strictEqual(settingsSymbol.children!.length, 1);
        
        const settingsObjectSymbol = settingsSymbol.children![0];
        assert.strictEqual(settingsObjectSymbol.kind, SymbolKind.Object);
        assert.strictEqual(settingsObjectSymbol.children!.length, 1);
        assert.strictEqual(settingsObjectSymbol.children![0].name, 'theme');
    });

    it('should create symbols for arrays', () => {
        const content = `{
            "items": [
                "apple",
                42,
                null,
                { "nested": true }
            ]
        }`;
        const document = createKsonDocument(content);
        const symbols = documentSymbolService.getDocumentSymbols(document.getAnalysisResult().ksonValue);
        
        const rootSymbol = symbols[0];
        const itemsSymbol = rootSymbol.children![0];
        
        assert.strictEqual(itemsSymbol.name, 'items');
        assert.strictEqual(itemsSymbol.kind, SymbolKind.Key);
        assert.strictEqual(itemsSymbol.detail, 'key');
        assert.strictEqual(itemsSymbol.children!.length, 1);
        
        const itemsArraySymbol = itemsSymbol.children![0];
        assert.strictEqual(itemsArraySymbol.kind, SymbolKind.Array);
        assert.strictEqual(itemsArraySymbol.detail, '[4 items]');
        assert.strictEqual(itemsArraySymbol.children!.length, 4);
        
        assert.strictEqual(itemsArraySymbol.children![0].name, '[0]');
        assert.strictEqual(itemsArraySymbol.children![0].kind, SymbolKind.String);
        
        assert.strictEqual(itemsArraySymbol.children![1].name, '[1]');
        assert.strictEqual(itemsArraySymbol.children![1].kind, SymbolKind.Number);
        
        assert.strictEqual(itemsArraySymbol.children![2].name, '[2]');
        assert.strictEqual(itemsArraySymbol.children![2].kind, SymbolKind.Null);
        
        assert.strictEqual(itemsArraySymbol.children![3].name, '[3]');
        assert.strictEqual(itemsArraySymbol.children![3].kind, SymbolKind.Object);
    });

    it('should handle empty objects and arrays', () => {
        const content = `{
            "emptyObject": {},
            "emptyArray": []
        }`;
        const document = createKsonDocument(content);
        const symbols = documentSymbolService.getDocumentSymbols(document.getAnalysisResult().ksonValue);
        
        const rootSymbol = symbols[0];
        assert.strictEqual(rootSymbol.children!.length, 2);
        
        const emptyObjectSymbol = rootSymbol.children![0];
        assert.strictEqual(emptyObjectSymbol.name, 'emptyObject');
        assert.strictEqual(emptyObjectSymbol.kind, SymbolKind.Key);
        assert.strictEqual(emptyObjectSymbol.detail, 'key');
        assert.strictEqual(emptyObjectSymbol.children!.length, 1);
        assert.strictEqual(emptyObjectSymbol.children![0].kind, SymbolKind.Object);
        assert.strictEqual(emptyObjectSymbol.children![0].detail, '{0 properties}');
        assert.strictEqual(emptyObjectSymbol.children![0].children!.length, 0);
        
        const emptyArraySymbol = rootSymbol.children![1];
        assert.strictEqual(emptyArraySymbol.name, 'emptyArray');
        assert.strictEqual(emptyArraySymbol.kind, SymbolKind.Key);
        assert.strictEqual(emptyArraySymbol.detail, 'key');
        assert.strictEqual(emptyArraySymbol.children!.length, 1);
        assert.strictEqual(emptyArraySymbol.children![0].kind, SymbolKind.Array);
        assert.strictEqual(emptyArraySymbol.children![0].detail, '[0 items]');
        assert.strictEqual(emptyArraySymbol.children![0].children!.length, 0);
    });

    it('should create symbols for root array', () => {
        const content = `["item1", "item2", "item3"]`;
        const document = createKsonDocument(content);
        const symbols = documentSymbolService.getDocumentSymbols(document.getAnalysisResult().ksonValue);
        
        assert.strictEqual(symbols.length, 1);
        const rootSymbol = symbols[0];
        assert.strictEqual(rootSymbol.name, 'root');
        assert.strictEqual(rootSymbol.kind, SymbolKind.Array);
        assert.strictEqual(rootSymbol.children!.length, 3);
    });

    it('should create symbols for primitive root values', () => {
        const stringDoc = createKsonDocument('"hello"');
        const stringSymbols = documentSymbolService.getDocumentSymbols(stringDoc.getAnalysisResult().ksonValue);
        assert.strictEqual(stringSymbols.length, 1);
        assert.strictEqual(stringSymbols[0].kind, SymbolKind.String);
        assert.strictEqual(stringSymbols[0].detail, 'hello');
        
        const numberDoc = createKsonDocument('42');
        const numberSymbols = documentSymbolService.getDocumentSymbols(numberDoc.getAnalysisResult().ksonValue);
        assert.strictEqual(numberSymbols.length, 1);
        assert.strictEqual(numberSymbols[0].kind, SymbolKind.Number);
        assert.strictEqual(numberSymbols[0].detail, '42');
        
        const boolDoc = createKsonDocument('false');
        const boolSymbols = documentSymbolService.getDocumentSymbols(boolDoc.getAnalysisResult().ksonValue);
        assert.strictEqual(boolSymbols.length, 1);
        assert.strictEqual(boolSymbols[0].kind, SymbolKind.Boolean);
        assert.strictEqual(boolSymbols[0].detail, 'false');
        
        const nullDoc = createKsonDocument('null');
        const nullSymbols = documentSymbolService.getDocumentSymbols(nullDoc.getAnalysisResult().ksonValue);
        assert.strictEqual(nullSymbols.length, 1);
        assert.strictEqual(nullSymbols[0].kind, SymbolKind.Null);
        assert.strictEqual(nullSymbols[0].detail, 'null');
    });
    
    it('should work with parent pointers in SymbolPositionIndex', () => {
        const content = `{
            "user": {
                "name": "John",
                "address": {
                    "city": "New York"
                }
            }
        }`;
        const document = createKsonDocument(content);
        const documentSymbols = documentSymbolService.getDocumentSymbols(document.getAnalysisResult().ksonValue);

        // Set the symbols with index on the document
        document.setSymbolsWithIndex(new IndexedDocumentSymbols(documentSymbols))
        const symbolsWithIndex = document.getSymbolsWithIndex()
        const symbols = symbolsWithIndex.getDocumentSymbols()
        // Find the city symbol - let's debug the structure
        const rootSymbol = symbols[0];
        assert.strictEqual(rootSymbol.name, 'root');
        assert.strictEqual(rootSymbol.children!.length, 1);
        
        const userProperty = rootSymbol.children![0];
        assert.strictEqual(userProperty.name, 'user');
        assert.strictEqual(userProperty.children!.length, 1);
        
        const userObject = userProperty.children![0];
        assert.strictEqual(userObject.kind, SymbolKind.Object);
        assert.strictEqual(userObject.children!.length, 2);
        
        const nameProperty = userObject.children![0];
        assert.strictEqual(nameProperty.name, 'name');
        assert.strictEqual(nameProperty.kind, SymbolKind.Key);
        
        const addressProperty = userObject.children![1];
        assert.strictEqual(addressProperty.name, 'address');
        assert.strictEqual(addressProperty.kind, SymbolKind.Key);
        assert.strictEqual(addressProperty.children!.length, 1);
        
        const addressObject = addressProperty.children![0];
        assert.strictEqual(addressObject.kind, SymbolKind.Object);
        assert.strictEqual(addressObject.children!.length, 1);
        
        const cityProperty = addressObject.children![0];
        assert.strictEqual(cityProperty.name, 'city');
        assert.strictEqual(cityProperty.children!.length, 1);
        
        const cityString = cityProperty.children![0];
        assert.strictEqual(cityString.kind, SymbolKind.String);

        // Test parent lookup using the index
        const cityStringParent = cityString.parent;
        assert.notStrictEqual(cityStringParent, null, 'Should find parent of cityString');
        assert.strictEqual(cityStringParent!.name, 'city');

        const cityPropertyParent = cityProperty.parent;
        assert.notStrictEqual(cityPropertyParent, null);
        assert.strictEqual(cityPropertyParent!.name, 'address');

        const addressObjectParent = addressObject.parent;
        assert.notStrictEqual(addressObjectParent, null);
        assert.strictEqual(addressObjectParent!.name, 'address');
    });
});