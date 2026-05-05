import { defineConfig } from 'vite';
import { resolve } from 'path';
import dts from 'vite-plugin-dts';

// Multi-entry library build.  `build.lib.entry` as an object emits both
// `@kson/monaco-editor` and `@kson/monaco-editor/react` as separate dist
// files (vs. the single-entry shape we had before), with `.d.ts` siblings
// from `vite-plugin-dts`.  Peer deps are externalized so consumers' copies
// of monaco/react win at runtime.
export default defineConfig({
    base: './',
    worker: {
        format: 'es',
    },
    plugins: [
        dts({
            entryRoot: 'src',
            outDir: 'dist',
            include: ['src/**/*.ts'],
            exclude: ['src/**/*.test.ts', 'src/test/**'],
        }),
    ],
    build: {
        target: 'esnext',
        emptyOutDir: true,
        outDir: 'dist',
        sourcemap: true,
        minify: 'oxc',
        lib: {
            entry: {
                // Filenames mirror `vite-plugin-dts`'s output paths so each
                // `.js` sits next to its `.d.ts`.
                index: resolve(__dirname, 'src/index.ts'),
                'react/index': resolve(__dirname, 'src/react/index.ts'),
            },
            formats: ['es'],
        },
        rollupOptions: {
            external: [
                'monaco-editor',
                /^monaco-editor\//,
                'react',
                /^react\//,
                'react-dom',
                /^react-dom\//,
            ],
            output: {
                entryFileNames: '[name].js',
                chunkFileNames: 'chunks/[name]-[hash].js',
                preserveModules: false,
            },
        },
    },
    server: {
        port: 5173,
        open: false,
    },
});
