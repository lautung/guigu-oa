/**
 * 接口套件的入口（#120 §1 / 附注）：
 *
 * - `DEMO_ORIGIN`（默认 `http://localhost:9530`）是 Compose 的 nginx，API 经同源 `/dev-api` 代理；
 * - `API_BASE_URL` 覆盖完整前缀（直连 `http://localhost:8800` 时用，此时不带 `/dev-api`）。
 *
 * 单独成模块，让 playwright.api.config.ts 与测试 helper 读同一份解析结果。
 */
const origin = (process.env.DEMO_ORIGIN || 'http://localhost:9530').replace(/\/$/, '')

export const apiBaseURL = (process.env.API_BASE_URL || `${origin}/dev-api`).replace(/\/$/, '')
