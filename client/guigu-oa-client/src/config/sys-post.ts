import type { ApiRequestConfig, EntityId, PostForm, PostListItem, PostRecord } from '@/types/domain'
import { requiredRule, type RequiredRule } from './form-rules'

/** 岗位状态两档（#248），顺序即下拉顺序。 */
export const POST_STATUS_OPTIONS = [
  { value: 1, label: '正常' },
  { value: 0, label: '停用' },
] as const

export function postStatusLabel(status: number | null | undefined): string {
  return POST_STATUS_OPTIONS.find((option) => option.value === status)?.label || ''
}

export function presentListRecord(record: PostRecord = {}): PostListItem {
  return {
    id: record.id || '',
    postCode: record.postCode,
    name: record.name,
    sortValue: record.sortValue,
    status: record.status,
    statusLabel: postStatusLabel(record.status),
    createTime: record.createTime,
  }
}

export function presentListPage(
  page: { records?: PostRecord[]; total?: number; pages?: number } = {},
) {
  return {
    records: (page.records || []).map(presentListRecord),
    total: page.total || 0,
    pages: page.pages || 0,
  }
}

const SYS_POST_API = '/admin/system/sysPost'

export function listRequest(
  page: number,
  limit: number,
  searchObj: Record<string, string | number> = {},
): ApiRequestConfig {
  return {
    method: 'get',
    url: `${SYS_POST_API}/${page}/${limit}`,
    params: searchObj,
  }
}

export function removeRequest(id: EntityId): ApiRequestConfig {
  return {
    method: 'delete',
    url: `${SYS_POST_API}/remove/${id}`,
  }
}

export function writeRequest(form: PostForm | PostRecord): ApiRequestConfig {
  const data: {
    id?: EntityId
    postCode?: string
    name?: string
    sortValue: number
    status: number
  } = {
    postCode: form.postCode,
    name: form.name,
    sortValue: form.sortValue == null ? 0 : form.sortValue,
    status: form.status == null ? 1 : form.status,
  }

  if (form.id) {
    data.id = form.id
    return {
      method: 'put',
      url: `${SYS_POST_API}/update`,
      data,
    }
  }

  return {
    method: 'post',
    url: `${SYS_POST_API}/save`,
    data,
  }
}

export function emptyForm(): PostForm {
  return {
    id: '',
    postCode: '',
    name: '',
    sortValue: 0,
    status: 1,
  }
}

export function presentForm(record: PostRecord = {}): PostForm {
  return {
    id: record.id || '',
    postCode: record.postCode || '',
    name: record.name || '',
    sortValue: record.sortValue == null ? 0 : record.sortValue,
    status: record.status == null ? 1 : record.status,
  }
}

/**
 * 必填与文案镜像服务端 SysPostSaveRequest（ADR 0034/0038）：
 * 岗位编码与名称始终必填；排序与状态有默认值，不参与本地必填。
 */
export function formRules(): Record<'postCode' | 'name', RequiredRule[]> {
  return {
    postCode: [requiredRule('岗位编码不能为空')],
    name: [requiredRule('岗位名称不能为空')],
  }
}
