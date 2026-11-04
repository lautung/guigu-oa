import { describe, expect, it } from 'vitest'

import {
  DICT_PERMISSIONS,
  DICT_STATUS_OPTIONS,
  DICT_TYPES,
  batchDataRequest,
  dataFormRules,
  dataListRequest,
  dataRemoveRequest,
  dataWriteRequest,
  dictStatusLabel,
  emptyDataForm,
  emptyTypeForm,
  presentDataPage,
  presentDataRecord,
  presentTypeForm,
  presentTypePage,
  presentTypeRecord,
  refreshRequest,
  typeFormRules,
  typeListRequest,
  typeRemoveRequest,
  typeWriteRequest,
} from '@/config/dict'

describe('config/dict 管理页投影与请求', () => {
  it('names the page buttons and the four seeded dictionary types', () => {
    expect(DICT_PERMISSIONS).toEqual({
      add: 'system:dict:add',
      edit: 'system:dict:edit',
      remove: 'system:dict:remove',
    })
    expect(DICT_TYPES).toEqual({
      noticeType: 'sys_notice_type',
      noticeStatus: 'sys_notice_status',
      jobStatus: 'sys_job_status',
      loginStatus: 'sys_login_status',
    })
    expect(DICT_STATUS_OPTIONS).toEqual([
      { value: 1, label: '正常' },
      { value: 0, label: '停用' },
    ])
    expect(dictStatusLabel(1)).toBe('正常')
    expect(dictStatusLabel(0)).toBe('停用')
    expect(dictStatusLabel(undefined)).toBe('')
  })

  it('projects type rows and pages with status labels', () => {
    const row = presentTypeRecord({
      id: 4,
      dictName: '公告类型',
      dictType: 'sys_notice_type',
      status: 1,
      remark: 'r',
      createTime: '2026-11-04 10:00:00',
    })
    expect(row).toEqual({
      id: 4,
      dictName: '公告类型',
      dictType: 'sys_notice_type',
      status: 1,
      statusLabel: '正常',
      remark: 'r',
      createTime: '2026-11-04 10:00:00',
    })
    expect(presentTypePage()).toEqual({ records: [], total: 0, pages: 0 })
  })

  it('projects data rows and pages with status labels', () => {
    const row = presentDataRecord({
      id: 8,
      dictType: 'sys_notice_type',
      dictLabel: '通知',
      dictValue: '1',
      sortValue: 1,
      status: 0,
    })
    expect(row).toMatchObject({
      id: 8,
      dictType: 'sys_notice_type',
      dictLabel: '通知',
      dictValue: '1',
      sortValue: 1,
      status: 0,
      statusLabel: '停用',
    })
    expect(presentDataPage()).toEqual({ records: [], total: 0, pages: 0 })
  })

  it('builds the two-level list and remove request shapes', () => {
    expect(typeListRequest(1, 10, { dictType: 't' })).toEqual({
      method: 'get',
      url: '/admin/system/dict/type/1/10',
      params: { dictType: 't' },
    })
    expect(dataListRequest(2, 20, { dictType: 't' })).toEqual({
      method: 'get',
      url: '/admin/system/dict/data/2/20',
      params: { dictType: 't' },
    })
    expect(typeRemoveRequest(3)).toEqual({
      method: 'delete',
      url: '/admin/system/dict/type/remove/3',
    })
    expect(dataRemoveRequest(4)).toEqual({
      method: 'delete',
      url: '/admin/system/dict/data/remove/4',
    })
  })

  it('adds id only on update and fixes the status/sort defaults', () => {
    expect(typeWriteRequest({ ...emptyTypeForm(), dictName: 'N', dictType: 'c' })).toEqual({
      method: 'post',
      url: '/admin/system/dict/type/save',
      data: { dictName: 'N', dictType: 'c', status: 1, remark: '' },
    })
    expect(typeWriteRequest({ ...emptyTypeForm(), id: 9, dictName: 'N', dictType: 'c' })).toEqual({
      method: 'put',
      url: '/admin/system/dict/type/update',
      data: { id: 9, dictName: 'N', dictType: 'c', status: 1, remark: '' },
    })
    expect(dataWriteRequest({ ...emptyDataForm('t'), dictLabel: 'L', dictValue: '1' })).toEqual({
      method: 'post',
      url: '/admin/system/dict/data/save',
      data: {
        dictType: 't',
        dictLabel: 'L',
        dictValue: '1',
        sortValue: 0,
        status: 1,
        remark: '',
      },
    })
    expect(
      dataWriteRequest({ ...emptyDataForm('t'), id: 5, dictLabel: 'L', dictValue: '1' }),
    ).toMatchObject({ method: 'put', url: '/admin/system/dict/data/update' })
  })

  it('reads labels in one batch and refreshes through the edit button', () => {
    expect(batchDataRequest(['sys_notice_type', 'sys_login_status'])).toEqual({
      method: 'get',
      url: '/admin/system/dict/data/batch',
      params: { types: 'sys_notice_type,sys_login_status' },
    })
    expect(refreshRequest()).toEqual({ method: 'post', url: '/admin/system/dict/refresh' })
  })

  it('maps forms back and mirrors the required messages', () => {
    expect(emptyTypeForm()).toEqual({
      id: '',
      dictName: '',
      dictType: '',
      status: 1,
      remark: '',
    })
    expect(presentTypeForm({ id: 3, status: 0 })).toEqual({
      id: 3,
      dictName: '',
      dictType: '',
      status: 0,
      remark: '',
    })
    expect(presentTypeForm({ dictName: '公告', dictType: 't', remark: 'r' })).toMatchObject({
      dictName: '公告',
      dictType: 't',
      remark: 'r',
    })
    expect(Object.keys(typeFormRules())).toEqual(['dictName', 'dictType'])
    expect(Object.keys(dataFormRules())).toEqual(['dictLabel', 'dictValue'])
    expect(ruleMessage(typeFormRules().dictName[0])).toBe('字典名称不能为空')
    expect(ruleMessage(typeFormRules().dictType[0])).toBe('字典类型不能为空')
    expect(ruleMessage(dataFormRules().dictLabel[0])).toBe('字典标签不能为空')
    expect(ruleMessage(dataFormRules().dictValue[0])).toBe('字典值不能为空')
  })
})

function ruleMessage(rule) {
  let captured = ''
  rule.validator(rule, '', (error) => {
    captured = error?.message || ''
  })
  return captured
}
