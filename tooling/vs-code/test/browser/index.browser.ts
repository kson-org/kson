// Browser test runner entry point
import 'mocha/mocha';
import { runTests } from '../index.common';

export function run(): Promise<void> {
  // In browser, mocha is provided by @vscode/test-web
  return runTests(mocha, true);
}