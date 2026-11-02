import { describe, expect, it } from 'vitest'
import {
  LOGIN_LOG_PERMISSIONS,
  LOGIN_STATUS_OPTIONS,
  cleanRequest,
  listRequest,
  loginStatusLabel,
  loginStatusTagType,
  presentListPage,
  presentListRecord,
  presentQuery,
  removeRequest,
  unlockRequest,
} from '@/config/login-log'

describe('config/login-log', () => {
  describe('labels', () => {
    it('maps the four login statuses to labels and tag types', () => {
      expect(LOGIN_STATUS_OPTIONS).toEqual([
        { value: 0, label: '成功' },
        { value: 1, label: '失败' },
        { value: 2, label: '锁定' },
        { value: 3, label: '解锁' },
      ])
      expect(loginStatusLabel(0)).toBe('成功')
      expect(loginStatusLabel(1)).toBe('失败')
      expect(loginStatusLabel(2)).toBe('锁定')
      expect(loginStatusLabel(3)).toBe('解锁')
      expect(loginStatusLabel(undefined)).toBe('')
      expect(loginStatusTagType(0)).toBe('success')
      expect(loginStatusTagType(1)).toBe('danger')
      expect(loginStatusTagType(2)).toBe('warning')
      expect(loginStatusTagType(3)).toBe('info')
    })

    it('names the button permissions used by the page', () => {
      expect(LOGIN_LOG_PERMISSIONS).toEqual({
        remove: 'system:loginLog:remove',
        clean: 'system:loginLog:clean',
        unlock: 'system:loginLog:unlock',
      })
    })
  })

  describe('presentQuery', () => {
    it('omits empty fields but keeps status 0', () => {
      expect(presentQuery()).toEqual({})
      expect(presentQuery({ username: '', ip: '', status: '', createTimes: [] })).toEqual({})
      expect(presentQuery({ status: 0 })).toEqual({ status: 0 })
    })

    it('maps the create-time range to begin/end parameters', () => {
      expect(
        presentQuery({
          username: 'alice',
          ip: '127.0.0.1',
          status: 2,
          createTimes: ['2026-01-01 00:00:00', '2026-01-31 23:59:59'],
        }),
      ).toEqual({
        username: 'alice',
        ip: '127.0.0.1',
        status: 2,
        createTimeBegin: '2026-01-01 00:00:00',
        createTimeEnd: '2026-01-31 23:59:59',
      })
    })
  })

  describe('request builders', () => {
    it('pages 登录日志 with GET /admin/system/sysLoginLog/{page}/{limit}', () => {
      expect(listRequest(1, 10, { username: 'alice' })).toEqual({
        method: 'get',
        url: '/admin/system/sysLoginLog/1/10',
        params: { username: 'alice' },
      })
    })

    it('removes selected 登录日志 with a DELETE body id list', () => {
      expect(removeRequest([7, 8])).toEqual({
        method: 'delete',
        url: '/admin/system/sysLoginLog/remove',
        data: [7, 8],
      })
    })

    it('clears all 登录日志 with DELETE /admin/system/sysLoginLog/clean', () => {
      expect(cleanRequest()).toEqual({
        method: 'delete',
        url: '/admin/system/sysLoginLog/clean',
      })
    })

    it('unlocks one account with an encoded username', () => {
      expect(unlockRequest('alice')).toEqual({
        method: 'post',
        url: '/admin/system/sysLoginLog/unlock/alice',
      })
      expect(unlockRequest('a b/c')).toEqual({
        method: 'post',
        url: '/admin/system/sysLoginLog/unlock/a%20b%2Fc',
      })
    })
  })

  describe('presenters', () => {
    it('presents a row as the whole record', () => {
      expect(
        presentListRecord({
          id: 2,
          username: 'alice',
          ip: '127.0.0.1',
          browser: 'Chrome',
          os: 'macOS',
          status: 2,
          msg: '账号锁定',
          createTime: '2026-01-01 10:00:00',
        }),
      ).toEqual({
        id: 2,
        username: 'alice',
        ip: '127.0.0.1',
        browser: 'Chrome',
        os: 'macOS',
        status: 2,
        statusLabel: '锁定',
        statusTagType: 'warning',
        msg: '账号锁定',
        createTime: '2026-01-01 10:00:00',
      })
    })

    it('treats a missing payload as an empty page', () => {
      expect(presentListPage()).toEqual({ records: [], total: 0, pages: 0 })
    })
  })
})
