import { defineConfig } from 'vite';
import { resolve } from 'path';

/**
 * Serves the library demo — vite resolves ES module imports from source.
 *
 * The package's `exports` map points at built dist artifacts (so npm
 * consumers don't try to import .ts), but the demo imports `@kson/monaco-editor`
 * to mirror how a real consumer would use it.  These aliases redirect that
 * package name back to the source tree so the demo runs without a prior
 * `vite build`.
 */
export default defineConfig({
    worker: {
        format: 'es',
    },
    resolve: {
        alias: {
            '@kson/monaco-editor/react': resolve(__dirname, 'src/react/index.ts'),
            '@kson/monaco-editor': resolve(__dirname, 'src/index.ts'),
        },
    },
    server: {
        port: 5174,
        open: '/demos/library/index.html',
    },
});
