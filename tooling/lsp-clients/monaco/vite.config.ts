import { defineConfig } from 'vite';
import { resolve } from 'path';

export default defineConfig({
    base: './',
    worker: {
        format: 'es'
    },
    build: {
        target: 'esnext',
        lib: {
            entry: resolve(__dirname, 'src/index.ts'),
            fileName: 'kson-monaco',
            formats: ['es']
        },
        rollupOptions: {
            external: ['monaco-editor'],
            output: {
                preserveModules: false,
                inlineDynamicImports: false,
            }
        },
        minify: 'oxc',
        sourcemap: true,
    },
    server: {
        port: 5173,
        open: true
    }
});
