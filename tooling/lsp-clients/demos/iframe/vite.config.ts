import { defineConfig } from 'vite';
import { resolve } from 'path';

/** External consumer demo — serves dist-iframe assets from the installed package. */
export default defineConfig({
    publicDir: resolve(__dirname, 'node_modules/@kson/monaco-editor/dist-iframe'),
    server: {
        port: 5175,
        open: '/',
    },
});
