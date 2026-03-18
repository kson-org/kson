import { defineConfig } from 'vite';

/**
 * Serves the iframe demo.  Root is the monaco directory so that the
 * relative paths to dist-iframe/ resolve correctly.
 */
export default defineConfig({
    server: {
        port: 5175,
        open: '/demos/iframe/index.html',
    },
});
