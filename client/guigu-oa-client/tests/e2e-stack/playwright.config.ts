import { defineConfig } from '@playwright/test'

/**
 * 真实栈功能套件（#121 §2）：指向 Compose 统一客户端，真实 service-oa / MySQL / Redis / MinIO。
 * 不启动 webServer——先按 docs/development/compose-demo.md 把栈跑起来再执行。
 *
 *     npm run test:e2e:stack
 *
 * 环境地址沿用 DEMO_ORIGIN（与 scripts/compose-demo-smoke.mjs 同一约定）。
 */

const origin = (process.env.DEMO_ORIGIN || 'http://localhost:9530').replace(/\/$/, '')

export default defineConfig({
  testDir: '.',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  forbidOnly: !!process.env.CI,
  timeout: 90_000,
  reporter: [
    ['list'],
    ['json', { outputFile: 'test-results/e2e-stack/report.json' }],
    ['junit', { outputFile: 'test-results/e2e-stack/report.xml' }],
  ],
  use: {
    baseURL: origin,
    trace: 'retain-on-failure',
    actionTimeout: 15_000,
    navigationTimeout: 20_000,
  },
  expect: { timeout: 10_000 },
  projects: [
    {
      name: 'stack-chromium',
      use: { browserName: 'chromium', viewport: { width: 1280, height: 900 } },
    },
  ],
})
