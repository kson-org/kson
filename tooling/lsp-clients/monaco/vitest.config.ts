import { defineConfig } from 'vitest/config';
import path from 'path';

export default defineConfig({
    test: {
        include: ['src/**/*.test.ts'],
    },
    resolve: {
        alias: {
            'monaco-editor': path.resolve(__dirname, 'src/test/monacoStub.ts'),
        },
    },
});
