// Node.js test runner entry point
import Mocha from 'mocha';
import { runTests } from '../index.common';

export function run(): Promise<void> {
  // Create the mocha test instance for Node
  const mocha = new Mocha({
    /**
     * Set this to 'bdd'(behavior-driven-development) for now. Changing this throws unexpected errors when running the
     * tests.
     */
    ui: 'bdd',
    color: true,
    timeout: 20000
  });

  return runTests(mocha, false);
}