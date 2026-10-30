import { defineConfig } from '@playwright/test'

import { apiBaseURL } from './tests/api/helpers/env'

/**
 * 接口套件（#120 §1）：Playwright APIRequest 打已运行的 Compose 栈，不启动 webServer。
 *
 *     npm run test:api
 *     PLAYWRIGHT_JUNIT_OUTPUT_NAME=test-results/api/junit.xml npm run test:api
 *
 * 入口默认 `DEMO_ORIGIN`（与 scripts/compose-demo-smoke.mjs、真实栈功能套件同一约定）拼 `/dev-api`；
 * `API_BASE_URL` 覆盖完整前缀（直连 8800 时不带 `/dev-api`）。
 */

export default defineConfig({
  testDir: './tests/api',
  globalSetup: './tests/api/global-setup.ts',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  forbidOnly: !!process.env.CI,
  timeout: 90_000,
  reporter: [
    ['list'],
    ['json', { outputFile: 'test-results/api/report.json' }],
    ['junit', { outputFile: 'test-results/api/junit.xml' }],
  ],
  use: {
    baseURL: apiBaseURL,
    extraHTTPHeaders: { Accept: 'application/json' },
    actionTimeout: 15_000,
  },
  expect: { timeout: 10_000 },
})
