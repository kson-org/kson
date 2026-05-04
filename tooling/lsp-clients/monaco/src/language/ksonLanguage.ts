/**
 * Registers the KSON language with Monaco: language ID, Monarch tokenizer,
 * and bracket/comment configuration.
 *
 * The Monarch tokenizer provides instant basic colorization.  Richer coloring
 * comes from the LSP's semantic tokens provider (registered by KsonLspBridge).
 */

import * as monaco from 'monaco-editor';

export const KSON_LANGUAGE_ID = 'kson';

let registeredWithDefault = false;

/**
 * Register the KSON language with Monaco.  Safe to call multiple times.
 *
 * When `monacoApi` is provided, registration runs against that instance
 * unconditionally (we can't know whether it has already been registered,
 * and the underlying Monaco APIs are idempotent).  When omitted, we
 * register against the imported `monaco-editor` module and skip on
 * subsequent calls.
 */
export function registerKsonLanguage(monacoApi?: typeof monaco): void {
    const api = monacoApi ?? monaco;

    if (!monacoApi) {
        if (registeredWithDefault) return;
        registeredWithDefault = true;
    }

    api.languages.register({
        id: KSON_LANGUAGE_ID,
        extensions: ['.kson'],
        aliases: ['KSON', 'Kson', 'kson'],
    });

    api.languages.setLanguageConfiguration(KSON_LANGUAGE_ID, {
        comments: { lineComment: '#' },
        brackets: [
            ['{', '}'],
            ['[', ']'],
        ],
        autoClosingPairs: [
            { open: '{', close: '}' },
            { open: '[', close: ']' },
            { open: '"', close: '"', notIn: ['string'] },
            { open: '`', close: '`', notIn: ['string'] },
        ],
        surroundingPairs: [
            { open: '{', close: '}' },
            { open: '[', close: ']' },
            { open: '"', close: '"' },
        ],
    });

    api.languages.setMonarchTokensProvider(KSON_LANGUAGE_ID, {
        keywords: ['true', 'false', 'null'],

        tokenizer: {
            root: [
                // Comments
                [/#.*$/, 'comment'],

                // Strings (double-quoted and single-quoted)
                [/"/, 'string', '@string_double'],
                [/'/, 'string', '@string_single'],

                // Numbers (integer and float, optional sign)
                [/[+-]?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?/, 'number'],

                // Keywords (true, false, null) — must precede the unquoted identifier rule
                [/\b(?:true|false|null)\b/, 'keyword'],

                // Unquoted identifiers (keys and values)
                [/[a-zA-Z_]\w*/, 'identifier'],

                // Delimiters and operators
                [/[{}[\]<>]/, '@brackets'],
                [/:/, 'delimiter'],
                [/[.,=]/, 'delimiter'],

                // List dash (dash followed by whitespace)
                [/-(?=\s)/, 'delimiter'],

                // Embed open/close delimiters
                [/\$\$|\$/, 'metatag'],
                [/%%|%/, 'metatag'],
            ],

            string_double: [
                [/[^\\"]+/, 'string'],
                [/\\./, 'string.escape'],
                [/"/, 'string', '@pop'],
            ],

            string_single: [
                [/[^\\']+/, 'string'],
                [/\\./, 'string.escape'],
                [/'/, 'string', '@pop'],
            ],
        },
    });
}
