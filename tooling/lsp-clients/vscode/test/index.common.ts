// Common test runner logic shared between Node and browser environments
export async function runTests(mocha: any, isBrowser: boolean): Promise<void> {
    return new Promise((resolve, reject) => {
        // Configure mocha
        if (isBrowser) {
            mocha.setup({
                /**
                 * Set this to 'bdd'(behavior-driven-development) for now. Changing this throws unexpected errors when running the
                 * tests.
                 */
                ui: 'bdd',
                reporter: undefined,
                timeout: 10000
            });
        }

        // Load tests
        const loadTests = async () => {
            if (isBrowser) {
                // Browser: use dynamic imports
                await import('./suite/diagnostics.test');
                await import('./suite/editing.test');
                await import('./suite/formatting.test');
            } else {
                // Node: use addFile
                const path = await import('path');
                const testsRoot = path.resolve(__dirname);

                // Directly add test files without glob
                const testFiles = [
                    'suite/diagnostics.test.js',
                    'suite/editing.test.js',
                    'suite/formatting.test.js'
                ];

                testFiles.forEach(f => {
                    mocha.addFile(path.resolve(testsRoot, f));
                });
            }
        };

        // Run tests
        loadTests().then(() => {
            try {
                mocha.run((failures: number) => {
                    if (failures > 0) {
                        reject(new Error(`${failures} tests failed.`));
                    } else {
                        resolve();
                    }
                });
            } catch (err) {
                console.error(err);
                reject(err);
            }
        }).catch(err => {
            console.error('Failed to load tests:', err);
            reject(err);
        });
    });
}