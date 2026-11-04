import type { ApiRequestConfig } from '@/types/domain'

const SYS_USER_API = '/admin/system/sysUser'

/** 用户页的导入导出按钮点（#259）：导出与导入各自独立，不复用编辑点。 */
export const SYS_USER_IO_PERMISSIONS = {
  export: 'system:user:export',
} as const

/** 导出与导入模板共用的列（服务端唯一 owner 是 SysUserExcelService.COLUMNS）。 */
export const USER_EXCEL_COLUMNS = [
  '用户名',
  '姓名',
  '部门',
  '岗位',
  '手机',
  '状态',
  '创建时间',
] as const

/** 导出沿用列表当前筛选：空值不上送，服务端按「不过滤」处理。 */
export function exportRequest(
  searchObj: Record<string, string | number> = {},
): ApiRequestConfig & { responseType: 'blob' } {
  const params: Record<string, string | number> = {}
  for (const [key, value] of Object.entries(searchObj)) {
    if (value !== '' && value !== undefined && value !== null) params[key] = value
  }
  return {
    method: 'get',
    url: `${SYS_USER_API}/export`,
    params,
    responseType: 'blob',
  }
}

/** 下载文件名在客户端生成：二进制响应不暴露 Content-Disposition 头。 */
export function exportFileName(now: Date = new Date()): string {
  const pad = (value: number) => String(value).padStart(2, '0')
  const stamp =
    `${now.getFullYear()}${pad(now.getMonth() + 1)}${pad(now.getDate())}` +
    `${pad(now.getHours())}${pad(now.getMinutes())}${pad(now.getSeconds())}`
  return `用户列表-${stamp}.xlsx`
}

/** 已认证同源 blob 落到本地文件；临时 URL 在点击后释放。 */
export function downloadBlob(
  blob: Blob,
  fileName: string,
  doc: Document = document,
  urlApi: Pick<typeof URL, 'createObjectURL' | 'revokeObjectURL'> = URL,
): void {
  const url = urlApi.createObjectURL(blob)
  const link = doc.createElement('a')
  link.href = url
  link.download = fileName
  link.rel = 'noopener'
  doc.body.appendChild(link)
  link.click()
  link.remove()
  urlApi.revokeObjectURL(url)
}
