import { describe, expect, it } from 'vitest'
import {
  BUSINESS_TYPE_OPTIONS,
  OPER_LOG_STATUS_OPTIONS,
  businessTypeLabel,
  cleanRequest,
  detailRequest,
  formatOperParam,
  listRequest,
  operLogStatusLabel,
  operLogStatusTagType,
  presentDetail,
  presentListPage,
  presentListRecord,
  presentQuery,
  removeRequest,
} from '@/config/oper-log'

describe('config/oper-log', () => {
  describe('labels', () => {
    it('maps every stable business type code to its label', () => {
      expect(BUSINESS_TYPE_OPTIONS).toEqual([
        { value: 0, label: '其他' },
        { value: 1, label: '新增' },
        { value: 2, label: '修改' },
        { value: 3, label: '删除' },
        { value: 4, label: '授权' },
        { value: 5, label: '发布' },
        { value: 6, label: '清空' },
        { value: 7, label: '解锁' },
      ])
      expect(businessTypeLabel(0)).toBe('其他')
      expect(businessTypeLabel(7)).toBe('解锁')
      expect(businessTypeLabel(99)).toBe('')
      expect(businessTypeLabel(undefined)).toBe('')
    })

    it('maps the two statuses to labels and tag types', () => {
      expect(OPER_LOG_STATUS_OPTIONS).toEqual([
        { value: 0, label: '成功' },
        { value: 1, label: '失败' },
      ])
      expect(operLogStatusLabel(0)).toBe('成功')
      expect(operLogStatusLabel(1)).toBe('失败')
      expect(operLogStatusTagType(0)).toBe('success')
      expect(operLogStatusTagType(1)).toBe('danger')
      expect(operLogStatusTagType(undefined)).toBe('info')
    })
  })

  describe('presentQuery', () => {
    it('omits empty fields but keeps zero-valued enum fields', () => {
      expect(presentQuery()).toEqual({})
      expect(
        presentQuery({ operName: '', title: '', businessType: '', status: '', createTimes: [] }),
      ).toEqual({})
      expect(presentQuery({ businessType: 0, status: 0 })).toEqual({
        businessType: 0,
        status: 0,
      })
    })

    it('maps the create-time range to begin/end parameters', () => {
      expect(
        presentQuery({
          operName: '张三',
          title: '用户管理',
          businessType: 1,
          status: 1,
          createTimes: ['2026-01-01 00:00:00', '2026-01-31 23:59:59'],
        }),
      ).toEqual({
        operName: '张三',
        title: '用户管理',
        businessType: 1,
        status: 1,
        createTimeBegin: '2026-01-01 00:00:00',
        createTimeEnd: '2026-01-31 23:59:59',
      })
    })

    it('ignores an incomplete time range', () => {
      expect(presentQuery({ createTimes: ['2026-01-01 00:00:00'] })).toEqual({})
      expect(presentQuery({ createTimes: ['', '2026-01-31 23:59:59'] })).toEqual({})
    })
  })

  describe('request builders', () => {
    it('pages 操作日志 with GET /admin/system/sysOperLog/{page}/{limit}', () => {
      expect(listRequest(2, 20, { status: 0 })).toEqual({
        method: 'get',
        url: '/admin/system/sysOperLog/2/20',
        params: { status: 0 },
      })
    })

    it('loads one 操作日志 with GET /admin/system/sysOperLog/get/{id}', () => {
      expect(detailRequest(9)).toEqual({
        method: 'get',
        url: '/admin/system/sysOperLog/get/9',
      })
    })

    it('removes selected 操作日志 with a DELETE body id list', () => {
      expect(removeRequest([3, 5])).toEqual({
        method: 'delete',
        url: '/admin/system/sysOperLog/remove',
        data: [3, 5],
      })
    })

    it('clears all 操作日志 with DELETE /admin/system/sysOperLog/clean', () => {
      expect(cleanRequest()).toEqual({
        method: 'delete',
        url: '/admin/system/sysOperLog/clean',
      })
    })
  })

  describe('presenters', () => {
    it('presents a list record with labels and cost text', () => {
      expect(
        presentListRecord({
          id: 3,
          title: '用户管理·新增用户',
          businessType: 1,
          operName: 'admin',
          deptName: '研发部',
          operIp: '127.0.0.1',
          status: 0,
          costTime: 12,
          createTime: '2026-01-01 10:00:00',
        }),
      ).toEqual({
        id: 3,
        title: '用户管理·新增用户',
        businessType: 1,
        businessTypeLabel: '新增',
        operName: 'admin',
        deptName: '研发部',
        operIp: '127.0.0.1',
        status: 0,
        statusLabel: '成功',
        statusTagType: 'success',
        costTime: 12,
        costTimeText: '12 ms',
        createTime: '2026-01-01 10:00:00',
      })
    })

    it('treats a missing payload as an empty page', () => {
      expect(presentListPage()).toEqual({ records: [], total: 0, pages: 0 })
    })

    it('pretty-prints JSON parameters and falls back to the raw text', () => {
      expect(formatOperParam('{"username":"alice","password":"***"}')).toBe(
        '{\n  "username": "alice",\n  "password": "***"\n}',
      )
      expect(formatOperParam('{"username":"alice"')).toBe('{"username":"alice"')
      expect(formatOperParam('')).toBe('')
      expect(formatOperParam(undefined)).toBe('')
    })

    it('presents the detail with formatted parameters and empty fallbacks', () => {
      const detail = presentDetail({
        id: 3,
        title: '用户管理·新增用户',
        businessType: 1,
        requestMethod: 'POST',
        operUrl: '/admin/system/sysUser/save',
        operIp: '127.0.0.1',
        status: 1,
        resultCode: 500,
        resultMessage: '操作失败',
        errorMsg: '数据库不可用',
        costTime: 25,
        operParam: '{"username":"alice"}',
        createTime: '2026-01-01 10:00:00',
      })

      expect(detail).toMatchObject({
        id: 3,
        title: '用户管理·新增用户',
        businessTypeLabel: '新增',
        requestMethod: 'POST',
        operUrl: '/admin/system/sysUser/save',
        operIp: '127.0.0.1',
        statusLabel: '失败',
        statusTagType: 'danger',
        resultCodeText: '500',
        resultMessage: '操作失败',
        errorMsg: '数据库不可用',
        costTimeText: '25 ms',
        operParamText: '{\n  "username": "alice"\n}',
        createTime: '2026-01-01 10:00:00',
      })
      expect(presentDetail()).toMatchObject({
        title: '',
        operName: '',
        deptName: '',
        requestMethod: '',
        operUrl: '',
        resultCodeText: '',
        resultMessage: '',
        errorMsg: '',
        costTimeText: '',
        operParamText: '',
        createTime: '',
      })
    })
  })
})
