import { defineConfig } from 'vite';

/** Serves the library demo — vite resolves ES module imports from source. */
export default defineConfig({
    worker: {
        format: 'es',
    },
    server: {
        port: 5174,
        open: '/demos/library/index.html',
    },
});
