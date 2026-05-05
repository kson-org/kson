import { defineConfig } from 'vite';

/** External consumer demo — resolves @kson/monaco-editor from the package's built output. */
export default defineConfig({
    worker: {
        format: 'es',
    },
    server: {
        port: 5174,
        open: '/',
    },
});
