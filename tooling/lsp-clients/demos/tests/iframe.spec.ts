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

    // Monaco degrades silently when it can't load something it needs (e.g. its
    // editor worker): the editor still mounts, but features like completion
    // hang. Exercise completion and fail on any console or page error.
    test('completion works and no errors are logged', async ({ page }) => {
        const errors: string[] = [];
        page.on('console', msg => { if (msg.type() === 'error') errors.push(msg.text()); });
        page.on('pageerror', err => errors.push(err.message));

        await page.goto('http://localhost:5175');
        await expect(page.locator('#log')).toContainText('editor ready', { timeout: 20000 });

        const editorFrame = page.frameLocator('#editor iframe');
        await expect(editorFrame.locator('.view-lines')).toContainText('my-project', { timeout: 15000 });

        // Open a blank line just inside the closing brace and trigger completion.
        await editorFrame.locator('.monaco-editor .view-lines').click();
        await page.keyboard.press('ControlOrMeta+End');
        await page.keyboard.press('Home');
        await page.keyboard.press('Enter');
        await page.keyboard.press('ArrowUp');
        await page.keyboard.press('Control+Space');

        const suggestions = editorFrame.locator('.suggest-widget.visible .monaco-list-row');
        await expect(suggestions.first()).toBeVisible({ timeout: 10000 });

        expect(errors).toEqual([]);
    });
});
