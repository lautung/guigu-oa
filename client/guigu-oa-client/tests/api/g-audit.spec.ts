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
  type LoginLogRecord,
  type MenuRecord,
  type OperLogRecord,
  type PageResult,
} from './helpers/api'

/**
 * G 审计 / 操作日志（#214）与登录日志（#216）。
 *
 * `sysOperLog` / `sysLoginLog` 页授予才能读；写动作各要自己的按钮点。清空是物理全表删除，
 * 采集切面在方法返回后写入，所以操作日志的清空行会存活。
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

test.describe('G 登录日志', () => {
  test('G3 列表/过滤/删除/清空：页面授予开读、按钮点缺失 205、空列表 201', async ({ request }) => {
    const api = new Api(request)
    const role = await createRole(api, unique('api-loginlog-role'))
    const temp = await createUser(api, unique('api-loginlog-user'))
    try {
      const tree = await api.call<MenuRecord[]>(
        ADMIN,
        'get',
        `/admin/system/sysMenu/toAssign/${role.id}`,
      )
      const page = flattenMenus(tree).find((menu) => menu.path === '/system/log/loginLog')
      expect(page, '菜单树里应有登录日志页').toBeTruthy()

      await assignMenus(api, role.id, [(page as MenuRecord).id])
      await assignRoles(api, temp.id, [role.id])

      const info = await api.call<InfoPayload>(temp.username, 'get', '/admin/system/index/info')
      expect(info.menus.map((menu) => menu.path)).toEqual(
        expect.arrayContaining(['/system/log', '/system/log/loginLog']),
      )
      expect(info.permissions).not.toContain('system:loginLog:remove')
      expect(info.permissions).not.toContain('system:loginLog:clean')
      expect(info.permissions).not.toContain('system:loginLog:unlock')

      expect(await api.code(temp.username, 'get', '/admin/system/sysLoginLog/1/5')).toBe(200)
      expect(
        await api.code(temp.username, 'delete', '/admin/system/sysLoginLog/remove', [999999]),
      ).toBe(205)
      expect(await api.code(temp.username, 'delete', '/admin/system/sysLoginLog/clean')).toBe(205)
      expect(
        await api.code(temp.username, 'post', '/admin/system/sysLoginLog/unlock/api-unlock-denied'),
      ).toBe(205)
      expect(await api.code(PLAIN_USER, 'get', '/admin/system/sysLoginLog/1/5')).toBe(205)

      // 管理员自己的成功登录行就是一条稳定样本；username/status/ip/时间范围命中同一读。
      const pageResult = await api.call<PageResult<LoginLogRecord>>(
        ADMIN,
        'get',
        '/admin/system/sysLoginLog/1/50?username=admin&status=0',
      )
      expect(pageResult.total).toBeGreaterThan(0)
      const record = pageResult.records[0]
      expect(record.id).toBeGreaterThan(0)
      expect(record.username).toBe('admin')
      expect(record.status).toBe(0)
      expect(record.ip).toBeTruthy()

      const byIp = await api.call<PageResult<LoginLogRecord>>(
        ADMIN,
        'get',
        `/admin/system/sysLoginLog/1/20?ip=${encodeURIComponent(record.ip as string)}`,
      )
      expect(byIp.total).toBeGreaterThan(0)
      expect(byIp.records.every((row) => (row.ip ?? '').includes(record.ip as string))).toBe(true)

      const byRange = await api.call<PageResult<LoginLogRecord>>(
        ADMIN,
        'get',
        '/admin/system/sysLoginLog/1/20?createTimeBegin=2000-01-01%2000:00:00&createTimeEnd=2099-12-31%2023:59:59',
      )
      expect(byRange.total).toBeGreaterThan(0)

      await api.call(ADMIN, 'delete', '/admin/system/sysLoginLog/remove', [record.id])
      const afterRemove = await api.call<PageResult<LoginLogRecord>>(
        ADMIN,
        'get',
        '/admin/system/sysLoginLog/1/500?username=admin&status=0',
      )
      expect(afterRemove.records.some((row) => row.id === record.id)).toBe(false)

      const empty = await api.envelope(ADMIN, 'delete', '/admin/system/sysLoginLog/remove', [])
      expectCodeAndMessage(empty.body, 201, '删除列表不能为空', 'G3 空列表（ADR 0040）')

      await api.call(ADMIN, 'delete', '/admin/system/sysLoginLog/clean')
      const cleared = await api.call<PageResult<LoginLogRecord>>(
        ADMIN,
        'get',
        '/admin/system/sysLoginLog/1/5',
      )
      expect(cleared.total).toBe(0)
    } finally {
      await assignRoles(api, temp.id, []).catch(() => {})
      await deleteRole(api, role.id).catch(() => {})
      await deleteUser(api, temp.id).catch(() => {})
    }
  })

  test('G4 解锁：登录日志 status=3 与操作日志各一行', async ({ request }) => {
    const api = new Api(request)
    const username = unique('api-unlock')

    await api.call(ADMIN, 'post', `/admin/system/sysLoginLog/unlock/${username}`)

    const loginLogs = await api.call<PageResult<LoginLogRecord>>(
      ADMIN,
      'get',
      `/admin/system/sysLoginLog/1/50?username=${username}&status=3`,
    )
    expect(loginLogs.total).toBe(1)
    expect(loginLogs.records[0].username).toBe(username)
    expect(loginLogs.records[0].msg).toBe('手动解锁')

    const audits = await api.call<PageResult<OperLogRecord>>(
      ADMIN,
      'get',
      `/admin/system/sysOperLog/1/50?title=${encodeURIComponent('登录日志·解锁')}`,
    )
    expect(audits.total).toBeGreaterThan(0)
    expect(audits.records[0].title).toBe('登录日志·解锁')
    expect(audits.records[0].businessType).toBe(7)
    expect(audits.records[0].status).toBe(0)

    expect(await api.code(PLAIN_USER, 'post', `/admin/system/sysLoginLog/unlock/${username}`)).toBe(
      205,
    )
  })
})
