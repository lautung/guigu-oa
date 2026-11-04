import { describe, expect, it, vi } from 'vitest'

import {
  downloadBlob,
  exportFileName,
  exportRequest,
  IMPORT_MAX_BYTES,
  IMPORT_TEMPLATE_FILE_NAME,
  importRequest,
  importResultSummary,
  importTemplateRequest,
  presentImportResult,
  SYS_USER_IO_PERMISSIONS,
  validateImportFile,
} from '@/config/user-export'

describe('config/user-export', () => {
  it('names the standalone export and import button points', () => {
    expect(SYS_USER_IO_PERMISSIONS.export).toBe('system:user:export')
    expect(SYS_USER_IO_PERMISSIONS.import).toBe('system:user:import')
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

  it('names the download with a stable local timestamp', () => {
    expect(exportFileName(new Date(2026, 10, 4, 9, 5, 7))).toBe('用户列表-20261104090507.xlsx')
  })

  it('downloads the empty import template through the blob read', () => {
    expect(importTemplateRequest()).toEqual({
      method: 'get',
      url: '/admin/system/sysUser/importTemplate',
      responseType: 'blob',
    })
    expect(IMPORT_TEMPLATE_FILE_NAME).toBe('用户导入模板.xlsx')
  })

  it('posts the selected file as the multipart field `file`', () => {
    const file = new File(['PK'], 'users.xlsx')
    const config = importRequest(file)

    expect(config.method).toBe('post')
    expect(config.url).toBe('/admin/system/sysUser/import')
    expect(config.data).toBeInstanceOf(FormData)
    expect(config.data.get('file')).toBe(file)
  })

  it('rejects empty, oversized and non-xlsx files locally with the server wording', () => {
    expect(validateImportFile(null)).toBe('导入文件不能为空')
    expect(validateImportFile(new File([], 'empty.xlsx'))).toBe('导入文件不能为空')
    expect(validateImportFile(new File([new Uint8Array(IMPORT_MAX_BYTES + 1)], 'big.xlsx'))).toBe(
      '导入文件超过 2 MB 限制',
    )
    expect(validateImportFile(new File(['x'], 'users.csv'))).toBe('仅支持 xlsx 文件')
    expect(validateImportFile(new File(['PK'], 'USERS.XLSX'))).toBe('')
  })

  it('projects the partial-success summary and every failure row', () => {
    const view = presentImportResult({
      total: 3,
      successCount: 2,
      failureCount: 1,
      failures: [{ rowNumber: 4, username: 'bob', reason: '用户名已存在' }],
    })

    expect(view).toEqual({
      total: 3,
      successCount: 2,
      failureCount: 1,
      failures: [{ rowNumber: 4, username: 'bob', reason: '用户名已存在' }],
    })
    expect(importResultSummary(view)).toBe('成功 2 条，失败 1 条')
  })

  it('normalizes an empty import result instead of throwing on the dialog', () => {
    expect(presentImportResult()).toEqual({
      total: 0,
      successCount: 0,
      failureCount: 0,
      failures: [],
    })
    expect(presentImportResult({ failures: [{ rowNumber: 3 }] }).failures).toEqual([
      { rowNumber: 3, username: '', reason: '' },
    ])
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
