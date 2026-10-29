import { expect, test } from '@playwright/test'

import { CLIENT_PAGES } from './helpers'

/**
 * SEC-LEAK-09（#140）：客户端不暴露 nginx 精确版本。
 *
 * `server_tokens off` 写在 nginx.conf.template 里，模板是否真的进了镜像、envsubst 之后是否
 * 生效，只有真实栈能回答；所以断言放在这一层，与会话无关。明确版本号的判定与
 * scripts/security-scan.mjs 的 SEC-LEAK-09 保持一致：Server 头里没有 `x.y` 即通过。
 */
test('客户端响应头不暴露 nginx 精确版本', async ({ request }) => {
  for (const path of CLIENT_PAGES) {
    const response = await request.get(path)
    expect(response.status(), `${path} 状态`).toBe(200)
    const server = response.headers().server ?? ''
    expect(server, `${path} 的 Server 头泄露版本：${server}`).not.toMatch(/\d+\.\d+/)
  }
})
