import { assert } from './assert';
import { initializeLanguageConfig, getLanguageConfiguration, isKsonDialect, resetLanguageConfiguration } from '../../src/config/languageConfig';

describe('Language Configuration Tests', () => {

    beforeEach(() => resetLanguageConfiguration());
    afterEach(() => resetLanguageConfiguration());

    function initWithLanguages(languages: any[]) {
        initializeLanguageConfig({ contributes: { languages } });
    }

    describe('getLanguageConfiguration', () => {
        it('Should extract kson language and extension', () => {
            initWithLanguages([{ id: 'kson', extensions: ['.kson'] }]);
            const config = getLanguageConfiguration();

            assert.deepStrictEqual(config.languageIds, ['kson']);
            assert.deepStrictEqual(config.fileExtensions, ['kson']);
        });

        it('Should extract multiple dialects', () => {
            initWithLanguages([
                { id: 'kson', extensions: ['.kson'] },
                { id: 'KuStON', extensions: ['.KuStON'] }
            ]);
            const config = getLanguageConfiguration();

            assert.ok(config.languageIds.includes('kson'));
            assert.ok(config.languageIds.includes('KuStON'));
            assert.ok(config.fileExtensions.includes('kson'));
            assert.ok(config.fileExtensions.includes('KuStON'));
        });

        it('Should handle multiple extensions for one language', () => {
            initWithLanguages([{ id: 'kson', extensions: ['.kson', '.json5'] }]);
            const config = getLanguageConfiguration();

            assert.deepStrictEqual(config.languageIds, ['kson']);
            assert.ok(config.fileExtensions.includes('kson'));
            assert.ok(config.fileExtensions.includes('json5'));
        });

        it('Should strip leading dots from extensions', () => {
            initWithLanguages([{ id: 'kson', extensions: ['.kson', 'other'] }]);
            const config = getLanguageConfiguration();

            assert.ok(config.fileExtensions.includes('kson'));
            assert.ok(config.fileExtensions.includes('other'));
        });
    });

    describe('isKsonDialect', () => {
        it('Should return true for kson language', () => {
            initWithLanguages([{ id: 'kson', extensions: ['.kson'] }]);
            assert.strictEqual(isKsonDialect('kson'), true);
        });

        it('Should return true for registered dialect', () => {
            initWithLanguages([
                { id: 'kson', extensions: ['.kson'] },
                { id: 'KuStON', extensions: ['.KuStON'] }
            ]);
            assert.strictEqual(isKsonDialect('KuStON'), true);
        });

        it('Should return false for unregistered language', () => {
            initWithLanguages([{ id: 'kson', extensions: ['.kson'] }]);
            assert.strictEqual(isKsonDialect('python'), false);
        });
    });
});
