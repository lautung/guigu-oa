import { expect, test, type APIRequestContext } from '@playwright/test'

import { PASSWORD } from './helpers'

/**
 * SEC-PROFILE-AVATAR（#254）：个人中心头像上传是新的 multipart 面，安全套件钉三类拒绝——
 * 未认证（401 + 208）、类型不支持（201「头像类型不支持」）、超过 2MB（201「头像超过 2 MB 限制」）。
 * 用例只发拒绝型载荷，不覆盖演示账号的头像。
 */

function pngBytes(size: number): Buffer {
  const content = Buffer.alloc(Math.max(size, 8), 1)
  Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]).copy(content, 0)
  return content
}

async function login(request: APIRequestContext, username: string): Promise<string> {
  const response = await request.post('/dev-api/admin/system/index/login', {
    data: { username, password: PASSWORD },
  })
  const body = (await response.json()) as { code: number; data?: { token?: string } }
  expect(body.code, `登录 ${username}`).toBe(200)
  const token = body.data?.token
  expect(token, `登录 ${username} 返回 token`).toBeTruthy()
  return token as string
}

test('未认证的头像上传被拒', async ({ request }) => {
  const response = await request.post('/dev-api/admin/system/profile/avatar', {
    multipart: {
      file: { name: 'a.png', mimeType: 'image/png', buffer: pngBytes(16) },
    },
  })
  expect(response.status()).toBe(401)
  expect((await response.json()).code).toBe(208)
})

test('头像类型不支持被拒', async ({ request }) => {
  const token = await login(request, 'alice')
  const response = await request.post('/dev-api/admin/system/profile/avatar', {
    headers: { token },
    multipart: {
      file: { name: 'a.svg', mimeType: 'image/svg+xml', buffer: Buffer.from('<svg/>') },
    },
  })
  expect(response.status()).toBe(200)
  const body = (await response.json()) as { code: number; message?: string }
  expect(body.code).toBe(201)
  expect(body.message).toBe('头像类型不支持')
})

test('超过 2MB 的头像被拒', async ({ request }) => {
  const token = await login(request, 'alice')
  const response = await request.post('/dev-api/admin/system/profile/avatar', {
    headers: { token },
    multipart: {
      file: {
        name: 'big.png',
        mimeType: 'image/png',
        buffer: pngBytes(2 * 1024 * 1024 + 1),
      },
    },
  })
  expect(response.status()).toBe(200)
  const body = (await response.json()) as { code: number; message?: string }
  expect(body.code).toBe(201)
  expect(body.message).toBe('头像超过 2 MB 限制')
})
