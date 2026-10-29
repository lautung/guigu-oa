import { defineConfig } from '@playwright/test'

/**
 * 安全用例（#113）：只跑存储型 XSS 这类必须真实浏览器才能证明的用例。
 * 不启动 webServer——先按 docs/development/compose-demo.md 把 Compose 栈跑起来。
 *
 *     npm run test:security
 */

const origin = (process.env.DEMO_ORIGIN || 'http://localhost:9530').replace(/\/$/, '')

export default defineConfig({
  testDir: '.',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  timeout: 90_000,
  reporter: [
    ['list'],
    ['json', { outputFile: 'test-results/xss-report.json' }],
    ['junit', { outputFile: 'test-results/xss-report.xml' }],
  ],
  use: {
    baseURL: origin,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    actionTimeout: 15_000,
    navigationTimeout: 20_000,
  },
  expect: { timeout: 10_000 },
  projects: [
    {
      name: 'security-chromium',
      use: { browserName: 'chromium', viewport: { width: 1280, height: 900 } },
    },
  ],
})
