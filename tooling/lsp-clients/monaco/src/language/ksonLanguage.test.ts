// @vitest-environment happy-dom
import { beforeEach, describe, expect, it, vi } from 'vitest';

/** Builds a fresh fake monaco API exposing only the language-registration methods. */
function fakeMonacoApi() {
    return {
        languages: {
            register: vi.fn(),
            setLanguageConfiguration: vi.fn(),
            setMonarchTokensProvider: vi.fn(),
        },
    };
}

beforeEach(() => {
    // The imported-monaco fallback is gated by a module-level flag; reset
    // modules so each test gets a fresh registration state.
    vi.resetModules();
});

describe('registerKsonLanguage', () => {
    it('registers against the passed monacoApi when one is provided', async () => {
        const { registerKsonLanguage, KSON_LANGUAGE_ID } = await import('./ksonLanguage.js');
        const api = fakeMonacoApi();

        registerKsonLanguage(api as never);

        expect(api.languages.register).toHaveBeenCalledWith(
            expect.objectContaining({ id: KSON_LANGUAGE_ID, extensions: ['.kson'] }),
        );
        expect(api.languages.setLanguageConfiguration).toHaveBeenCalledWith(
            KSON_LANGUAGE_ID,
            expect.any(Object),
        );
        expect(api.languages.setMonarchTokensProvider).toHaveBeenCalledWith(
            KSON_LANGUAGE_ID,
            expect.any(Object),
        );
    });

    it('registers unconditionally on every call when monacoApi is passed', async () => {
        const { registerKsonLanguage } = await import('./ksonLanguage.js');
        const api = fakeMonacoApi();

        registerKsonLanguage(api as never);
        registerKsonLanguage(api as never);

        // We can't tell whether *that* monaco has already been registered,
        // and the underlying API is idempotent, so always re-run.
        expect(api.languages.register).toHaveBeenCalledTimes(2);
    });

    it('does not touch the imported monaco when an explicit api is passed', async () => {
        const monacoStub = await import('monaco-editor');
        const registerSpy = vi.spyOn(monacoStub.languages, 'register');
        const { registerKsonLanguage } = await import('./ksonLanguage.js');
        const api = fakeMonacoApi();

        registerKsonLanguage(api as never);

        expect(registerSpy).not.toHaveBeenCalled();
        expect(api.languages.register).toHaveBeenCalledTimes(1);
    });

    it('falls back to the imported monaco when no api is passed and skips redundant calls', async () => {
        const monacoStub = await import('monaco-editor');
        const registerSpy = vi.spyOn(monacoStub.languages, 'register');
        const { registerKsonLanguage } = await import('./ksonLanguage.js');

        registerKsonLanguage();
        registerKsonLanguage();

        expect(registerSpy).toHaveBeenCalledTimes(1);
    });
});
