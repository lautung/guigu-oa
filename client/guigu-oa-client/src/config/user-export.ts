import type { ApiRequestConfig, UserImportResult, UserImportResultView } from '@/types/domain'

const SYS_USER_API = '/admin/system/sysUser'

/** 导入业务上限与 xlsx 类型限制（#259）：与服务端 SysUserExcelService 同值。 */
export const IMPORT_MAX_BYTES = 2 * 1024 * 1024

export const IMPORT_ACCEPT = '.xlsx'

/** 模板下载文件名在客户端生成：二进制响应不暴露 Content-Disposition 头。 */
export const IMPORT_TEMPLATE_FILE_NAME = '用户导入模板.xlsx'

/** 初始密码是导入动作参数（#263）：必填、6–100 位，与服务端同一策略。 */
export const IMPORT_PASSWORD_MIN_LENGTH = 6

export const IMPORT_PASSWORD_MAX_LENGTH = 100

export const IMPORT_PASSWORD_REQUIRED_MESSAGE = '初始密码不能为空'

export const IMPORT_PASSWORD_LENGTH_MESSAGE = `初始密码长度必须在 ${IMPORT_PASSWORD_MIN_LENGTH} 到 ${IMPORT_PASSWORD_MAX_LENGTH} 位之间`

const IMPORT_EXCEL_EXTENSION = '.xlsx'

/** 用户页的导入导出按钮点（#259）：导出与导入各自独立，不复用编辑点。 */
export const SYS_USER_IO_PERMISSIONS = {
  export: 'system:user:export',
  import: 'system:user:import',
} as const

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

/** 导入模板下载：只有表头，列与导出完全一致。 */
export function importTemplateRequest(): ApiRequestConfig & { responseType: 'blob' } {
  return {
    method: 'get',
    url: `${SYS_USER_API}/importTemplate`,
    responseType: 'blob',
  }
}

/** multipart 字段固定为 `file` + `initialPassword`（与服务端 @RequestParam 同名）。 */
export function importRequest(file: File, initialPassword: string): ApiRequestConfig {
  const data = new FormData()
  data.append('file', file)
  data.append('initialPassword', initialPassword)
  return {
    method: 'post',
    url: `${SYS_USER_API}/import`,
    data,
  }
}

/**
 * 本地预检与服务端同文案；真正的判定仍在服务端（扩展名 + ZIP 魔数 + 2MB）。
 */
export function validateImportFile(file: File | null | undefined): string {
  if (!file) return '导入文件不能为空'
  if (file.size === 0) return '导入文件不能为空'
  if (file.size > IMPORT_MAX_BYTES) return '导入文件超过 2 MB 限制'
  if (!file.name.toLowerCase().endsWith(IMPORT_EXCEL_EXTENSION)) return '仅支持 xlsx 文件'
  return ''
}

/** 初始密码必填且 6–100 位；空白按缺失处理，与服务端 hasText 同语义。 */
export function validateImportPassword(initialPassword: string): string {
  if (!initialPassword.trim()) return IMPORT_PASSWORD_REQUIRED_MESSAGE
  const length = initialPassword.length
  if (length < IMPORT_PASSWORD_MIN_LENGTH || length > IMPORT_PASSWORD_MAX_LENGTH) {
    return IMPORT_PASSWORD_LENGTH_MESSAGE
  }
  return ''
}

/** 汇总投影：失败行保留服务端行号与原因，空字段归一成空串。 */
export function presentImportResult(data: UserImportResult = {}): UserImportResultView {
  const failures = (data.failures || []).map((failure) => ({
    rowNumber: Number(failure.rowNumber) || 0,
    username: failure.username || '',
    reason: failure.reason || '',
  }))
  return {
    total: Number(data.total) || 0,
    successCount: Number(data.successCount) || 0,
    failureCount: Number(data.failureCount) || failures.length,
    failures,
  }
}

/** 结果行文案：成功与失败并列，不把部分成功说成整体失败。 */
export function importResultSummary(result: UserImportResultView): string {
  return `成功 ${result.successCount} 条，失败 ${result.failureCount} 条`
}

/** 点击后浏览器仍要异步读取 blob URL；立即撤销可能取消尚未开始的下载。 */
export const DOWNLOAD_URL_RELEASE_DELAY = 1000

/** 已认证同源 blob 落到本地文件；临时 URL 延迟释放，给浏览器留出读取时间。 */
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
  setTimeout(() => urlApi.revokeObjectURL(url), DOWNLOAD_URL_RELEASE_DELAY)
}
