const esbuild = require('esbuild');

const production = process.argv.includes('--production');
const includeTests = process.argv.includes('--tests');

/**
 * @type {import('esbuild').Plugin}
 */
const esbuildProblemMatcherPlugin = {
  name: 'esbuild-problem-matcher',

  setup(build) {
    build.onStart(() => {
      console.log('[watch] build started');
    });
    build.onEnd(result => {
      result.errors.forEach(({ text, location }) => {
        console.error(`✘ [ERROR] ${text}`);
        if (location == null) return;
        console.error(`    ${location.file}:${location.line}:${location.column}:`);
      });
      console.log('[watch] build finished');
    });
  }
};

// Base configuration that's shared between all builds
const baseConfig = {
    bundle: true,
    format: 'cjs',
    minify: production,
    sourcemap: !production,
    sourcesContent: false,
    logLevel: 'warning',
    plugins: [esbuildProblemMatcherPlugin]
};

async function main() {
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
    // Add test entry points if building tests
    if (includeTests) {
        // Node-only test runners
        nodeEntryPoints['runTests'] = './test/runTests.ts';
        nodeEntryPoints['index.node'] = './test/node/index.node.ts';
        // Shared test files (built for both platforms)
        const sharedTestEntryPoints = {
            'index.common': './test/index.common.ts',
            'suite/diagnostics.test': './test/suite/diagnostics.test.ts',
            'suite/editing.test': './test/suite/editing.test.ts',
            'suite/formatting.test': './test/suite/formatting.test.ts'
        };

        // Add shared test files to both entry point sets
        Object.assign(nodeEntryPoints, sharedTestEntryPoints);
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


    // Execute build
    await Promise.all(contexts.map(ctx => ctx.rebuild()));
    await Promise.all(contexts.map(ctx => ctx.dispose()));
}

main().catch(e => {
  console.error(e);
  process.exit(1);
}); 