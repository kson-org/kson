import * as cp from 'child_process';
import * as os from 'os';
import * as path from 'path';
import {
  downloadAndUnzipVSCode,
  resolveCliArgsFromVSCodeExecutablePath
} from '@vscode/test-electron';

/**
 * VS Code extension launcher with VSIX.
 *
 * This script downloads VS Code, installs the VSIX package, and launches VS Code.
 */
async function main() {
  try {
    console.log(`Starting VS Code with VSIX...`);

    const projectRootPath = path.resolve(__dirname, '../');
    const workspacePath = path.resolve(projectRootPath, 'test/workspace');
    const vsixPath = path.resolve(projectRootPath, './dist/vscode-kson-plugin.vsix');

    console.log('Downloading VS Code...');
    const vscodeExecutablePath = await downloadAndUnzipVSCode('stable');
    console.log('VS Code download complete.');

    const [cliPath] = resolveCliArgsFromVSCodeExecutablePath(vscodeExecutablePath);

    // Use a short user-data-dir path (must be < 128 characters)
    const userDataDir = path.join(os.tmpdir(), 'vscode-kson');

    console.log('Installing VSIX...');
    const installResult = cp.spawnSync(cliPath, ['--user-data-dir', userDataDir, '--install-extension', vsixPath], {
      encoding: 'utf-8',
      stdio: 'inherit',
      shell: process.platform === 'win32',
    });
    
    if (installResult.error) {
      console.error('Failed to install VSIX:', installResult.error);
      process.exit(1);
    }
    console.log('VSIX installation complete.');

    console.log('Launching VS Code with extension...');

    // Don't use extensionDevelopmentPath since we installed the VSIX
    // --wait keeps the CLI process running until VS Code is closed
    const launchArgs = ['--user-data-dir', userDataDir, '--wait', workspacePath];

    console.log('Launching command:', cliPath);
    console.log('With args:', launchArgs);

    const vscodeProcess = cp.spawn(cliPath, launchArgs, {
      stdio: 'inherit',
      detached: false,
      shell: process.platform === 'win32',
    });

    vscodeProcess.on('error', (err) => {
      console.error('Failed to start VS Code:', err);
      process.exit(1);
    });

    vscodeProcess.on('close', (code) => {
      console.log(`VS Code exited with code ${code}`);
      process.exit(code || 0);
    });

    // Keep the process alive
    process.on('SIGINT', () => {
      console.log('Received SIGINT, closing VS Code...');
      vscodeProcess.kill();
    });

  } catch (err) {
    console.error('Failed to launch VS Code:', err);
    process.exit(1);
  }
}

main();