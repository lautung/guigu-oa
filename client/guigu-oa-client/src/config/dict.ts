import type {
  ApiRequestConfig,
  DictDataForm,
  DictDataListItem,
  DictDataRecord,
  DictTypeForm,
  DictTypeListItem,
  DictTypeRecord,
  EntityId,
} from '@/types/domain'
import { requiredRule, type RequiredRule } from './form-rules'

/**
 * 字典管理（#251，ADR 0062）：类型/数据两级请求形状、管理页投影与表单。
 *
 * 业务页面不读本文件里的中文枚举——标签一律走 `DictTag` / `useDict`（`@/dict/use-dict`），
 * 由服务端 `dict:<dictType>` 缓存供值。这里只有管理页自身的「正常/停用」两档状态标签。
 */

export const DICT_PERMISSIONS = {
  add: 'system:dict:add',
  edit: 'system:dict:edit',
  remove: 'system:dict:remove',
} as const

/** 首批四组字典的类型编码（V130 种子），业务页面按这些常量取标签。 */
export const DICT_TYPES = {
  noticeType: 'sys_notice_type',
  noticeStatus: 'sys_notice_status',
  jobStatus: 'sys_job_status',
  loginStatus: 'sys_login_status',
} as const

/** 字典管理页的状态两档（与 sys_post/部门同口径）：1 正常 / 0 停用。 */
export const DICT_STATUS_OPTIONS = [
  { value: 1, label: '正常' },
  { value: 0, label: '停用' },
] as const

export function dictStatusLabel(status: number | null | undefined): string {
  return DICT_STATUS_OPTIONS.find((option) => option.value === status)?.label || ''
}

export function presentTypeRecord(record: DictTypeRecord = {}): DictTypeListItem {
  return {
    id: record.id || '',
    dictName: record.dictName,
    dictType: record.dictType,
    status: record.status,
    statusLabel: dictStatusLabel(record.status),
    remark: record.remark,
    createTime: record.createTime,
  }
}

export function presentTypePage(
  page: { records?: DictTypeRecord[]; total?: number; pages?: number } = {},
) {
  return {
    records: (page.records || []).map(presentTypeRecord),
    total: page.total || 0,
    pages: page.pages || 0,
  }
}

export function presentDataRecord(record: DictDataRecord = {}): DictDataListItem {
  return {
    id: record.id || '',
    dictType: record.dictType,
    dictLabel: record.dictLabel,
    dictValue: record.dictValue,
    sortValue: record.sortValue,
    status: record.status,
    statusLabel: dictStatusLabel(record.status),
    remark: record.remark,
    createTime: record.createTime,
  }
}

export function presentDataPage(
  page: { records?: DictDataRecord[]; total?: number; pages?: number } = {},
) {
  return {
    records: (page.records || []).map(presentDataRecord),
    total: page.total || 0,
    pages: page.pages || 0,
  }
}

const SYS_DICT_API = '/admin/system/dict'

export function typeListRequest(
  page: number,
  limit: number,
  searchObj: Record<string, string | number> = {},
): ApiRequestConfig {
  return {
    method: 'get',
    url: `${SYS_DICT_API}/type/${page}/${limit}`,
    params: searchObj,
  }
}

export function typeRemoveRequest(id: EntityId): ApiRequestConfig {
  return { method: 'delete', url: `${SYS_DICT_API}/type/remove/${id}` }
}

export function dataListRequest(
  page: number,
  limit: number,
  searchObj: Record<string, string | number> = {},
): ApiRequestConfig {
  return {
    method: 'get',
    url: `${SYS_DICT_API}/data/${page}/${limit}`,
    params: searchObj,
  }
}

export function dataRemoveRequest(id: EntityId): ApiRequestConfig {
  return { method: 'delete', url: `${SYS_DICT_API}/data/remove/${id}` }
}

/** 标签读（登录即有）：`useDict` 批量取数用；`types` 逗号连接，服务端也接受重复参数。 */
export function batchDataRequest(types: readonly string[]): ApiRequestConfig {
  return {
    method: 'get',
    url: `${SYS_DICT_API}/data/batch`,
    params: { types: types.join(',') },
  }
}

export function refreshRequest(): ApiRequestConfig {
  return { method: 'post', url: `${SYS_DICT_API}/refresh` }
}

function textOf(value: string | null | undefined): string {
  return value == null ? '' : value
}

/** 写载荷：save 不带 id，update 带 id；状态缺省正常。 */
export function typeWriteRequest(form: DictTypeForm | DictTypeRecord): ApiRequestConfig {
  const data = {
    dictName: textOf(form.dictName),
    dictType: textOf(form.dictType),
    status: form.status ?? 1,
    remark: textOf(form.remark),
  }
  if (form.id) {
    return { method: 'put', url: `${SYS_DICT_API}/type/update`, data: { id: form.id, ...data } }
  }
  return { method: 'post', url: `${SYS_DICT_API}/type/save`, data }
}

export function dataWriteRequest(form: DictDataForm | DictDataRecord): ApiRequestConfig {
  const data = {
    dictType: textOf(form.dictType),
    dictLabel: textOf(form.dictLabel),
    dictValue: textOf(form.dictValue),
    sortValue: form.sortValue ?? 0,
    status: form.status ?? 1,
    remark: textOf(form.remark),
  }
  if (form.id) {
    return { method: 'put', url: `${SYS_DICT_API}/data/update`, data: { id: form.id, ...data } }
  }
  return { method: 'post', url: `${SYS_DICT_API}/data/save`, data }
}

export function emptyTypeForm(): DictTypeForm {
  return { id: '', dictName: '', dictType: '', status: 1, remark: '' }
}

export function presentTypeForm(record: DictTypeRecord = {}): DictTypeForm {
  return {
    id: record.id || '',
    dictName: record.dictName || '',
    dictType: record.dictType || '',
    status: record.status ?? 1,
    remark: textOf(record.remark),
  }
}

export function emptyDataForm(dictType = ''): DictDataForm {
  return { id: '', dictType, dictLabel: '', dictValue: '', sortValue: 0, status: 1, remark: '' }
}

export function presentDataForm(record: DictDataRecord = {}): DictDataForm {
  return {
    id: record.id || '',
    dictType: record.dictType || '',
    dictLabel: record.dictLabel || '',
    dictValue: record.dictValue || '',
    sortValue: record.sortValue ?? 0,
    status: record.status ?? 1,
    remark: textOf(record.remark),
  }
}

/** 必填文案镜像服务端 DTO（ADR 0034/0038）：类型与名称/标签/值必填。 */
export function typeFormRules(): Record<'dictName' | 'dictType', RequiredRule[]> {
  return {
    dictName: [requiredRule('字典名称不能为空')],
    dictType: [requiredRule('字典类型不能为空')],
  }
}

export function dataFormRules(): Record<'dictLabel' | 'dictValue', RequiredRule[]> {
  return {
    dictLabel: [requiredRule('字典标签不能为空')],
    dictValue: [requiredRule('字典值不能为空')],
  }
}
