// Process shim for browser environment
if (typeof globalThis.process === 'undefined') {
  globalThis.process = {
    env: {
      NODE_ENV: 'test'
    },
    version: 'v16.0.0',
    versions: {
      node: '16.0.0'
    },
    platform: 'browser',
    nextTick: (fn) => Promise.resolve().then(fn),
    cwd: () => '/',
    argv: []
  };
}