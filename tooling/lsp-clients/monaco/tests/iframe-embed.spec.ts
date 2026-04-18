import { test, expect } from '@playwright/test';

/**
 * Integration test for the iframe embedding flow.
 *
 * Verifies the Stripe Elements-style architecture:
 *   1. Parent loads kson-editor.js, calls KsonEditor.create()
 *   2. Iframe posts kson:ready, parent responds with kson:init
 *   3. Editor renders with the initial value
 *   4. getValue() returns current content synchronously
 *   5. setValue() updates the editor content
 *   6. onChange fires when the user edits
 *   7. dispose() removes the iframe
 *
 * Prerequisites:
 *   - dist-iframe/ must be built (npm run build:iframe)
 *   - Dev server running on http://localhost:5173 (npm run dev)
 */
test.describe('Iframe Embed', () => {
    test('ready → init → getValue → setValue → dispose', async ({ page }) => {
        await page.goto('http://localhost:5173/tests/iframe-embed.html');

        // Wait for the KsonEditor.create() promise to resolve (logs "ready").
        const output = page.locator('#output');
        await expect(output).toContainText('ready', { timeout: 15000 });

        // getValue() should return the initial value.
        await expect(output).toContainText('value:{ name: "test-value" }');

        // The iframe should be present and contain a Monaco editor.
        const iframe = page.locator('iframe');
        await expect(iframe).toBeVisible();

        const editorFrame = page.frameLocator('iframe');
        await expect(editorFrame.locator('.monaco-editor')).toBeVisible({ timeout: 10000 });

        // setValue() should update the editor content.
        await page.evaluate(() => {
            (window as any).__ksonEditor.setValue('{ name: "updated" }');
        });

        // Verify the new value is reflected in getValue() on the parent side.
        const newValue = await page.evaluate(() => {
            return (window as any).__ksonEditor.getValue();
        });
        expect(newValue).toBe('{ name: "updated" }');

        // Verify the iframe editor actually received the new content.
        await expect(editorFrame.locator('.view-lines')).toContainText('updated');

        // dispose() should remove the iframe.
        await page.evaluate(() => {
            (window as any).__ksonEditor.dispose();
        });
        await expect(iframe).toHaveCount(0);
    });

    test('onChange fires on editor input', async ({ page }) => {
        await page.goto('http://localhost:5173/tests/iframe-embed.html');

        const output = page.locator('#output');
        await expect(output).toContainText('ready', { timeout: 15000 });

        // Wait for the Monaco editor inside the iframe to be fully loaded.
        const editorFrame = page.frameLocator('iframe');
        await expect(editorFrame.locator('.monaco-editor')).toBeVisible({ timeout: 10000 });

        // Type into the editor inside the iframe.
        // Click to focus, then type at the end of line 1.
        await editorFrame.locator('.monaco-editor .view-lines').click();
        await page.keyboard.press('End');
        await page.keyboard.type(' ');

        // onChange should have fired with the updated content.
        await expect(output).toContainText('change:', { timeout: 5000 });
    });
});
