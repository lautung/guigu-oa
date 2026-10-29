import { test, expect } from '@playwright/test'

import {
  ADMIN,
  ALICE,
  Api,
  BOB,
  CAROL,
  PLAIN_USER,
  expectCode,
  type PageResult,
  type ProcessAttachmentList,
  type ProcessRecord,
  type UploadGrant,
} from './helpers/api'

/**
 * E 附件（#120 §6 E1–E6）。
 *
 * 上传时序按客户端真实链路：登记（拿预签名）→ PUT 对象存储 → complete。
 * 预签名地址是 S3_PUBLIC_ENDPOINT（默认 http://localhost:9000），与 CSP connect-src 成对。
 *
 * 成员矩阵（ADR 0011）：可读=可下载；可上传=当前处理人/申请人；越权统一 205。
 */

interface DemoProcess {
  processId: number
  endedProcessId: number
  attachmentId: number
}

async function demoProcess(api: Api): Promise<DemoProcess> {
  let pendingId: number | undefined
  let endedId: number | undefined
  for (let page = 1; page <= 20; page += 1) {
    const result = await api.call<PageResult<ProcessRecord>>(
      ALICE,
      'get',
      `/admin/process/findStarted/${page}/100`,
    )
    for (const record of result.records) {
      const values = record.formValues ?? ''
      if (pendingId === undefined && values.includes('demo-pending')) pendingId = record.id
      if (endedId === undefined && values.includes('demo-ended')) endedId = record.id
    }
    if (pendingId !== undefined && endedId !== undefined) break
    if (page * 100 >= result.total) break
  }
  if (pendingId === undefined || endedId === undefined) {
    throw new Error('找不到演示审批实例：确认 Compose 栈已按 #128 播种')
  }
  const list = await api.call<ProcessAttachmentList>(
    BOB,
    'get',
    `/admin/attachments/processes/${pendingId}`,
  )
  const attachment = list.attachments[0]
  if (!attachment) throw new Error('演示待处理审批没有附件')
  return { processId: pendingId, endedProcessId: endedId, attachmentId: attachment.id }
}

function draftId(): string {
  return crypto.randomUUID()
}

async function grantDraft(
  api: Api,
  user: string,
  options: { fileName?: string; contentType?: string; sizeBytes?: number; draft?: string } = {},
): Promise<UploadGrant & { draftId: string }> {
  const draft = options.draft ?? draftId()
  const grant = await api.call<UploadGrant>(user, 'post', '/admin/attachments/uploads', {
    draftId: draft,
    fileName: options.fileName ?? '接口测试.txt',
    contentType: options.contentType ?? 'text/plain',
    sizeBytes: options.sizeBytes ?? 5,
  })
  return { ...grant, draftId: draft }
}

async function completeDraft(api: Api, user: string): Promise<void> {
  const grant = await grantDraft(api, user)
  await api.putObject(grant.uploadUrl, grant.headers, Buffer.from('hello'))
  await api.call(user, 'post', `/admin/attachments/${grant.attachmentId}/complete`)
  await api.call(user, 'delete', `/admin/attachments/pending/${grant.attachmentId}`)
}

test.describe('E 附件', () => {
  test('E1 uploads：草稿正例 / 超过 50 MiB 201 / 非成员 205', async ({ request }) => {
    const api = new Api(request)
    const demo = await demoProcess(api)

    const grant = await grantDraft(api, ALICE)
    try {
      expect(grant.attachmentId).toBeGreaterThan(0)
      expect(grant.uploadUrl).toContain('http')
      expect(grant.headers?.['Content-Type']).toBe('text/plain')
    } finally {
      await api.call(ALICE, 'delete', `/admin/attachments/pending/${grant.attachmentId}`)
    }

    const oversized = await api.envelope(ALICE, 'post', '/admin/attachments/uploads', {
      draftId: draftId(),
      fileName: '大文件.txt',
      contentType: 'text/plain',
      sizeBytes: 52_428_801,
    })
    expectCode(oversized.body, 201, 'E1 超过 50 MiB')

    const nonMember = await api.envelope(ADMIN, 'post', '/admin/attachments/uploads', {
      processId: demo.processId,
      fileName: '越权.txt',
      contentType: 'text/plain',
      sizeBytes: 5,
    })
    expectCode(nonMember.body, 205, 'E1 非成员补传')
  })

  test('E2 complete：正例 / 对象缺失 201 / 大小不符 201 / 非 owner 205', async ({ request }) => {
    const api = new Api(request)

    const grant = await grantDraft(api, ALICE)
    await api.putObject(grant.uploadUrl, grant.headers, Buffer.from('hello'))
    const completed = await api.call<{ id: number; fileName: string }>(
      ALICE,
      'post',
      `/admin/attachments/${grant.attachmentId}/complete`,
    )
    expect(completed.fileName).toBe('接口测试.txt')
    const drafts = await api.call<{ id: number }[]>(
      ALICE,
      'get',
      `/admin/attachments/drafts/${grant.draftId}`,
    )
    expect(drafts.map((item) => item.id)).toContain(grant.attachmentId)
    await api.call(ALICE, 'delete', `/admin/attachments/pending/${grant.attachmentId}`)

    const missing = await grantDraft(api, ALICE)
    const missingComplete = await api.envelope(
      ALICE,
      'post',
      `/admin/attachments/${missing.attachmentId}/complete`,
    )
    expectCode(missingComplete.body, 201, 'E2 对象缺失')
    await api.call(ALICE, 'delete', `/admin/attachments/pending/${missing.attachmentId}`)

    const mismatch = await grantDraft(api, ALICE, { sizeBytes: 10 })
    await api.putObject(mismatch.uploadUrl, mismatch.headers, Buffer.from('hello'))
    const mismatchComplete = await api.envelope(
      ALICE,
      'post',
      `/admin/attachments/${mismatch.attachmentId}/complete`,
    )
    expectCode(mismatchComplete.body, 201, 'E2 大小不符')
    await api.call(ALICE, 'delete', `/admin/attachments/pending/${mismatch.attachmentId}`)

    const foreign = await grantDraft(api, ALICE)
    const foreignComplete = await api.envelope(
      BOB,
      'post',
      `/admin/attachments/${foreign.attachmentId}/complete`,
    )
    expectCode(foreignComplete.body, 205, 'E2 非 owner')
    await api.call(ALICE, 'delete', `/admin/attachments/pending/${foreign.attachmentId}`)
  })

  test('E3 drafts：owner 正例 / 他人空 / 非法 draftId 201', async ({ request }) => {
    const api = new Api(request)
    const grant = await grantDraft(api, ALICE)
    try {
      await api.putObject(grant.uploadUrl, grant.headers, Buffer.from('hello'))
      await api.call(ALICE, 'post', `/admin/attachments/${grant.attachmentId}/complete`)

      const mine = await api.call<{ id: number }[]>(
        ALICE,
        'get',
        `/admin/attachments/drafts/${grant.draftId}`,
      )
      expect(mine.map((item) => item.id)).toContain(grant.attachmentId)

      const others = await api.call<{ id: number }[]>(
        BOB,
        'get',
        `/admin/attachments/drafts/${grant.draftId}`,
      )
      expect(others).toEqual([])
    } finally {
      await api.call(ALICE, 'delete', `/admin/attachments/pending/${grant.attachmentId}`)
    }

    const invalid = await api.envelope(ALICE, 'get', '/admin/attachments/drafts/not-a-uuid')
    expectCode(invalid.body, 201, 'E3 非法 draftId')
  })

  test('E4 deletePending：PENDING 正例 / 已绑定 205 / 非 owner 205', async ({ request }) => {
    const api = new Api(request)
    const demo = await demoProcess(api)

    const grant = await grantDraft(api, ALICE)
    await api.putObject(grant.uploadUrl, grant.headers, Buffer.from('hello'))
    await api.call(ALICE, 'post', `/admin/attachments/${grant.attachmentId}/complete`)

    const foreign = await api.envelope(
      BOB,
      'delete',
      `/admin/attachments/pending/${grant.attachmentId}`,
    )
    expectCode(foreign.body, 205, 'E4 非 owner')

    await api.call(ALICE, 'delete', `/admin/attachments/pending/${grant.attachmentId}`)
    const after = await api.call<{ id: number }[]>(
      ALICE,
      'get',
      `/admin/attachments/drafts/${grant.draftId}`,
    )
    expect(after).toEqual([])

    const attached = await api.envelope(
      ALICE,
      'delete',
      `/admin/attachments/pending/${demo.attachmentId}`,
    )
    expectCode(attached.body, 205, 'E4 已绑定流程的附件')
  })

  test('E5 processes：成员可读（canUpload）/ 非成员 205', async ({ request }) => {
    const api = new Api(request)
    const demo = await demoProcess(api)

    const bobView = await api.call<ProcessAttachmentList>(
      BOB,
      'get',
      `/admin/attachments/processes/${demo.processId}`,
    )
    expect(bobView.canUpload).toBe(true)
    expect(bobView.attachments.length).toBeGreaterThan(0)

    const carolView = await api.call<ProcessAttachmentList>(
      CAROL,
      'get',
      `/admin/attachments/processes/${demo.processId}`,
    )
    expect(carolView.canUpload).toBe(false)

    expect(await api.code(ADMIN, 'get', `/admin/attachments/processes/${demo.processId}`)).toBe(205)
    expect(
      await api.code(PLAIN_USER, 'get', `/admin/attachments/processes/${demo.processId}`),
    ).toBe(205)
  })

  test('E6 download-url：成员正例 / 非成员 205 / 附件不属于该 process 205', async ({ request }) => {
    const api = new Api(request)
    const demo = await demoProcess(api)

    const bobGrant = await api.call<{ downloadUrl: string }>(
      BOB,
      'post',
      `/admin/attachments/processes/${demo.processId}/${demo.attachmentId}/download-url`,
    )
    expect(bobGrant.downloadUrl).toContain('http')

    const carolGrant = await api.call<{ downloadUrl: string }>(
      CAROL,
      'post',
      `/admin/attachments/processes/${demo.processId}/${demo.attachmentId}/download-url`,
    )
    expect(carolGrant.downloadUrl).toContain('http')

    const nonMember = await api.envelope(
      ADMIN,
      'post',
      `/admin/attachments/processes/${demo.processId}/${demo.attachmentId}/download-url`,
    )
    expectCode(nonMember.body, 205, 'E6 非成员')

    const wrongProcess = await api.envelope(
      BOB,
      'post',
      `/admin/attachments/processes/${demo.endedProcessId}/${demo.attachmentId}/download-url`,
    )
    expectCode(wrongProcess.body, 205, 'E6 附件不属于该 process')
  })

  test('E 附：附件生命周期正例（登记→PUT→complete→删除）', async ({ request }) => {
    const api = new Api(request)
    await completeDraft(api, ALICE)
  })
})
