// Node.js test runner entry point
import Mocha from 'mocha';
import { runTests } from '../index.common';
import { nodeConfig } from '../mocha-config';

export function run(): Promise<void> {
  // Create the mocha test instance for Node
  const mocha = new Mocha(nodeConfig);

  return runTests(mocha, false);
}