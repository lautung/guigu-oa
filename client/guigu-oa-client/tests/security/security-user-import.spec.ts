import { expect, test, type APIRequestContext } from '@playwright/test'

import { PASSWORD } from './helpers'

/**
 * SEC-USER-IMPORT（#259）：用户导入是新的 multipart 写面，安全套件钉四类拒绝——
 * 未认证（401 + 208）、非 xlsx 扩展名（201「仅支持 xlsx 文件」）、超过 2MB
 * （201「导入文件超过 2 MB 限制」）、扩展名对但内容不是 OOXML（201「导入文件不是有效的 Excel 文件」）。
 * 用例只发拒绝型载荷，不写任何用户行。
 */

const IMPORT_PATH = '/dev-api/admin/system/sysUser/import'

async function adminToken(request: APIRequestContext): Promise<string> {
  const response = await request.post('/dev-api/admin/system/index/login', {
    data: { username: 'admin', password: PASSWORD },
  })
  const body = (await response.json()) as { code: number; data?: { token?: string } }
  expect(body.code, 'admin 登录').toBe(200)
  expect(body.data?.token, 'admin 登录返回 token').toBeTruthy()
  return body.data?.token as string
}

function ooxmlBytes(size: number): Buffer {
  const content = Buffer.alloc(Math.max(size, 4), 1)
  Buffer.from([0x50, 0x4b, 0x03, 0x04]).copy(content, 0)
  return content
}

test('未认证的用户导入被拒', async ({ request }) => {
  const response = await request.post(IMPORT_PATH, {
    multipart: {
      file: { name: 'users.xlsx', mimeType: 'application/octet-stream', buffer: ooxmlBytes(16) },
    },
  })
  expect(response.status()).toBe(401)
  expect((await response.json()).code).toBe(208)
})

test('非 xlsx 扩展名的导入被拒', async ({ request }) => {
  const token = await adminToken(request)
  const response = await request.post(IMPORT_PATH, {
    headers: { token },
    multipart: {
      file: { name: 'users.csv', mimeType: 'text/csv', buffer: Buffer.from('username,name') },
    },
  })
  expect(response.status()).toBe(200)
  const body = (await response.json()) as { code: number; message?: string }
  expect(body.code).toBe(201)
  expect(body.message).toBe('仅支持 xlsx 文件')
})

test('超过 2MB 的导入被拒', async ({ request }) => {
  const token = await adminToken(request)
  const response = await request.post(IMPORT_PATH, {
    headers: { token },
    multipart: {
      file: {
        name: 'big.xlsx',
        mimeType: 'application/octet-stream',
        buffer: ooxmlBytes(2 * 1024 * 1024 + 1),
      },
    },
  })
  expect(response.status()).toBe(200)
  const body = (await response.json()) as { code: number; message?: string }
  expect(body.code).toBe(201)
  expect(body.message).toBe('导入文件超过 2 MB 限制')
})

test('畸形 xlsx 在解析前被拒', async ({ request }) => {
  const token = await adminToken(request)
  const response = await request.post(IMPORT_PATH, {
    headers: { token },
    multipart: {
      file: {
        name: 'users.xlsx',
        mimeType: 'application/octet-stream',
        buffer: Buffer.from('not an excel file'),
      },
    },
  })
  expect(response.status()).toBe(200)
  const body = (await response.json()) as { code: number; message?: string }
  expect(body.code).toBe(201)
  expect(body.message).toBe('导入文件不是有效的 Excel 文件')
})

test('缺少 multipart 字段的导入被拒', async ({ request }) => {
  const token = await adminToken(request)
  const response = await request.post(IMPORT_PATH, { headers: { token } })
  expect(response.status()).toBe(200)
  const body = (await response.json()) as { code: number; message?: string }
  expect(body.code).toBe(201)
  expect(body.message).toBe('导入文件不能为空')
})
