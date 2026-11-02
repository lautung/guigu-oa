import { test, expect } from '@playwright/test'

import {
  ADMIN,
  Api,
  PLAIN_USER,
  assignMenus,
  assignRoles,
  createRole,
  createUser,
  deleteRole,
  deleteUser,
  expectCodeAndMessage,
  flattenMenus,
  unique,
  type InfoPayload,
  type MenuRecord,
  type OperLogRecord,
  type PageResult,
} from './helpers/api'

/**
 * G 审计 / 操作日志（#214）。
 *
 * `sysOperLog` 页授予才能读；删除/清空各要 `system:operLog:remove` / `system:operLog:clean`。
 * 清空是物理全表删除，清空行由采集切面在方法返回后写入，所以会存活。
 */

test.describe('G 操作日志', () => {
  test('G1 列表/详情/删除：页面授予开读、按钮点缺失 205、空列表 201', async ({ request }) => {
    const api = new Api(request)
    const role = await createRole(api, unique('api-log-role'))
    const temp = await createUser(api, unique('api-log-user'))
    try {
      const tree = await api.call<MenuRecord[]>(
        ADMIN,
        'get',
        `/admin/system/sysMenu/toAssign/${role.id}`,
      )
      const page = flattenMenus(tree).find((menu) => menu.path === '/system/log/operLog')
      expect(page, '菜单树里应有操作日志页').toBeTruthy()

      await assignMenus(api, role.id, [(page as MenuRecord).id])
      await assignRoles(api, temp.id, [role.id])

      const info = await api.call<InfoPayload>(temp.username, 'get', '/admin/system/index/info')
      expect(info.menus.map((menu) => menu.path)).toEqual(
        expect.arrayContaining(['/system/log', '/system/log/operLog']),
      )
      expect(info.permissions).not.toContain('system:operLog:remove')
      expect(info.permissions).not.toContain('system:operLog:clean')

      expect(await api.code(temp.username, 'get', '/admin/system/sysOperLog/1/5')).toBe(200)
      expect(
        await api.code(temp.username, 'delete', '/admin/system/sysOperLog/remove', [999999]),
      ).toBe(205)
      expect(await api.code(temp.username, 'delete', '/admin/system/sysOperLog/clean')).toBe(205)
      expect(await api.code(PLAIN_USER, 'get', '/admin/system/sysOperLog/1/5')).toBe(205)

      const pageResult = await api.call<PageResult<OperLogRecord>>(
        ADMIN,
        'get',
        '/admin/system/sysOperLog/1/5',
      )
      expect(pageResult.total).toBeGreaterThan(0)
      const record = pageResult.records[0]
      expect(record.id).toBeGreaterThan(0)
      expect(typeof record.title).toBe('string')

      const detail = await api.call<OperLogRecord>(
        ADMIN,
        'get',
        `/admin/system/sysOperLog/get/${record.id}`,
      )
      expect(detail.id).toBe(record.id)
      expect(detail.title).toBe(record.title)
      expect(detail).toHaveProperty('operParam')

      await api.call(ADMIN, 'delete', '/admin/system/sysOperLog/remove', [record.id])
      const gone = await api.call<OperLogRecord | null>(
        ADMIN,
        'get',
        `/admin/system/sysOperLog/get/${record.id}`,
      )
      expect(gone).toBeNull()

      const empty = await api.envelope(ADMIN, 'delete', '/admin/system/sysOperLog/remove', [])
      expectCodeAndMessage(empty.body, 201, '删除列表不能为空', 'G1 空列表（ADR 0040）')
    } finally {
      await assignRoles(api, temp.id, []).catch(() => {})
      await deleteRole(api, role.id).catch(() => {})
      await deleteUser(api, temp.id).catch(() => {})
    }
  })

  test('G2 clean：物理清空且清空行存活', async ({ request }) => {
    const api = new Api(request)

    await api.call(ADMIN, 'delete', '/admin/system/sysOperLog/clean')

    const page = await api.call<PageResult<OperLogRecord>>(
      ADMIN,
      'get',
      `/admin/system/sysOperLog/1/5?title=${encodeURIComponent('操作日志·清空')}`,
    )
    expect(page.total).toBe(1)
    expect(page.records[0].title).toBe('操作日志·清空')
    expect(page.records[0].businessType).toBe(6)
    expect(page.records[0].status).toBe(0)
  })
})
