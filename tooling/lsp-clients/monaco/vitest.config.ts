import { configDefaults, defineConfig } from 'vitest/config';
import path from 'path';

export default defineConfig({
    test: {
        projects: [
            {
                resolve: {
                    alias: {
                        'monaco-editor': path.resolve(import.meta.dirname, 'src/test/monacoStub.ts'),
                    },
                },
                test: {
                    name: 'stubbed monaco',
                    include: ['src/**/*.test.ts'],
                    exclude: [...configDefaults.exclude, 'src/**/*.realMonaco.test.ts'],
                },
            },
            {
                resolve: {
                    // monaco-editor only declares an ES module entry
                    mainFields: ['module'],
                },
                test: {
                    name: 'real monaco',
                    include: ['src/**/*.realMonaco.test.ts'],
                    environment: 'happy-dom',
                    setupFiles: ['src/test/canvasStub.ts'],
                    // monaco-editor imports CSS, which only Vite can load
                    server: { deps: { inline: ['monaco-editor'] } },
                },
            },
        ],
    },
});
