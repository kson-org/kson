const esbuild = require('esbuild');
const {cpSync, writeFileSync} = require('fs');
const {join, basename} = require('path');
const {glob} = require('glob');

/**
 * Depending on the production or test flag we either include test files or not.
 * @type {boolean}
 */
const production = process.argv.includes('--production');
const includeTests = process.argv.includes('--tests');

async function getTestEntries() {
    if (!includeTests) return {};

    // Find all test files automatically
    const testFiles = await glob('test/suite/*.test.ts');
    const entries = {};
    const testList = [];

    // Add each test file as an entry point
    testFiles.forEach(file => {
        // Keep the same output structure
        const entryName = file.replace('.ts', '').replace(/\//g, '/');
        entries[entryName] = `./${file}`;

        // Add to test list for manifest
        testList.push(basename(file, '.ts'));
    });

    // Write test manifest for runtime discovery
    const manifest = {
        tests: testList
    };
    writeFileSync(join(__dirname, 'test', 'test-files.json'), JSON.stringify(manifest, null, 2));
    console.log(`📝 Generated test manifest with ${testList.length} tests`);

    // Add test runners and common files
    entries['runTests'] = './test/runTests.ts';
    entries['test/index.node'] = './test/node/index.node.ts';
    entries['test/index.browser'] = './test/browser/index.browser.ts';
    entries['test/index.common'] = './test/index.common.ts';

    return entries;
}

async function build() {
    // Copy extension files
    cpSync(join(__dirname, '..', 'shared', 'extension'),
        join(__dirname, 'dist', 'extension'),
        {recursive: true});

    const testEntries = await getTestEntries();

    // Common build options
    const baseConfig = {
        bundle: true,
        format: 'cjs',
        minify: production,
        sourcemap: !production,
        outdir: './dist',
        logLevel: 'info',
    };

    // Build for Node.js
    await esbuild.build({
        ...baseConfig,
        entryPoints: {
            'client': './src/client/node/ksonClientMain.ts',
            'server': './src/server/node/ksonServerMain.ts',
            ...testEntries
        },
        platform: 'node',
        external: ['vscode', ...(includeTests ? ['mocha', '@vscode/test-electron', '@vscode/test-web'] : [])]
    });

    // Build for browser
    await esbuild.build({
        ...baseConfig,
        entryPoints: {
            'browserClient': './src/client/browser/ksonClientMain.ts',
            'browserServer': './src/server/browser/ksonServerMain.ts',
            // Add browser test entries when in test mode
            ...(includeTests ? Object.fromEntries(
                Object.entries(testEntries).filter(([key]) =>
                    ['browser', 'common', 'suite'].some(term => key.includes(term))
                )
            ) : {})
        },
        platform: 'browser',
        external: ['vscode', 'path', 'crypto', 'fs'],
        // Provide a stub for minimatch so it doesn't get bundled
        alias: {
            'minimatch': './src/stubs/minimatch.stub.ts'
        },
        define: {
            'global': 'globalThis'
        },
    });

    console.log(`✅ Build complete!`);
}

build().catch(e => {
    console.error('❌ Build failed:', e);
    process.exit(1);
});