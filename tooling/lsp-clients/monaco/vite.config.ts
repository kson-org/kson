import { defineConfig } from 'vite';
import importMetaUrlPlugin from '@codingame/esbuild-import-meta-url-plugin';
import { resolve } from 'path';

export default defineConfig(({  }) => {
    return {
        base: './',
        resolve: {
            dedupe: ['vscode']
        },
        optimizeDeps: {
            esbuildOptions: {
                plugins: [
                    importMetaUrlPlugin
                ]
            },
            include: [
                'vscode-textmate',
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
            minify: 'esbuild',
            sourcemap: true,
        },
        server: {
            port: 5173,
            open: true
        }
    };
});
