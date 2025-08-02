// Browser test runner entry point
import 'mocha/mocha';
import { runTests } from '../index.common';
import { browserConfig } from '../mocha-config';

export function run(): Promise<void> {
  // Configure mocha for browser
  mocha.setup(browserConfig);
  
  return runTests(mocha, true);
}