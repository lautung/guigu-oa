import { describe, expect, it } from 'vitest'

import {
  MONITOR_ONLINE_PERMISSIONS,
  emptyQuery,
  forceLogoutRequest,
  listRequest,
  presentListRecord,
  presentQuery,
} from '@/config/monitor-online'

describe('在线用户查询投影', () => {
  it('sends username contains and the login time range as beginTime/endTime', () => {
    expect(
      presentQuery({
        username: 'ali',
        loginTimes: ['2026-01-01 08:00:00', '2026-01-02 23:59:59'],
      }),
    ).toEqual({
      username: 'ali',
      beginTime: '2026-01-01 08:00:00',
      endTime: '2026-01-02 23:59:59',
    })
  })

  it('omits empty fields and half-open time ranges', () => {
    expect(presentQuery({ username: '', loginTimes: [] })).toEqual({})
    expect(presentQuery({ username: 'ali', loginTimes: ['2026-01-01 08:00:00', ''] })).toEqual({
      username: 'ali',
    })
  })

  it('starts from an empty query', () => {
    expect(emptyQuery()).toEqual({ username: '', loginTimes: [] })
    expect(presentQuery(emptyQuery())).toEqual({})
  })
})

describe('在线用户行投影', () => {
  it('projects the session row with the server-side current flag', () => {
    expect(
      presentListRecord({
        sessionId: 's-1',
        username: 'admin',
        name: '管理员',
        loginTime: '2026-01-01 08:00:00',
        ip: '127.0.0.1',
        browser: 'Chrome',
        os: 'macOS',
        lastActive: '2026-01-01 09:00:00',
        current: true,
      }),
    ).toEqual({
      sessionId: 's-1',
      username: 'admin',
      name: '管理员',
      loginTime: '2026-01-01 08:00:00',
      ip: '127.0.0.1',
      browser: 'Chrome',
      os: 'macOS',
      lastActive: '2026-01-01 09:00:00',
      current: true,
    })
  })

  it('defaults missing fields and never marks a session current without the flag', () => {
    expect(presentListRecord({})).toEqual({
      sessionId: '',
      username: '',
      name: '',
      loginTime: '',
      ip: '',
      browser: '',
      os: '',
      lastActive: '',
      current: false,
    })
    expect(presentListRecord({ sessionId: 's-2', current: false }).current).toBe(false)
  })
})

describe('在线用户请求构造', () => {
  it('builds the paged list request with page/limit path params', () => {
    expect(listRequest(2, 10, { username: 'ali' })).toEqual({
      method: 'get',
      url: '/admin/monitor/online/2/10',
      params: { username: 'ali' },
    })
    expect(listRequest(1, 20)).toEqual({
      method: 'get',
      url: '/admin/monitor/online/1/20',
      params: {},
    })
  })

  it('builds the force logout request with the encoded session id', () => {
    expect(forceLogoutRequest('sess/1 id')).toEqual({
      method: 'post',
      url: '/admin/monitor/online/sess%2F1%20id/forceLogout',
    })
  })

  it('exposes the force logout button permission', () => {
    expect(MONITOR_ONLINE_PERMISSIONS.forceLogout).toBe('monitor:online:forceLogout')
  })
})
