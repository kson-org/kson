// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest';

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

    it('is idempotent on subsequent calls', async () => {
        const monacoStub = await import('monaco-editor');
        const registerSpy = vi.spyOn(monacoStub.languages, 'register');
        const { registerKsonLanguage } = await import('./ksonLanguage.js');

        registerKsonLanguage();
        registerKsonLanguage();

        expect(registerSpy).toHaveBeenCalledTimes(1);
    });
});
