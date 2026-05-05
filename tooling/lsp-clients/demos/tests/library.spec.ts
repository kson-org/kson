import { test, expect } from '@playwright/test';

/**
 * Verifies the @kson/monaco-editor ES module consumer integration:
 * the package's export map resolves, types from dist/ load, two editors
 * mount through one shared LSP worker (refcounted registry), and the
 * bundled JSON Schema produces diagnostics on editor 1.
 */
test.describe('Library demo', () => {
    test('two editors mount via the shared registry; get/setValue work', async ({ page }) => {
        await page.goto('http://localhost:5174');

        // Both editors render — the second acquire reuses the running LSP worker.
        await expect(page.locator('.monaco-editor')).toHaveCount(2, { timeout: 15000 });
        for (let i = 0; i < 2; i++) {
            await expect(page.locator('.monaco-editor').nth(i)).toBeVisible({ timeout: 15000 });
        }

        const log = page.locator('#log');
        await expect(log).toContainText('editor 1 ready', { timeout: 15000 });
        await expect(log).toContainText('editor 2 active', { timeout: 15000 });

        await page.locator('#btn-get').click();
        await expect(log).toContainText('name: "my-project"');
        await expect(log).toContainText('version: "1.0.0"');

        await page.locator('#btn-set').click();
        await expect(page.locator('#editor-1 .view-lines')).toContainText('updated');
        await expect(page.locator('#editor-1 .view-lines')).toContainText('2.0.0');

        await page.locator('#btn-get').click();
        await expect(log).toContainText('name: "updated"');
    });

    test('bundled schema produces diagnostic for unknown key', async ({ page }) => {
        await page.goto('http://localhost:5174');

        await expect(page.locator('.monaco-editor').first()).toBeVisible({ timeout: 15000 });
        await expect(page.locator('#log')).toContainText('editor 1 ready', { timeout: 15000 });

        // Insert an additionalProperties violation; the schema disallows any
        // key beyond name/version/tags, so the LSP must surface a diagnostic.
        await page.locator('#editor-1 .view-lines').click();
        await page.keyboard.press('Control+Home');
        await page.keyboard.press('ArrowDown');
        await page.keyboard.press('Home');
        await page.keyboard.type('unknownKey: "oops"\n');

        // Squiggly overlays are the user-visible LSP-marker signal; the
        // additionalProperties violation surfaces as a warning-level squiggle.
        await expect(page.locator('#editor-1 [class*="squiggly"]').first())
            .toBeVisible({ timeout: 15000 });
    });

    test('disposing editor 1 keeps editor 2 alive', async ({ page }) => {
        await page.goto('http://localhost:5174');

        const log = page.locator('#log');
        await expect(log).toContainText('editor 2 active', { timeout: 15000 });

        await page.locator('#btn-dispose').click();
        await expect(log).toContainText('editor 1 disposed');
        // Editor 2 is still mounted — registry refcount > 0 keeps the LSP worker.
        await expect(page.locator('#editor-2 .monaco-editor')).toBeVisible();
    });
});
