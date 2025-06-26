import * as cp from 'child_process';
import * as path from 'path';
import {
  downloadAndUnzipVSCode,
  resolveCliArgsFromVSCodeExecutablePath,
  runTests
} from '@vscode/test-electron';

async function main() {
  try {
    const useVsix = process.env.USE_VSIX === 'true';
    console.log(`Starting test setup (USE_VSIX=${useVsix})...`);

    const projectRootPath = path.resolve(__dirname, '../../');
    const extensionTestsPath = path.resolve(__dirname, './index');
    const workspacePath = path.resolve(projectRootPath, 'test/workspace');

    console.log('Downloading VS Code...');
    const vscodeExecutablePath = await downloadAndUnzipVSCode('stable');
    console.log('VS Code download complete.');

    let extensionDevelopmentPath: string;

    if (useVsix) {
      const vsixPath = path.resolve(projectRootPath, './out/vscode-kson-plugin.vsix');
      const [cliPath, ...args] = resolveCliArgsFromVSCodeExecutablePath(vscodeExecutablePath);

      console.log('Installing VSIX...');
      cp.spawnSync(cliPath, [...args, '--install-extension', vsixPath], {
        encoding: 'utf-8',
        stdio: 'inherit'
      });
      console.log('VSIX installation complete.');

      // Use an empty directory for extensionDevelopmentPath to ensure we're testing the installed VSIX.
      extensionDevelopmentPath = path.resolve(projectRootPath, 'test/empty-for-vsix-test');
    } else {
      extensionDevelopmentPath = projectRootPath;
    }

    console.log('Running tests...');
    // Run the extension test
    await runTests({
      vscodeExecutablePath,
      extensionDevelopmentPath,
      extensionTestsPath,
      launchArgs: [workspacePath]
    });
    console.log('Tests finished.');
  } catch (err) {
    console.error('Failed to run tests');
    process.exit(1);
  }
}

main(); 