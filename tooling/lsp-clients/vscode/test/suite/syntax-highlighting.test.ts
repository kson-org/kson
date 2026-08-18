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

    /**
     * Every embed-block rule carries a `meta.embedded.block.<language>.kson` name, so the
     * scope on a block's body tells us which rule actually matched the tag. Unknown tags
     * fall through to the catch-all, which is named `...block.generic.kson`.
     */
    async function matchedEmbedLanguage(document: vscode.TextDocument, line: number): Promise<string> {
        const scopes = await getTokenScopesAtPosition(document, line, 5);
        const scope = scopes.find(s => /^meta\.embedded\.block\..+\.kson$/.test(s));
        assert.ok(scope, `No meta.embedded.block scope at line ${line}; got ${JSON.stringify(scopes)}`);
        return scope!.slice('meta.embedded.block.'.length, -'.kson'.length);
    }

    describe('Embed tag language matching', () => {
        // Language ids and aliases are pasted into a regex by
        // shared/scripts/generate-tm-embed-block.ts. Unescaped, the `c++` alias reads as
        // "one or more c" and swallows every tag starting with `c`. Unanchored, a tag only
        // has to *start with* a known name, so `%jsonnet` matches JavaScript. A KSON tag
        // runs from the delimiter to the newline (docs/readme.md:354).
        const cases: Array<{ tag: string, expected: string, why: string }> = [
            // Aliases containing regex metacharacters must be matched literally.
            {tag: '%c++', expected: 'cpp', why: 'the `c++` alias is escaped'},
            {tag: '%c#', expected: 'csharp', why: 'the `c#` alias is escaped'},
            {tag: '%cobol', expected: 'generic', why: '`c++` must not collapse to a bare `c`'},
            {tag: '%csharp', expected: 'csharp', why: 'the csharp rule must be reachable'},

            // The language name must be anchored, not merely a prefix of the tag.
            {tag: '%json', expected: 'json', why: 'the json rule must not be shadowed by `js`'},
            {tag: '%jsonnet', expected: 'generic', why: 'a longer tag must not match a shorter name'},
            {tag: '%pythonic', expected: 'generic', why: 'a longer tag must not match a shorter name'},
            {tag: '%rst', expected: 'generic', why: 'a longer tag must not match the `rs` alias'},

            // Tags that already resolved correctly, kept here so the anchor cannot over-tighten.
            {tag: '%python', expected: 'python', why: 'a plain tag still matches'},
            {tag: '%py', expected: 'python', why: 'a plain alias still matches'},
            {tag: '$python', expected: 'python', why: 'the `$` delimiter still matches'},
            {tag: '%kotlin', expected: 'generic', why: 'an unknown language falls through'},

            // Whitespace or end of line bounds the name; a colon is ordinary tag text.
            {tag: '%python v3', expected: 'python', why: 'metadata may follow the name'},
            {
                tag: '%sql "server=10.0.1.174;uid=root;database=company"',
                expected: 'sql',
                why: 'the tag from docs/readme.md:374 resolves'
            },
            {tag: '%python:', expected: 'generic', why: 'the tag is `python:`, not `python`'},
            {tag: '%python: v3', expected: 'generic', why: 'a colon does not end the name'},

            // The lexer skips inline whitespace after the delimiter (Lexer.kt).
            {tag: '% python', expected: 'python', why: 'a space may precede the tag'},
            {tag: '%\tpython', expected: 'python', why: 'a tab may precede the tag'},
            {tag: '$ python', expected: 'python', why: 'the `$` delimiter allows it too'},
        ];

        for (const {tag, expected, why} of cases) {
            it(`Should highlight \`${tag}\` as ${expected} because ${why}`, async () => {
                const [uri, document] = await createTestFile(`key: ${tag}\n    body\n    %%`);
                testFileUri = uri;

                assert.strictEqual(await matchedEmbedLanguage(document, 1), expected,
                    `\`${tag}\` matched the wrong embed-block rule`);
            }).timeout(10000);
        }

        // A mismatched grammar can leave a multi-line construct open. The embed block's own
        // `end` pattern is only tested when its rule is back on top of the TextMate rule
        // stack, so an unterminated injected grammar keeps the block open to end of file and
        // takes every following block with it.
        it('Should not let an unknown language swallow the rest of the file', async () => {
            const content = `weekly: %jsonnet
    { total: std.foldl(function(a, b) a + b, [4, 4.5], 0) }
    %%
after: %python
    print("still python")
    %%`;

            const [uri, document] = await createTestFile(content);
            testFileUri = uri;

            assert.strictEqual(await matchedEmbedLanguage(document, 1), 'generic',
                '`%jsonnet` should fall through to the catch-all');
            assert.strictEqual(await matchedEmbedLanguage(document, 4), 'python',
                'the block after `%jsonnet` should be unaffected');
        }).timeout(10000);
    });
});