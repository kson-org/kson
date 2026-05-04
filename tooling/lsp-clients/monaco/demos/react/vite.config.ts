import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

/** Serves the React demo — `@monaco-editor/react` driving an attached KSON LSP. */
export default defineConfig({
    plugins: [react()],
    worker: {
        format: 'es',
    },
    server: {
        port: 5176,
    },
});
