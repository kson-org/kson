import { defineConfig } from 'vite';
import importMetaUrlPlugin from '@codingame/esbuild-import-meta-url-plugin';
import { resolve } from 'path';

export default defineConfig(({  }) => {
    return {
        base: './',
        optimizeDeps: {
            esbuildOptions: {
                plugins: [
                    importMetaUrlPlugin
                ]
            },
            include: [
                'monaco-editor-wrapper',
                'vscode-languageclient',
                'vscode-textmate',
                '@codingame/monaco-vscode-textmate-service-override',
                'vscode-oniguruma'
            ]
        },
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
                output: {
                    // Preserve named exports
                    preserveModules: false,
                    // Use a single chunk to ensure exports are accessible
                    inlineDynamicImports: false
                }
            },
            // Enable code splitting for better lazy loading
            minify: 'esbuild',
            sourcemap: true,
        },
        server: {
            port: 5173,
            open: true
        }
    };
});