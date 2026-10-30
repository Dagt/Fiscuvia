import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  workers: 2,
  retries: 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: 'http://127.0.0.1:4173',
    browserName: 'chromium',
    trace: 'retain-on-failure',
  },
  webServer: {
    command: 'npm run build && npm run preview',
    url: 'http://127.0.0.1:4173',
    reuseExistingServer: false,
    timeout: 60000,
  },
  projects: [
    {
      name: 'desktop',
      testIgnore: '**/evidence.spec.ts',
      use: { viewport: { width: 1440, height: 900 } },
    },
    {
      name: 'compact',
      testIgnore: '**/evidence.spec.ts',
      use: { viewport: { width: 1280, height: 800 } },
    },
    {
      name: 'tablet',
      testIgnore: '**/evidence.spec.ts',
      use: { viewport: { width: 768, height: 1024 } },
    },
    {
      name: 'mobile',
      testIgnore: '**/evidence.spec.ts',
      use: {
        viewport: { width: 390, height: 844 },
        isMobile: true,
        hasTouch: true,
      },
    },
    {
      name: 'evidence',
      testMatch: '**/evidence.spec.ts',
      use: { viewport: { width: 1440, height: 900 } },
    },
  ],
})
