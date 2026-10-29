import { test, expect, type APIRequestContext } from '@playwright/test'

import {
  ADMIN,
  Api,
  PLAIN_USER,
  createUnpublishedTemplate,
  getTemplate,
  processTypeId,
  publishTemplate,
  unique,
  expectCode,
  type PageResult,
  type TemplateRecord,
} from './helpers/api'
import { bpmnXml, buildZip, runId } from '../e2e-stack/helpers/stack'

/**
 * C 审批配置（#120 §6 C1–C14）。
 *
 * 权限契约：每个端点各自要 process / processTemplate / processType 页授予；`user` 全 205。
 * 已发布模板冻结（ADR 0005）：update / remove / 再发布都 201。
 *
 * 已发布模板无法删除，beforeAll 造一条 runId 命名的模板供「冻结」断言共用；
 * 这是 #120 §5 里唯一不清理的残留。
 */

let api: Api
let context: APIRequestContext
let publishedTemplateId: number

test.beforeAll(async ({ playwright }) => {
  context = await playwright.request.newContext()
  api = new Api(context)
  publishedTemplateId = await publishTemplate(api, unique('api-published'))
})

test.afterAll(async () => {
  await context.dispose()
})

test.describe('C 审批配置', () => {
  test('C1 审批列表：正例 / 无授予 205', async () => {
    const page = await api.call<PageResult<Record<string, unknown>>>(
      ADMIN,
      'get',
      '/admin/process/1/5',
    )
    expect(page.total).toBeGreaterThanOrEqual(0)
    expect(await api.code(PLAIN_USER, 'get', '/admin/process/1/5')).toBe(205)
  })

  test('C2 模板分页：含未发布 / 无授予 205', async () => {
    const unpublishedId = await createUnpublishedTemplate(api, unique('api-page-unpub'))
    try {
      const page = await api.call<PageResult<TemplateRecord>>(
        ADMIN,
        'get',
        '/admin/process/processTemplate/1/100',
      )
      const found = page.records.find((record) => record.id === unpublishedId)
      expect(found?.status).toBe(0)

      expect(await api.code(PLAIN_USER, 'get', '/admin/process/processTemplate/1/5')).toBe(205)
    } finally {
      await api.call(ADMIN, 'delete', `/admin/process/processTemplate/remove/${unpublishedId}`)
    }
  })

  test('C3 模板详情：正例 / 无授予 205 / 不存在 200 null', async () => {
    const detail = await api.call<TemplateRecord>(
      ADMIN,
      'get',
      `/admin/process/processTemplate/get/${publishedTemplateId}`,
    )
    expect(detail.status).toBe(1)

    expect(
      await api.code(
        PLAIN_USER,
        'get',
        `/admin/process/processTemplate/get/${publishedTemplateId}`,
      ),
    ).toBe(205)

    const missing = await api.call<TemplateRecord | null>(
      ADMIN,
      'get',
      '/admin/process/processTemplate/get/999999',
    )
    expect(missing).toBeNull()
  })

  test('C4 save：新建即未发布 / 无授予 205', async () => {
    const name = unique('api-template-save')
    const id = await createUnpublishedTemplate(api, name)
    try {
      const detail = await getTemplate(api, id)
      expect(detail.name).toBe(name)
      expect(detail.status).toBe(0)

      expect(
        await api.code(PLAIN_USER, 'post', '/admin/process/processTemplate/save', {
          name: unique('api-denied'),
          processTypeId: await processTypeId(api, '演示审批'),
        }),
      ).toBe(205)
    } finally {
      await api.call(ADMIN, 'delete', `/admin/process/processTemplate/remove/${id}`)
    }
  })

  test('C5 update：未发布正例 / 已发布 201', async () => {
    const id = await createUnpublishedTemplate(api, unique('api-template-update'))
    try {
      const template = await getTemplate(api, id)
      const renamed = `${template.name}改`
      await api.call(ADMIN, 'put', '/admin/process/processTemplate/update', {
        ...template,
        name: renamed,
      })
      expect((await getTemplate(api, id)).name).toBe(renamed)

      const published = await getTemplate(api, publishedTemplateId)
      const frozen = await api.envelope(ADMIN, 'put', '/admin/process/processTemplate/update', {
        ...published,
        name: `${published.name}改`,
      })
      expectCode(frozen.body, 201, 'C5 已发布模板更新')
    } finally {
      await api.call(ADMIN, 'delete', `/admin/process/processTemplate/remove/${id}`)
    }
  })

  test('C6 remove：未发布正例 / 已发布 201', async () => {
    const id = await createUnpublishedTemplate(api, unique('api-template-remove'))
    await api.call(ADMIN, 'delete', `/admin/process/processTemplate/remove/${id}`)
    expect(await getTemplate(api, id)).toBeNull()

    const frozen = await api.envelope(
      ADMIN,
      'delete',
      `/admin/process/processTemplate/remove/${publishedTemplateId}`,
    )
    expectCode(frozen.body, 201, 'C6 已发布模板删除')
  })

  test('C7 uploadProcessDefinition：zip 正例 / 非 zip 201 / 空文件 201', async () => {
    const processKey = `api${runId()}up`
    const zip = buildZip([{ name: 'process.bpmn20.xml', data: bpmnXml(processKey) }])
    const ok = await api.uploadProcessZip(ADMIN, { name: `${processKey}.zip`, buffer: zip })
    expectCode(ok.body, 200, 'C7 zip 正例')
    expect(ok.body.data.processDefinitionPath).toMatch(/^processes\/sha256\/[0-9a-f]{64}\.zip$/)
    expect(ok.body.data.processDefinitionKey).toBe(processKey)

    const notZip = await api.uploadProcessZip(ADMIN, {
      name: `${processKey}.txt`,
      buffer: zip,
    })
    expectCode(notZip.body, 201, 'C7 非 zip 后缀')

    const empty = await api.uploadProcessZip(ADMIN, {
      name: `${processKey}.zip`,
      buffer: Buffer.alloc(0),
    })
    expectCode(empty.body, 201, 'C7 空文件')
  })

  test('C8 publish：未发布正例 / 再发布 201', async () => {
    const id = await publishTemplate(api, unique('api-publish'))
    const published = await getTemplate(api, id)
    expect(published.status).toBe(1)
    expect(published.availableForInitiation).toBe(true)

    const again = await api.envelope(ADMIN, 'post', `/admin/process/processTemplate/publish/${id}`)
    expectCode(again.body, 201, 'C8 已发布再发布')
  })

  test('C9 processType findAll：正例 / 无授予 205', async () => {
    const types = await api.call<{ id: number; name: string }[]>(
      ADMIN,
      'get',
      '/admin/process/processType/findAll',
    )
    expect(types.length).toBeGreaterThan(0)
    expect(await api.code(PLAIN_USER, 'get', '/admin/process/processType/findAll')).toBe(205)
  })

  test('C10 processType 分页：正例 / 无授予 205', async () => {
    const page = await api.call<PageResult<{ id: number; name: string }>>(
      ADMIN,
      'get',
      '/admin/process/processType/1/5',
    )
    expect(page.total).toBeGreaterThan(0)
    expect(await api.code(PLAIN_USER, 'get', '/admin/process/processType/1/5')).toBe(205)
  })

  test('C11 processType get：正例 / 无授予 205 / 不存在 200 null', async () => {
    const typeId = await processTypeId(api, '演示审批')
    const detail = await api.call<{ id: number; name: string }>(
      ADMIN,
      'get',
      `/admin/process/processType/get/${typeId}`,
    )
    expect(detail.id).toBe(typeId)

    expect(await api.code(PLAIN_USER, 'get', `/admin/process/processType/get/${typeId}`)).toBe(205)

    const missing = await api.call<Record<string, unknown> | null>(
      ADMIN,
      'get',
      '/admin/process/processType/get/999999',
    )
    expect(missing).toBeNull()
  })

  test('C12 processType save：正例 / 无授予 205', async () => {
    const name = unique('api-type-save')
    await api.call(ADMIN, 'post', '/admin/process/processType/save', { name, description: '' })
    const types = await api.call<{ id: number; name: string }[]>(
      ADMIN,
      'get',
      '/admin/process/processType/findAll',
    )
    const created = types.find((type) => type.name === name)
    expect(created, 'C12 创建后 findAll 可见').toBeTruthy()
    try {
      expect(
        await api.code(PLAIN_USER, 'post', '/admin/process/processType/save', {
          name: unique('api-denied'),
        }),
      ).toBe(205)
    } finally {
      await api.call(
        ADMIN,
        'delete',
        `/admin/process/processType/remove/${(created as { id: number }).id}`,
      )
    }
  })

  test('C13 processType update：正例 / 无授予 205', async () => {
    const name = unique('api-type-update')
    await api.call(ADMIN, 'post', '/admin/process/processType/save', { name, description: '' })
    const types = await api.call<{ id: number; name: string }[]>(
      ADMIN,
      'get',
      '/admin/process/processType/findAll',
    )
    const created = types.find((type) => type.name === name) as { id: number; name: string }
    try {
      const renamed = `${name}改`
      await api.call(ADMIN, 'put', '/admin/process/processType/update', {
        id: created.id,
        name: renamed,
      })
      const detail = await api.call<{ id: number; name: string }>(
        ADMIN,
        'get',
        `/admin/process/processType/get/${created.id}`,
      )
      expect(detail.name).toBe(renamed)

      expect(
        await api.code(PLAIN_USER, 'put', '/admin/process/processType/update', {
          id: created.id,
          name: 'denied',
        }),
      ).toBe(205)
    } finally {
      await api.call(ADMIN, 'delete', `/admin/process/processType/remove/${created.id}`)
    }
  })

  test('C14 processType remove：逻辑删除后 get 返回 null', async () => {
    const name = unique('api-type-remove')
    await api.call(ADMIN, 'post', '/admin/process/processType/save', { name, description: '' })
    const types = await api.call<{ id: number; name: string }[]>(
      ADMIN,
      'get',
      '/admin/process/processType/findAll',
    )
    const created = types.find((type) => type.name === name) as { id: number; name: string }
    await api.call(ADMIN, 'delete', `/admin/process/processType/remove/${created.id}`)
    const detail = await api.call<Record<string, unknown> | null>(
      ADMIN,
      'get',
      `/admin/process/processType/get/${created.id}`,
    )
    expect(detail).toBeNull()
  })
})
