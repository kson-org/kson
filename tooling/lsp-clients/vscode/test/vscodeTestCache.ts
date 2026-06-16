import * as os from 'os';
import * as path from 'path';

// Shared, env-overridable cache so checkouts/CI reuse one VS Code download.
export function getVSCodeTestCachePath(): string {
  const dir = resolveVSCodeTestCachePath();
  console.log(`VS Code test cache: ${dir} (override with VSCODE_TEST_CACHE)`);
  return dir;
}

// kson-namespaced OS cache dir so it's clearly attributable and easy to find/purge.
function resolveVSCodeTestCachePath(): string {
  if (process.env.VSCODE_TEST_CACHE) {
    return process.env.VSCODE_TEST_CACHE;
  }
  const home = os.homedir();
  switch (os.platform()) {
    case 'darwin':
      return path.join(home, 'Library', 'Caches', 'kson-vscode-test');
    case 'win32':
      return path.join(process.env.APPDATA ?? path.join(home, 'AppData', 'Roaming'), 'kson-vscode-test');
    default:
      return path.join(home, '.cache', 'kson-vscode-test');
  }
}
