// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { languageConfiguration } from '@kson/lsp-shared/config';

beforeEach(() => {
    // The "already-registered" guard is a module-level flag; reset modules
    // so each test starts with a fresh registration state.
    vi.resetModules();
});

describe('registerKsonLanguage', () => {
    it('registers the language with monaco on first call', async () => {
        const monacoStub = await import('monaco-editor');
        const registerSpy = vi.spyOn(monacoStub.languages, 'register');
        const configSpy = vi.spyOn(monacoStub.languages, 'setLanguageConfiguration');
        const tokensSpy = vi.spyOn(monacoStub.languages, 'setMonarchTokensProvider');
        const { registerKsonLanguage, KSON_LANGUAGE_ID } = await import('./ksonLanguage.js');

        registerKsonLanguage();

        expect(registerSpy).toHaveBeenCalledWith(
            expect.objectContaining({ id: KSON_LANGUAGE_ID, extensions: ['.kson'] }),
        );
        expect(configSpy).toHaveBeenCalledWith(KSON_LANGUAGE_ID, expect.any(Object));
        expect(tokensSpy).toHaveBeenCalledWith(KSON_LANGUAGE_ID, expect.any(Object));
    });

    describe('Enter rules', () => {
        const outdentsAfter = ['  .', '  =', '  list: end .', '  list: end ..', '  - list =', '  %%.'];
        const keepsIndentAfter = ['  x: 1.5', '  # done.', '  list: end # done.'];

        /** Registers the language and returns the Enter rules it hands to Monaco. */
        async function registeredEnterRules() {
            const monacoStub = await import('monaco-editor');
            const configSpy = vi.spyOn(monacoStub.languages, 'setLanguageConfiguration');
            const { registerKsonLanguage } = await import('./ksonLanguage.js');

            registerKsonLanguage();

            const [, { onEnterRules = [] }] = configSpy.mock.calls[0];
            return onEnterRules;
        }

        /** Whether Enter after `line` outdents, going by the first rule that matches it, as Monaco does. */
        async function outdentsOnEnterAfter(line: string) {
            const onEnterRules = await registeredEnterRules();
            const { languages } = await import('monaco-editor');
            const rule = onEnterRules.find(({ beforeText }) => beforeText.test(line));
            return rule?.action.indentAction === languages.IndentAction.Outdent;
        }

        it.each(outdentsAfter)('outdents after %j', async (line) => {
            expect(await outdentsOnEnterAfter(line)).toBe(true);
        });

        it.each(keepsIndentAfter)('keeps the indentation after %j', async (line) => {
            expect(await outdentsOnEnterAfter(line)).toBe(false);
        });

        it('matches the pattern of the VS Code outdent rule', async () => {
            const onEnterRules = await registeredEnterRules();
            const vscodeOutdentRule = languageConfiguration.onEnterRules.find(
                (rule) => rule.action.indent === 'outdent',
            );

            expect(onEnterRules.map(({ beforeText }) => beforeText.source))
                .toEqual([vscodeOutdentRule?.beforeText]);
        });
    });

    it('is idempotent on subsequent calls', async () => {
        const monacoStub = await import('monaco-editor');
        const registerSpy = vi.spyOn(monacoStub.languages, 'register');
        const { registerKsonLanguage } = await import('./ksonLanguage.js');

        registerKsonLanguage();
        registerKsonLanguage();

        expect(registerSpy).toHaveBeenCalledTimes(1);
    });
});
