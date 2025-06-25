const {defineConfig} = require('@vscode/test-cli');

module.exports = defineConfig([
    {
    label: 'integrationTest',
    files: 'out/test/**/*.test.js',
    version: 'insiders',
    workspaceFolder: './test/workspace',
    mocha: {
      timeout: 20000
    }
  }
])