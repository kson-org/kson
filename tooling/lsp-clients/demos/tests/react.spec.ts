import { test, expect } from '@playwright/test';

/**
 * Verifies the @monaco-editor/react consumer integration:
 *   1. All three <Editor /> instances mount (json + 2x kson sharing the registry).
 *   2. A single Monaco runtime owns every editor on the page (loader.config worked).
 *   3. The two kson editors register with language id 'kson' (useKsonLsp ran).
 *   4. The schema attached on kson #1 produces a diagnostic for the unknown_key.
 */
test.describe('React demo', () => {
    test.use({ baseURL: 'http://localhost:5176' });

    test('renders json + two kson editors with a shared monaco runtime', async ({ page }) => {
        await page.goto('/');

        // Three editor panes — json, kson #1, kson #2.
        await expect(page.locator('.monaco-editor')).toHaveCount(3, { timeout: 15000 });
        for (let i = 0; i < 3; i++) {
            await expect(page.locator('.monaco-editor').nth(i)).toBeVisible({ timeout: 15000 });
        }

        // Sample content surfaces in each pane.
        await expect(page.locator('.editor-pane').nth(0)).toContainText('kson-react-demo');
        await expect(page.locator('.editor-pane').nth(1)).toContainText('react');
        await expect(page.locator('.editor-pane').nth(2)).toContainText('second-document');

        // Single monaco runtime owns all three editors. If @monaco-editor/react
        // had race-fetched a CDN copy this would either be -1 (window.monaco
        // never set) or wrong because the second runtime owns its own editors.
        const editorCount = await page.evaluate(() => {
            const m = (window as { monaco?: typeof import('monaco-editor') }).monaco;
            return m ? m.editor.getEditors().length : -1;
        });
        expect(editorCount).toBe(3);
    });

    test('useKsonLsp registers the kson language on both kson editors', async ({ page }) => {
        await page.goto('/');
        await expect(page.locator('.monaco-editor')).toHaveCount(3, { timeout: 15000 });

        const ksonModelCount = await page.evaluate(() => {
            const m = (window as { monaco?: typeof import('monaco-editor') }).monaco;
            if (!m) return -1;
            return m.editor.getModels().filter((mdl) => mdl.getLanguageId() === 'kson').length;
        });
        expect(ksonModelCount).toBe(2);
    });

    test('kson #1 schema produces a diagnostic for the unknown_key sample', async ({ page }) => {
        await page.goto('/');

        // The sample KSON has `unknown_key: "..."` against an `additionalProperties: false`
        // schema; the LSP emits "Additional property 'unknown_key' is not allowed".
        await expect.poll(
            async () => {
                return await page.evaluate(() => {
                    const monaco = (window as { monaco?: typeof import('monaco-editor') }).monaco;
                    if (!monaco) return null;
                    const ksonModel = monaco.editor
                        .getModels()
                        .find((mdl) => mdl.getLanguageId() === 'kson' && mdl.uri.toString().endsWith('config-1.kson'));
                    if (!ksonModel) return null;
                    return monaco.editor
                        .getModelMarkers({ resource: ksonModel.uri })
                        .map((mk) => mk.message);
                });
            },
            { timeout: 20000, intervals: [500] },
        ).toEqual(expect.arrayContaining([expect.stringContaining("Additional property 'unknown_key'")]));
    });
});
