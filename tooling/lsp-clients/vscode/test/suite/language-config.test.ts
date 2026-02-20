import { assert } from './assert';
import { initializeLanguageConfig, getLanguageConfiguration, isKsonLanguage, resetLanguageConfiguration } from '../../src/config/languageConfig';

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

        it('Should extract multiple languages', () => {
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

    describe('isKsonLanguage', () => {
        it('Should return true for kson language', () => {
            initWithLanguages([{ id: 'kson', extensions: ['.kson'] }]);
            assert.strictEqual(isKsonLanguage('kson'), true);
        });

        it('Should return true for registered language', () => {
            initWithLanguages([
                { id: 'kson', extensions: ['.kson'] },
                { id: 'KuStON', extensions: ['.KuStON'] }
            ]);
            assert.strictEqual(isKsonLanguage('KuStON'), true);
        });

        it('Should return false for unregistered language', () => {
            initWithLanguages([{ id: 'kson', extensions: ['.kson'] }]);
            assert.strictEqual(isKsonLanguage('python'), false);
        });
    });

    describe('bundledSchemas', () => {
        it('Should extract bundled schema mappings using file extension', () => {
            initWithLanguages([
                { id: 'kson', extensions: ['.kson'], bundledSchema: null },
                { id: 'kxt', extensions: ['.kxt'], bundledSchema: './dist/extension/schemas/kxt.schema.kson' }
            ]);
            const config = getLanguageConfiguration();

            assert.ok(config.bundledSchemas, 'bundledSchemas should be defined');
            assert.strictEqual(config.bundledSchemas.length, 1, 'Should have 1 bundled schema');
            assert.strictEqual(config.bundledSchemas[0].fileExtension, 'kxt');
            assert.strictEqual(config.bundledSchemas[0].schemaPath, './dist/extension/schemas/kxt.schema.kson');
        });

        it('Should handle no bundled schemas', () => {
            initWithLanguages([
                { id: 'kson', extensions: ['.kson'], bundledSchema: null }
            ]);
            const config = getLanguageConfiguration();

            assert.ok(config.bundledSchemas, 'bundledSchemas should be defined');
            assert.strictEqual(config.bundledSchemas.length, 0, 'Should have 0 bundled schemas');
        });

        it('Should handle missing bundledSchema field', () => {
            initWithLanguages([
                { id: 'kson', extensions: ['.kson'] }
            ]);
            const config = getLanguageConfiguration();

            assert.ok(config.bundledSchemas, 'bundledSchemas should be defined');
            assert.strictEqual(config.bundledSchemas.length, 0, 'Should have 0 bundled schemas');
        });

        it('Should handle multiple bundled schemas', () => {
            initWithLanguages([
                { id: 'kson', extensions: ['.kson'], bundledSchema: null },
                { id: 'kxt', extensions: ['.kxt'], bundledSchema: './schemas/kxt.schema.kson' },
                { id: 'config', extensions: ['.config'], bundledSchema: './schemas/config.schema.kson' }
            ]);
            const config = getLanguageConfiguration();

            assert.strictEqual(config.bundledSchemas.length, 2, 'Should have 2 bundled schemas');
            assert.ok(config.bundledSchemas.some(s => s.fileExtension === 'kxt'));
            assert.ok(config.bundledSchemas.some(s => s.fileExtension === 'config'));
        });
    });
});
