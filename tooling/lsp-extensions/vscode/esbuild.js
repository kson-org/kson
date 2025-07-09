const esbuild = require('esbuild');
const fs = require('fs');
const path = require('path');

const production = process.argv.includes('--production');
const includeTests = process.argv.includes('--tests');

// Base configuration that's shared between all builds
const baseConfig = {
    bundle: true,
    format: 'cjs',
    minify: production,
    sourcemap: !production,
    sourcesContent: false,
    logLevel: 'warning',
};

function copyExtensionFiles() {
    const extensionDir = path.join(__dirname, 'dist', 'extension');
    const sharedExtensionDir = path.join(__dirname, '..', 'shared', 'extension');
    
    // Copy the entire shared extension directory
    fs.cpSync(sharedExtensionDir, extensionDir, { recursive: true });
    
    console.log('[build] Extension files copied from shared');
}

async function main() {
    // Copy shared extension files before building
    copyExtensionFiles();
    
    // Determine output directory and externals based on build type
    const buildConfig = includeTests
        ? {
            external: ['vscode', 'mocha', '@vscode/test-electron', '@vscode/test-web',
                'chromium-bidi/lib/cjs/bidiMapper/BidiMapper',
                'chromium-bidi/lib/cjs/cdp/CdpConnection']
        }
        : {
            external: ['vscode']
        };
    buildConfig.outDir = './dist'

    if (!production && !includeTests) {
        throw new Error('Unknown build type - use --production or --tests');
    }

    // Base entry points for Node.js
    const nodeEntryPoints = {
        client: './src/client/node/ksonClientMain.ts',
        server: './src/server/node/ksonServerMain.ts'
    };

    // Base entry points for browser
    const browserEntryPoints = {
        browserClient: './src/client/browser/ksonClientMain.ts',
        browserServer: './src/server/browser/ksonServerMain.ts'
    };

    // Add test entry points if building tests
    if (includeTests) {
        // Node-only test runners
        nodeEntryPoints['runTests'] = './test/runTests.ts';
        nodeEntryPoints['index.node'] = './test/node/index.node.ts';

        // Browser-only test entry
        browserEntryPoints['index.browser'] = './test/browser/index.browser.ts';

        // Shared test files (built for both platforms)
        const sharedTestEntryPoints = {
            'index.common': './test/index.common.ts',
            'suite/diagnostics.test': './test/suite/diagnostics.test.ts',
            'suite/editing.test': './test/suite/editing.test.ts',
            'suite/formatting.test': './test/suite/formatting.test.ts'
        };

        // Add shared test files to both entry point sets
        Object.assign(nodeEntryPoints, sharedTestEntryPoints);
        Object.assign(browserEntryPoints, sharedTestEntryPoints);
    }

    // Create contexts array to handle multiple builds
    const contexts = [];

    // Main build context (Node.js)
    const nodeCtx = await esbuild.context({
        ...baseConfig,
        entryPoints: nodeEntryPoints,
        outdir: buildConfig.outDir,
        platform: 'node',
        external: buildConfig.external
    });
    contexts.push(nodeCtx);

    // Browser build context - only build if we have browser entry points
    const browserCtx = await esbuild.context({
        ...baseConfig,
        entryPoints: browserEntryPoints,
        outdir: buildConfig.outDir,
        platform: 'browser',
        external: ['vscode', 'path'],
        define: {
            'process.env.NODE_ENV': '"test"',
            'global': 'globalThis'
        },
        inject: ['./test/browser/process-shim.js']
    });
    contexts.push(browserCtx);

    // Execute build
    await Promise.all(contexts.map(ctx => ctx.rebuild()));
    await Promise.all(contexts.map(ctx => ctx.dispose()));
}

main().catch(e => {
  console.error(e);
  process.exit(1);
}); 