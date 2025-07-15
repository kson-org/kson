// Common test runner logic shared between Node and browser environments
import testManifest from './test-files.json';

export async function runTests(mocha: any, isBrowser: boolean): Promise<void> {
    return new Promise((resolve, reject) => {
        // Load tests
        const loadTests = async () => {
            if (isBrowser) {
                // Browser: use dynamic imports from manifest
                for (const testName of testManifest.tests) {
                    await import(`./suite/${testName}.ts`);
                }
            } else {
                // Node: use addFile
                const path = await import('path');
                const testsRoot = path.resolve(__dirname);

                // Use test files from manifest
                testManifest.tests.forEach(testName => {
                    mocha.addFile(path.resolve(testsRoot, 'suite', `${testName}.js`));
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