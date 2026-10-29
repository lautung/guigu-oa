/**
 * 浏览器套件的被测来源（#114）：默认 Vite dev server，设置 `E2E_BASE_URL` 后指向已构建客户端。
 * seed cookie 必须落在同一个来源上，否则兼容性矩阵里 `localhost` 与 `127.0.0.1` 会拿不到会话。
 */
export const DEFAULT_BASE_URL = 'http://127.0.0.1:9530'

export function e2eBaseURL(): string {
  return (process.env.E2E_BASE_URL || DEFAULT_BASE_URL).replace(/\/$/, '')
}

/** addCookies 用：把 cookie 绑定到被测来源的根路径。 */
export function e2eCookieURL(): string {
  return `${e2eBaseURL()}/`
}
