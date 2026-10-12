import type { ApiRequestConfig, EntityId, ProcessTypeRecord } from '@/types/domain'

export interface ProcessTypeListItem extends ProcessTypeRecord {
  id: EntityId
}

function presentListItem(record: ProcessTypeRecord = {}): ProcessTypeListItem {
  return {
    id: record.id || '',
    name: record.name,
    description: record.description,
    createTime: record.createTime,
    updateTime: record.updateTime,
  }
}

export function presentListPage(
  page: { records?: ProcessTypeRecord[]; total?: number; pages?: number } = {},
) {
  return {
    records: (page.records || []).map(presentListItem),
    total: page.total || 0,
    pages: page.pages || 0,
  }
}

const PROCESS_TYPE_API = '/admin/process/processType'

export function listRequest(page: number, limit: number): ApiRequestConfig {
  return {
    method: 'get',
    url: `${PROCESS_TYPE_API}/${page}/${limit}`,
  }
}

export function getRequest(id: string | number): ApiRequestConfig {
  return {
    method: 'get',
    url: `${PROCESS_TYPE_API}/get/${id}`,
  }
}

export function removeRequest(id: string | number): ApiRequestConfig {
  return {
    method: 'delete',
    url: `${PROCESS_TYPE_API}/remove/${id}`,
  }
}

export function findAllRequest(): ApiRequestConfig {
  return {
    method: 'get',
    url: `${PROCESS_TYPE_API}/findAll`,
  }
}

export function writeRequest(form: ProcessTypeRecord): ApiRequestConfig {
  if (form.id) {
    return {
      method: 'put',
      url: `${PROCESS_TYPE_API}/update`,
      data: { id: form.id, name: form.name, description: form.description },
    }
  }

  return {
    method: 'post',
    url: `${PROCESS_TYPE_API}/save`,
    data: { name: form.name, description: form.description },
  }
}

export function emptyForm(): Required<Pick<ProcessTypeRecord, 'id' | 'name' | 'description'>> {
  return {
    id: '',
    name: '',
    description: '',
  }
}

export function presentForm(
  record: ProcessTypeRecord = {},
): Required<Pick<ProcessTypeRecord, 'id' | 'name' | 'description'>> {
  return {
    id: record.id || '',
    name: record.name || '',
    description: record.description || '',
  }
}
