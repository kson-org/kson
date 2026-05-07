import { defineConfig, devices } from '@playwright/test';

/**
 * Single Playwright config covering all three external consumer demos —
 * vanilla (5174), iframe (5175), react (5176).
 *
 * Each demo runs its own Vite dev server; we boot all three so a single
 * `playwright test` invocation exercises every consumer integration.
 */
export default defineConfig({
    testDir: './tests',
    timeout: 60 * 1000,
    expect: {
        timeout: 10000,
    },
    fullyParallel: true,
    forbidOnly: !!process.env.CI,
    retries: process.env.CI ? 2 : 0,
    workers: process.env.CI ? 1 : undefined,
    reporter: 'html',
    use: {
        trace: 'on-first-retry',
    },

    projects: [
        {
            name: 'chromium',
            use: { ...devices['Desktop Chrome'] },
        },
    ],

    webServer: [
        {
            command: 'npm run dev',
            cwd: './vanilla',
            port: 5174,
            reuseExistingServer: !process.env.CI,
            timeout: 120 * 1000,
        },
        {
            command: 'npm run dev',
            cwd: './iframe',
            port: 5175,
            reuseExistingServer: !process.env.CI,
            timeout: 120 * 1000,
        },
        {
            command: 'npm run dev',
            cwd: './react',
            port: 5176,
            reuseExistingServer: !process.env.CI,
            timeout: 120 * 1000,
        },
    ],
});
