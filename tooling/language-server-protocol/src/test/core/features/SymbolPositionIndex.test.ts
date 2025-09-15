import { describe, it } from 'mocha';
import * as assert from 'assert';
import { IndexedDocumentSymbols } from '../../../core/features/IndexedDocumentSymbols';
import { DocumentSymbolWithParent } from '../../../core/features/DocumentSymbolService.js';
import { SymbolKind, Position } from 'vscode-languageserver';

describe('SymbolPositionIndex', () => {
    // Helper to create a symbol with clear parameters
    const createSymbol = (
        name: string,
        kind: SymbolKind,
        startLine: number,
        startChar: number,
        endLine: number,
        endChar: number,
        children?: DocumentSymbolWithParent[]
    ): DocumentSymbolWithParent => {
        const symbol: DocumentSymbolWithParent = {
            name,
            kind,
            range: {
                start: { line: startLine, character: startChar },
                end: { line: endLine, character: endChar }
            },
            selectionRange: {
                start: { line: startLine, character: startChar },
                end: { line: endLine, character: endChar }
            },
            children,
            parent: null // Root symbols have no parent
        };

        // Set parent reference for all children
        if (children) {
            children.forEach(child => {
                child.parent = symbol;
            });
        }

        return symbol;
    };

    // Helper to create a position using the official Position.create method
    const pos = (line: number, character: number): Position => Position.create(line, character);

    // Helper to extract symbol names from an array of symbols
    const getSymbolNames = (symbols: DocumentSymbolWithParent[]): string[] =>
        symbols.map(s => s.name);

    // Helper to assert arrays contain same elements (order doesn't matter)
    const assertContainsSameElements = (actual: string[], expected: string[], message?: string) => {
        assert.strictEqual(actual.length, expected.length,
            message || `Expected ${expected.length} elements but got ${actual.length}`);
        for (const item of expected) {
            assert.ok(actual.includes(item),
                message || `Missing expected element: ${item}`);
        }
    };

    describe('constructor', () => {
        it('should handle empty symbols array', () => {
            const index = new IndexedDocumentSymbols([]);
            const symbols = index.findSymbolsAtPosition(pos(0, 0));
            assert.strictEqual(symbols.length, 0);
        });

        it('should handle single root symbol', () => {
            const rootSymbol = createSymbol('root', SymbolKind.Object, 0, 0, 5, 0);
            const index = new IndexedDocumentSymbols([rootSymbol]);

            const symbols = index.findSymbolsAtPosition(pos(2, 0));
            assert.strictEqual(symbols.length, 1);
            assert.strictEqual(symbols[0].name, 'root');
        });

        it('should handle multiple root symbols', () => {
            // Visual representation:
            // Line 0-2: firstRoot
            // Line 3-5: secondRoot
            // Line 6-8: thirdRoot
            const symbols = [
                createSymbol('firstRoot', SymbolKind.Object, 0, 0, 2, 0),
                createSymbol('secondRoot', SymbolKind.Object, 3, 0, 5, 0),
                createSymbol('thirdRoot', SymbolKind.Object, 6, 0, 8, 0)
            ];

            const index = new IndexedDocumentSymbols(symbols);

            // Test position in second root
            const found = index.findSymbolsAtPosition(pos(4, 0));
            assert.strictEqual(found.length, 1);
            assert.strictEqual(found[0].name, 'secondRoot');
        });

        it('should handle deeply nested symbols', () => {
            // Visual representation:
            // Line 0-10: grandparent {
            //   Line 1-9:  parent {
            //     Line 2-8:   child {
            //       Line 3-7:    grandchild {
            //         Line 4-6:     greatGrandchild
            //       }
            //     }
            //   }
            // }
            const greatGrandchild = createSymbol('greatGrandchild', SymbolKind.Property, 4, 0, 6, 0);
            const grandchild = createSymbol('grandchild', SymbolKind.Object, 3, 0, 7, 0, [greatGrandchild]);
            const child = createSymbol('child', SymbolKind.Object, 2, 0, 8, 0, [grandchild]);
            const parent = createSymbol('parent', SymbolKind.Object, 1, 0, 9, 0, [child]);
            const grandparent = createSymbol('grandparent', SymbolKind.Object, 0, 0, 10, 0, [parent]);

            const index = new IndexedDocumentSymbols([grandparent]);

            // Test position in deepest level
            const symbols = index.findSymbolsAtPosition(pos(5, 0));
            assert.strictEqual(symbols.length, 5);
            assertContainsSameElements(
                getSymbolNames(symbols),
                ['greatGrandchild', 'grandchild', 'child', 'parent', 'grandparent']
            );
        });
    });

    describe('findSymbolsAtPosition', () => {
        it('should find symbols at a given position', () => {
            const rootSymbol = createSymbol('root', SymbolKind.Object, 0, 0, 10, 0, [
                createSymbol('property1', SymbolKind.Property, 1, 2, 2, 15),
                createSymbol('property2', SymbolKind.Property, 3, 2, 4, 20),
                createSymbol('nested', SymbolKind.Object, 5, 2, 8, 2, [
                    createSymbol('nestedProp', SymbolKind.Property, 6, 4, 7, 10)
                ])
            ]);

            const index = new IndexedDocumentSymbols([rootSymbol]);

            // Position inside property1
            const symbols1 = index.findSymbolsAtPosition({ line: 1, character: 5 });
            assert.strictEqual(symbols1.length, 2);
            const names1 = symbols1.map(s => s.name).sort();
            assert.deepStrictEqual(names1, ['property1', 'root']);

            // Position inside nested object
            const symbols2 = index.findSymbolsAtPosition({ line: 6, character: 6 });
            assert.strictEqual(symbols2.length, 3);
            const names2 = symbols2.map(s => s.name).sort();
            assert.deepStrictEqual(names2, ['nested', 'nestedProp', 'root']);

            // Position outside any symbol
            const symbols3 = index.findSymbolsAtPosition({ line: 20, character: 0 });
            assert.strictEqual(symbols3.length, 0);
        });

        it('should return all symbols containing the position', () => {
            const rootSymbol = createSymbol('root', SymbolKind.Object, 0, 0, 10, 0, [
                createSymbol('outer', SymbolKind.Object, 1, 0, 9, 0, [
                    createSymbol('inner', SymbolKind.Object, 2, 0, 8, 0, [
                        createSymbol('value', SymbolKind.String, 3, 0, 4, 0)
                    ])
                ])
            ]);

            const index = new IndexedDocumentSymbols([rootSymbol]);
            const symbols = index.findSymbolsAtPosition({ line: 3, character: 0 });

            assert.strictEqual(symbols.length, 4);
            const names = symbols.map(s => s.name).sort();
            assert.deepStrictEqual(names, ['inner', 'outer', 'root', 'value']);
        });

        describe('boundary positions', () => {
            it('should find symbol when position is at exact start', () => {
                // Visual representation:
                // Line 2, char 5-15: symbol
                const symbol = createSymbol('boundarySymbol', SymbolKind.Property, 2, 5, 2, 15);
                const index = new IndexedDocumentSymbols([symbol]);

                // Position at exact start
                const symbols = index.findSymbolsAtPosition(pos(2, 5));
                assert.strictEqual(symbols.length, 1);
                assert.strictEqual(symbols[0].name, 'boundarySymbol');
            });

            it('should find symbol when position is at exact end', () => {
                // Visual representation:
                // Line 2, char 5-15: symbol
                const symbol = createSymbol('boundarySymbol', SymbolKind.Property, 2, 5, 2, 15);
                const index = new IndexedDocumentSymbols([symbol]);

                // Position at exact end
                const symbols = index.findSymbolsAtPosition(pos(2, 15));
                assert.strictEqual(symbols.length, 1);
                assert.strictEqual(symbols[0].name, 'boundarySymbol');
            });

            it('should not find symbol when position is one character before start', () => {
                const symbol = createSymbol('boundarySymbol', SymbolKind.Property, 2, 5, 2, 15);
                const index = new IndexedDocumentSymbols([symbol]);

                // Position one character before
                const symbols = index.findSymbolsAtPosition(pos(2, 4));
                assert.strictEqual(symbols.length, 0);
            });

            it('should not find symbol when position is one character after end', () => {
                const symbol = createSymbol('boundarySymbol', SymbolKind.Property, 2, 5, 2, 15);
                const index = new IndexedDocumentSymbols([symbol]);

                // Position one character after
                const symbols = index.findSymbolsAtPosition(pos(2, 16));
                assert.strictEqual(symbols.length, 0);
            });

            it('should handle symbols spanning multiple lines', () => {
                // Visual representation:
                // Line 2-5: multiLineSymbol
                const symbol = createSymbol('multiLineSymbol', SymbolKind.Object, 2, 0, 5, 10);
                const index = new IndexedDocumentSymbols([symbol]);

                // Test various positions
                assert.strictEqual(index.findSymbolsAtPosition(pos(2, 0)).length, 1, 'Start of first line');
                assert.strictEqual(index.findSymbolsAtPosition(pos(3, 5)).length, 1, 'Middle line');
                assert.strictEqual(index.findSymbolsAtPosition(pos(5, 10)).length, 1, 'End of last line');
                assert.strictEqual(index.findSymbolsAtPosition(pos(5, 11)).length, 0, 'After end');
                assert.strictEqual(index.findSymbolsAtPosition(pos(1, 0)).length, 0, 'Before start');
            });

            it('should handle zero-width ranges', () => {
                // Symbol with same start and end position
                const symbol = createSymbol('zeroWidth', SymbolKind.Property, 2, 5, 2, 5);
                const index = new IndexedDocumentSymbols([symbol]);

                // Position at the exact point
                const symbols = index.findSymbolsAtPosition(pos(2, 5));
                assert.strictEqual(symbols.length, 1);
                assert.strictEqual(symbols[0].name, 'zeroWidth');

                // Position after should not match
                assert.strictEqual(index.findSymbolsAtPosition(pos(2, 6)).length, 0);
            });
        });

        describe('overlapping symbols', () => {
            it('should find all overlapping symbols at position', () => {
                // Visual representation:
                // Line 0-10: outer
                //   Line 2-8: middle
                //     Line 4-6: inner
                const inner = createSymbol('inner', SymbolKind.Property, 4, 0, 6, 0);
                const middle = createSymbol('middle', SymbolKind.Object, 2, 0, 8, 0, [inner]);
                const outer = createSymbol('outer', SymbolKind.Object, 0, 0, 10, 0, [middle]);

                const index = new IndexedDocumentSymbols([outer]);

                // Position in all three
                const symbols = index.findSymbolsAtPosition(pos(5, 0));
                assert.strictEqual(symbols.length, 3);
                assertContainsSameElements(getSymbolNames(symbols), ['inner', 'middle', 'outer']);
            });

            it('should handle symbols with identical ranges', () => {
                // Two symbols with exact same range
                const symbol1 = createSymbol('identical1', SymbolKind.Property, 2, 5, 4, 10);
                const symbol2 = createSymbol('identical2', SymbolKind.Property, 2, 5, 4, 10);

                const index = new IndexedDocumentSymbols([symbol1, symbol2]);

                const symbols = index.findSymbolsAtPosition(pos(3, 7));
                assert.strictEqual(symbols.length, 2);
                assertContainsSameElements(getSymbolNames(symbols), ['identical1', 'identical2']);
            });

            it('should handle adjacent but non-overlapping symbols', () => {
                // Visual representation:
                // Line 2, char 0-10: first
                // Line 2, char 10-20: second (starts where first ends)
                const first = createSymbol('first', SymbolKind.Property, 2, 0, 2, 10);
                const second = createSymbol('second', SymbolKind.Property, 2, 10, 2, 20);

                const index = new IndexedDocumentSymbols([first, second]);

                // Position at boundary should match both
                const atBoundary = index.findSymbolsAtPosition(pos(2, 10));
                assert.strictEqual(atBoundary.length, 2);
                assertContainsSameElements(getSymbolNames(atBoundary), ['first', 'second']);

                // Position before boundary should only match first
                const beforeBoundary = index.findSymbolsAtPosition(pos(2, 9));
                assert.strictEqual(beforeBoundary.length, 1);
                assert.strictEqual(beforeBoundary[0].name, 'first');

                // Position after boundary should only match second
                const afterBoundary = index.findSymbolsAtPosition(pos(2, 11));
                assert.strictEqual(afterBoundary.length, 1);
                assert.strictEqual(afterBoundary[0].name, 'second');
            });
        });

        describe('no match cases', () => {
            it('should return empty array when position is before any symbol', () => {
                const symbols = [
                    createSymbol('symbol1', SymbolKind.Object, 5, 0, 7, 0),
                    createSymbol('symbol2', SymbolKind.Object, 10, 0, 12, 0)
                ];

                const index = new IndexedDocumentSymbols(symbols);

                // Position before all symbols
                assert.strictEqual(index.findSymbolsAtPosition(pos(2, 0)).length, 0);
                assert.strictEqual(index.findSymbolsAtPosition(pos(0, 0)).length, 0);
            });

            it('should return empty array when position is after all symbols', () => {
                const symbols = [
                    createSymbol('symbol1', SymbolKind.Object, 1, 0, 3, 0),
                    createSymbol('symbol2', SymbolKind.Object, 5, 0, 7, 0)
                ];

                const index = new IndexedDocumentSymbols(symbols);

                // Position after all symbols
                assert.strictEqual(index.findSymbolsAtPosition(pos(10, 0)).length, 0);
                assert.strictEqual(index.findSymbolsAtPosition(pos(100, 100)).length, 0);
            });

            it('should return empty array when position is between non-overlapping symbols', () => {
                // Visual representation:
                // Line 1-3: symbol1
                // Line 5-7: symbol2 (gap at line 4)
                const symbols = [
                    createSymbol('symbol1', SymbolKind.Object, 1, 0, 3, 0),
                    createSymbol('symbol2', SymbolKind.Object, 5, 0, 7, 0)
                ];

                const index = new IndexedDocumentSymbols(symbols);

                // Position in the gap
                assert.strictEqual(index.findSymbolsAtPosition(pos(4, 0)).length, 0);
            });
        });
    });

    describe('getMostSpecificSymbolAtPosition', () => {
        it('should return the most specific symbol at a position', () => {
            const rootSymbol = createSymbol('root', SymbolKind.Object, 0, 0, 10, 0, [
                createSymbol('property', SymbolKind.Property, 1, 2, 2, 10)
            ]);

            const index = new IndexedDocumentSymbols([rootSymbol]);
            const symbol = index.getMostSpecificSymbolAtPosition({ line: 1, character: 5 });

            assert.notStrictEqual(symbol, null);
            assert.strictEqual(symbol!.name, 'property');
        });

        it('should return null for position outside any symbol', () => {
            const rootSymbol = createSymbol('root', SymbolKind.Object, 0, 0, 5, 0);
            const index = new IndexedDocumentSymbols([rootSymbol]);

            const symbol = index.getMostSpecificSymbolAtPosition({ line: 10, character: 0 });
            assert.strictEqual(symbol, null);
        });

        it('should handle multiple symbols with identical ranges', () => {
            // When multiple symbols have identical ranges, should return one of them
            const symbol1 = createSymbol('duplicate1', SymbolKind.Object, 2, 0, 4, 0);
            const symbol2 = createSymbol('duplicate2', SymbolKind.Object, 2, 0, 4, 0);

            const index = new IndexedDocumentSymbols([symbol1, symbol2]);
            const symbol = index.getMostSpecificSymbolAtPosition(pos(3, 0));

            assert.notStrictEqual(symbol, null);
            assert.ok(['duplicate1', 'duplicate2'].includes(symbol!.name));
        });

        it('should return innermost symbol when multiple symbols start at same position', () => {
            // Visual representation:
            // Line 2-10: outer {
            //   Line 2-8: middle {  (starts at same position as outer)
            //     Line 2-6: inner   (starts at same position as middle and outer)
            //   }
            // }
            const inner = createSymbol('inner', SymbolKind.Property, 2, 0, 6, 0);
            const middle = createSymbol('middle', SymbolKind.Object, 2, 0, 8, 0, [inner]);
            const outer = createSymbol('outer', SymbolKind.Object, 2, 0, 10, 0, [middle]);

            const index = new IndexedDocumentSymbols([outer]);
            const symbol = index.getMostSpecificSymbolAtPosition(pos(4, 0));

            assert.notStrictEqual(symbol, null);
            assert.strictEqual(symbol!.name, 'inner', 'Should return the innermost symbol');
        });

        it('should handle position at exact boundary of nested symbols', () => {
            // Visual representation:
            // Line 0-10: outer
            //   Line 2-5: inner
            const inner = createSymbol('inner', SymbolKind.Property, 2, 0, 5, 0);
            const outer = createSymbol('outer', SymbolKind.Object, 0, 0, 10, 0, [inner]);

            const index = new IndexedDocumentSymbols([outer]);

            // Position at inner's end boundary
            const atInnerEnd = index.getMostSpecificSymbolAtPosition(pos(5, 0));
            assert.strictEqual(atInnerEnd!.name, 'inner');

            // Position just after inner's end (still in outer)
            const afterInner = index.getMostSpecificSymbolAtPosition(pos(6, 0));
            assert.strictEqual(afterInner!.name, 'outer');
        });

        it('should handle empty index', () => {
            const index = new IndexedDocumentSymbols([]);
            const symbol = index.getMostSpecificSymbolAtPosition(pos(0, 0));
            assert.strictEqual(symbol, null);
        });
    });

    describe('array symbols', () => {
        it('should handle array symbols with element children', () => {
            // Visual representation:
            // Line 0-10: rootArray [
            //   Line 1-3: element0
            //   Line 4-6: element1
            //   Line 7-9: element2
            // ]
            const element0 = createSymbol('[0]', SymbolKind.Number, 1, 0, 3, 0);
            const element1 = createSymbol('[1]', SymbolKind.Number, 4, 0, 6, 0);
            const element2 = createSymbol('[2]', SymbolKind.Number, 7, 0, 9, 0);
            const rootArray = createSymbol('array', SymbolKind.Array, 0, 0, 10, 0, [element0, element1, element2]);

            const index = new IndexedDocumentSymbols([rootArray]);

            // Position in second element
            const symbols = index.findSymbolsAtPosition(pos(5, 0));
            assert.strictEqual(symbols.length, 2);
            const names = symbols.map(s => s.name).sort();
            assert.deepStrictEqual(names, ['[1]', 'array']);
        });

        it('should handle nested arrays', () => {
            // Visual representation:
            // Line 0-20: outerArray [
            //   Line 1-8: [0] (innerArray1) [
            //     Line 2-3: [0]
            //     Line 4-5: [1]
            //   ]
            //   Line 9-18: [1] (innerArray2) [
            //     Line 10-12: [0]
            //     Line 13-15: [1]
            //     Line 16-17: [2]
            //   ]
            // ]
            const inner1Element0 = createSymbol('[0]', SymbolKind.String, 2, 0, 3, 0);
            const inner1Element1 = createSymbol('[1]', SymbolKind.String, 4, 0, 5, 0);
            const innerArray1 = createSymbol('[0]', SymbolKind.Array, 1, 0, 8, 0, [inner1Element0, inner1Element1]);

            const inner2Element0 = createSymbol('[0]', SymbolKind.String, 10, 0, 12, 0);
            const inner2Element1 = createSymbol('[1]', SymbolKind.String, 13, 0, 15, 0);
            const inner2Element2 = createSymbol('[2]', SymbolKind.String, 16, 0, 17, 0);
            const innerArray2 = createSymbol('[1]', SymbolKind.Array, 9, 0, 18, 0, [inner2Element0, inner2Element1, inner2Element2]);

            const outerArray = createSymbol('outerArray', SymbolKind.Array, 0, 0, 20, 0, [innerArray1, innerArray2]);

            const index = new IndexedDocumentSymbols([outerArray]);

            // Position in innerArray1's second element
            const symbols1 = index.findSymbolsAtPosition(pos(4, 0));
            assert.strictEqual(symbols1.length, 3);
            assertContainsSameElements(getSymbolNames(symbols1), ['[1]', '[0]', 'outerArray']);

            // Position in innerArray2's third element
            const symbols2 = index.findSymbolsAtPosition(pos(16, 5));
            assert.strictEqual(symbols2.length, 3);
            const names2 = symbols2.map(s => s.name).sort();
            assert.deepStrictEqual(names2, ['[1]', '[2]', 'outerArray']);
        });

        it('should handle mixed object and array structures', () => {
            // Visual representation:
            // Line 0-15: rootObject {
            //   Line 1-5: items: array [
            //     Line 2-3: [0]
            //     Line 4: [1]
            //   ]
            //   Line 6-14: nested: {
            //     Line 7-13: data: array [
            //       Line 8-10: [0]: object
            //       Line 11-12: [1]: object
            //     ]
            //   }
            // }
            const arrayItem0 = createSymbol('[0]', SymbolKind.String, 2, 0, 3, 0);
            const arrayItem1 = createSymbol('[1]', SymbolKind.String, 4, 0, 4, 20);
            const itemsArray = createSymbol('items', SymbolKind.Array, 1, 0, 5, 0, [arrayItem0, arrayItem1]);

            const dataItem0 = createSymbol('[0]', SymbolKind.Object, 8, 0, 10, 0);
            const dataItem1 = createSymbol('[1]', SymbolKind.Object, 11, 0, 12, 0);
            const dataArray = createSymbol('data', SymbolKind.Array, 7, 0, 13, 0, [dataItem0, dataItem1]);

            const nestedObject = createSymbol('nested', SymbolKind.Object, 6, 0, 14, 0, [dataArray]);
            const rootObject = createSymbol('rootObject', SymbolKind.Object, 0, 0, 15, 0, [itemsArray, nestedObject]);

            const index = new IndexedDocumentSymbols([rootObject]);

            // Position in nested array's first object
            const symbols = index.findSymbolsAtPosition(pos(9, 0));
            assert.strictEqual(symbols.length, 4);
            const names = symbols.map(s => s.name).sort();
            assert.deepStrictEqual(names, ['[0]', 'data', 'nested', 'rootObject']);
        });

        it('should handle empty arrays', () => {
            const emptyArray = createSymbol('emptyArray', SymbolKind.Array, 0, 0, 1, 0, []);
            const index = new IndexedDocumentSymbols([emptyArray]);

            // Position inside empty array
            const symbols = index.findSymbolsAtPosition(pos(0, 5));
            assert.strictEqual(symbols.length, 1);
            assert.strictEqual(symbols[0].name, 'emptyArray');
        });
    });
});