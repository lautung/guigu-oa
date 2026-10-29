import { defineConfig } from '@playwright/test'

const port = 9530

/**
 * 兼容性矩阵（#114）：`E2E_BASE_URL` 指向已构建的客户端（Compose 栈 9530 的 nginx 产物），
 * 未设置时沿用 Vite dev server。mock 套件只替换 `/dev-api`，静态资源来自被指向的来源。
 */
const externalBaseURL = process.env.E2E_BASE_URL?.replace(/\/$/, '')

export default defineConfig({
  testDir: './tests/e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? 'line' : 'list',
  use: {
    baseURL: externalBaseURL ?? `http://127.0.0.1:${port}`,
    trace: 'retain-on-failure',
  },
  projects: [
    {
      name: 'desktop-chromium',
      use: { browserName: 'chromium', viewport: { width: 1280, height: 900 } },
    },
    {
      name: 'mobile-chromium',
      use: {
        browserName: 'chromium',
        viewport: { width: 390, height: 844 },
        isMobile: true,
        hasTouch: true,
      },
    },
    {
      name: 'desktop-webkit',
      use: { browserName: 'webkit', viewport: { width: 1280, height: 900 } },
    },
    {
      name: 'mobile-webkit',
      use: {
        browserName: 'webkit',
        viewport: { width: 390, height: 844 },
        isMobile: true,
        hasTouch: true,
      },
    },
    {
      // 1440 只做关键页截图扫描（#114），不跑全量用例。
      name: 'desktop-1440-chromium',
      testMatch: /compatibility-pages\.spec\.ts/,
      use: { browserName: 'chromium', viewport: { width: 1440, height: 900 } },
    },
  ],
  webServer: externalBaseURL
    ? undefined
    : {
        command: `npm run dev -- --host 127.0.0.1 --strictPort`,
        url: `http://127.0.0.1:${port}`,
        reuseExistingServer: !process.env.CI,
        timeout: 120_000,
      },
})
