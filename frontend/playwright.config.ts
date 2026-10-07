import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './tests',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  // A html-only reporter writes nothing to stdout, which makes a CI failure a
  // red X with no explanation. Keep the console list, and still produce the html
  // report (with `open: never` so it does not try to launch a browser on a box
  // that has no display).
  reporter: process.env.CI
    ? [['list'], ['html', { open: 'never' }]]
    : [['html'], ['list']],
  use: {
    baseURL: 'http://localhost:4200',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      // Uses the full Chromium binary in new headless mode instead of the separate
      // chrome-headless-shell build. One binary rather than two, and it keeps the
      // suite runnable where only the main Chromium download succeeds.
      use: { ...devices['Desktop Chrome'], channel: 'chromium' },
    },
    {
      name: 'firefox',
      use: { ...devices['Desktop Firefox'] },
    },
    {
      name: 'webkit',
      use: { ...devices['Desktop Safari'] },
    },
    {
      name: 'mobile-chrome',
      use: { ...devices['Pixel 5'] },
    },
    {
      name: 'mobile-safari',
      use: { ...devices['iPhone 12'] },
    },
  ],
  webServer: {
    command: 'npm run start',
    url: 'http://localhost:4200',
    reuseExistingServer: !process.env.CI,
    timeout: 120000,
  },
  expect: {
    toHaveScreenshot: {
      maxDiffPixels: 100,
    },
  },
});