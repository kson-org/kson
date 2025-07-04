// Common test runner logic shared between Node and browser environments

export async function runTests(mocha: any): Promise<void> {
    return new Promise((resolve, reject) => {
        // Load tests
        const loadTests = async () => {
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