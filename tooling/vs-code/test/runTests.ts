import * as path from 'path';
import {
    downloadAndUnzipVSCode,
    runTests as runNodeTests,
} from '@vscode/test-electron';

async function main() {
    try {
        const extensionDevelopmentPath = path.resolve(__dirname, '../');
        // Create test workspace path for KSON files
        const testWorkspacePath = path.resolve(__dirname, '../test/workspace');

        console.log('Extension path:', extensionDevelopmentPath);
        console.log('Workspace path:', testWorkspacePath);

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

        await testNode();

        console.log('Tests completed successfully.');
    } catch (err) {
        console.error('Failed to run tests:', err);
        process.exit(1);
    }
}

main();