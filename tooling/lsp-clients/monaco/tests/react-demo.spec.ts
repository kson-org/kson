import { test, expect } from '@playwright/test';

/**
 * Smoke test for the React demo.
 *
 * Verifies that `@monaco-editor/react` cohabitation works:
 *   1. All three <Editor /> instances mount (json + 2x kson sharing the registry).
 *   2. No second Monaco copy is fetched from a CDN (does NOT cover a locally-bundled duplicate).
 *   3. A single Monaco runtime owns all editors.
 *   4. The KSON editor reports a schema-specific diagnostic via the attached LSP.
 *
 * Prerequisites:
 *   - Dev server running on http://localhost:5176
 *     (npm run dev -w @kson/monaco-react-demo, or ./gradlew tooling:lsp-clients:npm_run_demoReact)
 */
test.describe('React demo', () => {
    test.use({ baseURL: 'http://localhost:5176' });

    test('renders json + two kson editors with shared monaco', async ({ page }) => {
        await page.goto('/');
        await expect(page).toHaveTitle(/React Demo/);

        // All three editor panes render.
        await expect(page.locator('.monaco-editor')).toHaveCount(3);
        for (let i = 0; i < 3; i++) {
            await expect(page.locator('.monaco-editor').nth(i)).toBeVisible({ timeout: 15000 });
        }

        // Sample content is reachable on all three panes.
        await expect(page.locator('.editor-pane').nth(0)).toContainText('kson-react-demo');
        await expect(page.locator('.editor-pane').nth(1)).toContainText('react');
        await expect(page.locator('.editor-pane').nth(2)).toContainText('second-document');

        // No CDN fetch of monaco — does not cover a locally-bundled duplicate.
        const cdnMonacoRequests = await page.evaluate(() => {
            return performance
                .getEntriesByType('resource')
                .map((e) => (e as PerformanceResourceTiming).name)
                .filter((n) => /monaco-editor/.test(n) && /cdn|jsdelivr|unpkg/.test(n));
        });
        expect(cdnMonacoRequests).toEqual([]);

        // Single monaco runtime owns all three editors.
        const editorCount = await page.evaluate(() => {
            const m = (window as { monaco?: typeof import('monaco-editor') }).monaco;
            return m ? m.editor.getEditors().length : -1;
        });
        expect(editorCount).toBe(3);
    });

    test('kson editor reports schema-specific diagnostic', async ({ page }) => {
        await page.goto('/');

        // The sample KSON has `unknown_key: "..."` against an `additionalProperties: false`
        // schema; the LSP emits "Additional property 'unknown_key' is not allowed".
        // Note: this relies on kson #1 mounting first so its lspOptions.bundledSchemas seed the registry.
        await expect.poll(
            async () => {
                return await page.evaluate(() => {
                    const monaco = (window as { monaco?: typeof import('monaco-editor') }).monaco;
                    if (!monaco) return null;
                    const ksonModel = monaco.editor
                        .getModels()
                        .find((m) => m.getLanguageId() === 'kson');
                    if (!ksonModel) return null;
                    return monaco.editor
                        .getModelMarkers({ resource: ksonModel.uri })
                        .map((mk) => mk.message);
                });
            },
            { timeout: 15000, intervals: [500] },
        ).toEqual(expect.arrayContaining([expect.stringContaining("Additional property 'unknown_key'")]));
    });
});
