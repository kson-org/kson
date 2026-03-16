import { defineConfig } from 'vite';
import { resolve } from 'path';

/**
 * Builds the iframe HTML bundle — Monaco + LSP bridge + worker, all self-contained.
 * Output: dist-iframe/kson-editor.html (plus chunked assets/).
 */
export default defineConfig({
    base: './',
    worker: {
        format: 'es',
    },
    build: {
        target: 'esnext',
        outDir: 'dist-iframe',
        rollupOptions: {
            input: resolve(__dirname, 'kson-editor.html'),
        },
        minify: 'esbuild',
        sourcemap: false,
    },
});
