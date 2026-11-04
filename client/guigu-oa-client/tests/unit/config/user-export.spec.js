import { describe, expect, it, vi } from 'vitest'

import {
  downloadBlob,
  exportFileName,
  exportRequest,
  SYS_USER_IO_PERMISSIONS,
  USER_EXCEL_COLUMNS,
} from '@/config/user-export'

describe('config/user-export', () => {
  it('names the standalone export button point', () => {
    expect(SYS_USER_IO_PERMISSIONS.export).toBe('system:user:export')
  })

  it('builds the export request from the current filters as a binary read', () => {
    expect(exportRequest({ keyword: 'bob', deptId: 11, status: 0 })).toEqual({
      method: 'get',
      url: '/admin/system/sysUser/export',
      params: { keyword: 'bob', deptId: 11, status: 0 },
      responseType: 'blob',
    })
  })

  it('omits empty filters instead of sending blank query values', () => {
    expect(exportRequest({ keyword: '', deptId: '', status: '' }).params).toEqual({})
    expect(exportRequest().params).toEqual({})
  })

  it('keeps the seven exported columns in one place', () => {
    expect(USER_EXCEL_COLUMNS).toEqual([
      '用户名',
      '姓名',
      '部门',
      '岗位',
      '手机',
      '状态',
      '创建时间',
    ])
  })

  it('names the download with a stable local timestamp', () => {
    expect(exportFileName(new Date(2026, 10, 4, 9, 5, 7))).toBe('用户列表-20261104090507.xlsx')
  })

  it('downloads through an object URL and releases it', () => {
    const link = { href: '', download: '', rel: '', click: vi.fn(), remove: vi.fn() }
    const doc = {
      createElement: vi.fn(() => link),
      body: { appendChild: vi.fn() },
    }
    const urlApi = {
      createObjectURL: vi.fn(() => 'blob:export'),
      revokeObjectURL: vi.fn(),
    }
    const blob = new Blob(['xlsx'])

    downloadBlob(blob, '用户列表.xlsx', doc, urlApi)

    expect(urlApi.createObjectURL).toHaveBeenCalledWith(blob)
    expect(link.href).toBe('blob:export')
    expect(link.download).toBe('用户列表.xlsx')
    expect(link.click).toHaveBeenCalledTimes(1)
    expect(doc.body.appendChild).toHaveBeenCalledWith(link)
    expect(link.remove).toHaveBeenCalledTimes(1)
    expect(urlApi.revokeObjectURL).toHaveBeenCalledWith('blob:export')
  })
})
