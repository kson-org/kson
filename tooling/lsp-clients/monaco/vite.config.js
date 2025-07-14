import {defineConfig} from 'vite';
import importMetaUrlPlugin from '@codingame/esbuild-import-meta-url-plugin';

export default defineConfig({
    server: {
        port: 5173,
        open: true
    },
    build: {
        target: 'esnext'
    },
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
    }
});