import * as path from 'path';
import {runTests as runBrowserTests} from '@vscode/test-web';
import {
    downloadAndUnzipVSCode,
    runTests as runNodeTests,
} from '@vscode/test-electron';

async function main() {
    try {
        // test-browser or test-node or test-all
        const runMode = process.env.RUN_MODE || 'test-all';

        const extensionDevelopmentPath = path.resolve(__dirname, '../');
        // Create test workspace path for KSON files
        const testWorkspacePath = path.resolve(__dirname, '../test/workspace');

        console.log('Extension path:', extensionDevelopmentPath);
        console.log('Workspace path:', testWorkspacePath);

        /**
         * Runs browser tests
         */
        async function testBrowser() {
            console.log('Starting web extension tests...');

            const extensionBrowserTestsPath = path.resolve(__dirname, './index.browser.js');
            console.log('Test path:', extensionBrowserTestsPath);
            await runBrowserTests({
                browserType: 'chromium',
                extensionDevelopmentPath,
                extensionTestsPath: extensionBrowserTestsPath,
                folderPath: testWorkspacePath,
                headless: false,
                permissions: ['clipboard-read', 'clipboard-write']
            });
        }

        /**
         * Runs node tests
         */
        async function testNode() {
            const extensionNodeTestsPath = path.resolve(__dirname, './index.node.js');

            console.log('Downloading VS Code...');
            const vscodeExecutablePath = await downloadAndUnzipVSCode('stable');
            console.log('VS Code download complete.');

            await runNodeTests({
                vscodeExecutablePath,
                extensionDevelopmentPath,
                extensionTestsPath: extensionNodeTestsPath,
                launchArgs: [testWorkspacePath]
            });
        }

        switch (runMode) {
            case 'test-node': {
                await testNode();
                break;
            }
            case 'test-browser': {
                await testBrowser();
                break;
            }
            case 'test-all': {
                await testNode();
                await testBrowser();
                break;
            }
        }

        console.log('Tests completed successfully.');
    } catch (err) {
        console.error('Failed to run tests:', err);
        process.exit(1);
    }
}

main();