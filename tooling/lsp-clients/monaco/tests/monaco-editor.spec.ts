import {test, expect} from '@playwright/test';

/**
 * Smoke test for the Monaco KSON Editor
 *
 * Verifies basic functionality:
 * 1. Both editors load automatically on page load
 * 2. KSON content is displayed in each editor
 * 3. Syntax highlighting is applied
 * 4. Line numbers are visible
 *
 * Prerequisites:
 * - Dev server running on http://localhost:5173 (npm run dev)
 */
test.describe('Monaco KSON Editor - Smoke Test', () => {
    test('should load editors with KSON content and syntax highlighting', async ({page}) => {
        await page.goto('http://localhost:5173');
        await expect(page).toHaveTitle('KSON Monaco Editor');

        // Wait for both editors to appear
        const editors = page.locator('.monaco-editor');
        await expect(editors.first()).toBeVisible({timeout: 10000});
        await expect(editors).toHaveCount(2);

        // Wait for line numbers to render
        await page.waitForSelector('.line-numbers', {state: 'visible'});
        await page.waitForTimeout(1000); // Allow time for content rendering

        // Verify the left editor displays the expected content
        const leftEditorContent = await page.evaluate(() => {
            const container = document.getElementById('editor-left');
            if (!container) return '';
            const lines = container.querySelectorAll('.view-line');
            return Array.from(lines)
                .map(line => line.textContent || '')
                .join('\n');
        });

        const normalizedLeft = leftEditorContent.replace(/\u00A0/g, ' ').trim();
        expect(normalizedLeft).toContain('kson-monaco-editor');

        // Verify syntax highlighting (multiple token classes present)
        const tokenClasses = await page.evaluate(() => {
            const spans = document.querySelectorAll('.view-line span[class*="mtk"]');
            const classes = new Set<string>();
            spans.forEach(span => {
                const match = span.className.match(/mtk\d+/);
                if (match) {
                    classes.add(match[0]);
                }
            });
            return Array.from(classes);
        });
        expect(tokenClasses.length).toBeGreaterThan(1);

        // Verify line numbers are visible
        const lineNumberCount = await page.locator('.line-numbers').count();
        expect(lineNumberCount).toBeGreaterThan(0);
    });
});
