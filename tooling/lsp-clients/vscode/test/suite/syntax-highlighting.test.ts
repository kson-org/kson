import * as vscode from 'vscode';
import { assert } from './assert';
import { createTestFile, cleanUp } from './common';
import TextmateLanguageService from 'vscode-textmate-languageservice';

describe('Syntax Highlighting Tests', () => {
    let testFileUri: vscode.Uri | undefined;

    afterEach(async () => {
        if (testFileUri) {
            await cleanUp(testFileUri);
            testFileUri = undefined;
        }
    });

    async function getTokenScopesAtPosition(document: vscode.TextDocument, line: number, character: number): Promise<string[]> {
        const position = new vscode.Position(line, character);
        const tokenInfo = await TextmateLanguageService.api.getScopeInformationAtPosition(document, position);
        console.log(`Token at line ${line}, char ${character}:`, tokenInfo);
        return tokenInfo.scopes;
    }

    it('Should highlight Python embedded blocks', async () => {
        const content = `key: %python
            print("Hello, World!")
            def greet(name):
                return f"Hello, {name}!"
            %%`;
        
        const [uri, document] = await createTestFile(content);
        testFileUri = uri;

        // Check that Python code is tagged as python
        const pythonScopes = await getTokenScopesAtPosition(document, 1, 10);
        console.log("Python code scopes:", pythonScopes);
        assert.ok(pythonScopes.some(scope => scope.includes('source.python') || scope.includes('meta.embedded.python')));
    }).timeout(10000);
});