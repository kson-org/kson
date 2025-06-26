import * as cp from 'child_process';
import * as path from 'path';
import {
  downloadAndUnzipVSCode,
  resolveCliArgsFromVSCodeExecutablePath,
  runTests
} from '@vscode/test-electron';

async function main() {
  try {
    console.log('Starting VSIX test setup...');

    const extensionDevelopmentPath = path.resolve(__dirname, '../../');
    const extensionTestsPath = path.resolve(__dirname, './suite/index');
    const vsixPath = path.resolve(extensionDevelopmentPath, './out/vscode-kson-plugin.vsix');

    console.log('Downloading VS Code...');
    const vscodeExecutablePath = await downloadAndUnzipVSCode('stable');
    console.log('VS Code download complete.');

    const [cliPath, ...args] = resolveCliArgsFromVSCodeExecutablePath(vscodeExecutablePath);

    console.log('Installing VSIX...');
    // Install the .vsix
    cp.spawnSync(
      cliPath,
      [...args, '--install-extension', vsixPath, '--disable-extensions'],
      {
        encoding: 'utf-8',
        stdio: 'inherit'
      }
    );
    console.log('VSIX installation complete.');

    console.log('Running tests...');
    // Run the extension test
    await runTests({
      vscodeExecutablePath,
      extensionDevelopmentPath: '/Users/bart/workspace/code/kson',
      // extensionDevelopmentPath: '.',
      extensionTestsPath,
      launchArgs: [path.resolve(extensionDevelopmentPath, 'test/workspace')]
    });
    console.log('Tests finished.');
  } catch (err) {
    console.error('Failed to run tests');
    process.exit(1);
  }
}

main(); 