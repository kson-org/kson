import { test, expect } from '@playwright/test';

/**
 * Verifies the iframe drop-in script wires up correctly when the demo
 * pulls dist-iframe/kson-editor.js from the installed @kson/monaco-editor
 * package: KsonEditor.create() resolves, the iframe mounts a Monaco editor,
 * and getValue/setValue/onChange/dispose work end-to-end via postMessage.
 */
test.describe('Iframe demo', () => {
    test('create -> getValue/setValue -> dispose round-trip', async ({ page }) => {
        await page.goto('http://localhost:5175');

        const log = page.locator('#log');
        await expect(log).toContainText('editor ready', { timeout: 20000 });

        const iframe = page.locator('#editor iframe');
        await expect(iframe).toBeVisible();

        const editorFrame = page.frameLocator('#editor iframe');
        await expect(editorFrame.locator('.monaco-editor')).toBeVisible({ timeout: 15000 });
        await expect(editorFrame.locator('.view-lines')).toContainText('my-project');

        await page.locator('#btn-get').click();
        await expect(log).toContainText('name: "my-project"');

        await page.locator('#btn-set').click();
        await expect(editorFrame.locator('.view-lines')).toContainText('updated');
        await expect(editorFrame.locator('.view-lines')).toContainText('2.0.0');

        await page.locator('#btn-dispose').click();
        await expect(log).toContainText('disposed');
        await expect(iframe).toHaveCount(0);
    });

    test('onChange fires for user edits inside the iframe', async ({ page }) => {
        await page.goto('http://localhost:5175');

        await expect(page.locator('#log')).toContainText('editor ready', { timeout: 20000 });

        const editorFrame = page.frameLocator('#editor iframe');
        await expect(editorFrame.locator('.monaco-editor')).toBeVisible({ timeout: 15000 });

        await editorFrame.locator('.monaco-editor .view-lines').click();
        await page.keyboard.press('End');
        await page.keyboard.type(' ');

        await expect(page.locator('#log')).toContainText('onChange', { timeout: 5000 });
    });
});
