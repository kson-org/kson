import { defineConfig } from 'vite';
import { resolve } from 'path';

/**
 * Builds the parent-side proxy as a self-contained IIFE script.
 * Output: dist-iframe/kson-editor.js
 *
 * Consumers add `<script src="kson-editor.js">` and call
 * `KsonEditor.create(container, options)` — no npm, no bundler.
 */
export default defineConfig({
    build: {
        target: 'esnext',
        outDir: 'dist-iframe',
        emptyOutDir: false, // preserve the iframe HTML bundle already in dist-iframe/
        lib: {
            entry: resolve(__dirname, 'src/iframe/KsonEditorClient.ts'),
            name: 'KsonEditor',
            fileName: () => 'kson-editor.js',
            formats: ['iife'],
        },
        minify: 'oxc',
        sourcemap: false,
    },
});
