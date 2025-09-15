import {test, expect} from '@playwright/test';
import {readFileSync} from 'fs';
import {dirname, join} from 'path';
import {fileURLToPath} from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
/**
 * Smoke test for the Monaco KSON Editor
 *
 * This test verifies the basic functionality:
 * 1. The editor loads when clicking "Start Editor"
 * 2. The example KSON file content is displayed
 * 3. Syntax highlighting is applied
 * 4. Line numbers are visible
 *
 * Prerequisites:
 * - Dev server running on http://localhost:5173 (npm run dev)
 */
test.describe('Monaco KSON Editor - Smoke Test', () => {
    test('should load editor with KSON example and syntax highlighting', async ({page}) => {
        // Step 1: Navigate to the editor page
        await page.goto('http://localhost:5173');
        await expect(page).toHaveTitle('KSON Editor');

        // Step 2: Initialize the editor
        await page.getByRole('button', {name: 'Start Editor'}).click();

        // Step 3: Wait for editor components to load
        await page.waitForSelector('.monaco-editor', {state: 'visible'});
        await page.waitForSelector('.line-numbers', {state: 'visible'});
        await page.waitForTimeout(1000); // Allow time for content rendering

        // Step 4: Extract and verify editor content
        const actualEditorContent = await page.evaluate(() => {
            // Monaco renders each line as a .view-line element
            const lines = document.querySelectorAll('.view-line');
            return Array.from(lines)
                .map(line => line.textContent || '')
                .join('\n');
        });
        // Read the source KSON file content
        const sourceFileContent = readFileSync(
            join(__dirname, '../resources/kson/example.kson'),
            'utf-8'
        );

        // Normalize both strings by replacing NBSP (U+00A0) with regular spaces. Note: the string
        // we read from disk may have OS-specific line endings, which we normalize too.
        const normalizedActualContent = actualEditorContent.replace(/\u00A0/g, ' ');
        const normalizedSourceContent = sourceFileContent
            .replace(/\u00A0/g, ' ')
            .replace(/\r\n/g, '\n');

        // Verify the editor displays the source file content
        expect(normalizedActualContent).toBeTruthy();
        expect(normalizedSourceContent).toContain(normalizedActualContent);

        // Step 5: Verify syntax highlighting
        // Monaco applies syntax highlighting through span elements with classes

        // Monaco uses different classes for tokens. Each class is a number prepended with `mtk`
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
        // Just verify that we can find more than 1 token class
        expect(tokenClasses.length).toBeGreaterThan(1);

        // Step 6: Verify editor UI elements
        const lineNumberCount = await page.locator('.line-numbers').count();
        expect(lineNumberCount).toBeGreaterThan(0);
    });
});