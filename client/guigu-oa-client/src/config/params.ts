import type {
  ApiRequestConfig,
  EntityId,
  ParamForm,
  ParamListItem,
  ParamRecord,
} from '@/types/domain'
import { requiredRule, type RequiredRule } from './form-rules'

/** 参数页的按钮点（ADR 0056/#252）：读只判页面授予，不设 :query。 */
export const PARAMS_PERMISSIONS = {
  add: 'system:config:add',
  edit: 'system:config:edit',
  remove: 'system:config:remove',
} as const

export const VALUE_TYPE_OPTIONS = [
  { value: 'string', label: '字符串' },
  { value: 'number', label: '数字' },
  { value: 'boolean', label: '布尔' },
] as const

export const DEFAULT_VALUE_TYPE = 'string'

export function valueTypeLabel(valueType: string | null | undefined): string {
  return VALUE_TYPE_OPTIONS.find((option) => option.value === valueType)?.label || ''
}

export function isBuiltin(record: Pick<ParamRecord, 'builtin'> | null | undefined): boolean {
  return record?.builtin === 1
}

export function presentListRecord(record: ParamRecord = {}): ParamListItem {
  const builtin = isBuiltin(record) ? 1 : 0
  return {
    id: record.id || '',
    configKey: record.configKey,
    configValue: record.configValue,
    configName: record.configName,
    valueType: record.valueType || DEFAULT_VALUE_TYPE,
    builtin,
    builtinLabel: builtin === 1 ? '内置' : '自定义',
    valueTypeLabel: valueTypeLabel(record.valueType),
    remark: record.remark,
    createTime: record.createTime,
  }
}

export function presentListPage(
  page: { records?: ParamRecord[]; total?: number; pages?: number } = {},
) {
  return {
    records: (page.records || []).map(presentListRecord),
    total: page.total || 0,
    pages: page.pages || 0,
  }
}

const SYS_CONFIG_API = '/admin/system/config'

export function listRequest(
  page: number,
  limit: number,
  searchObj: Record<string, string | number> = {},
): ApiRequestConfig {
  return {
    method: 'get',
    url: `${SYS_CONFIG_API}/${page}/${limit}`,
    params: searchObj,
  }
}

export function removeRequest(id: EntityId): ApiRequestConfig {
  return {
    method: 'delete',
    url: `${SYS_CONFIG_API}/remove/${id}`,
  }
}

export function refreshRequest(): ApiRequestConfig {
  return {
    method: 'post',
    url: `${SYS_CONFIG_API}/refresh`,
  }
}

function textOf(value: string | null | undefined): string {
  return value == null ? '' : value
}

/** 键/名称/备注与 `requiredRule` 的 trim 判空一致：落库前去掉首尾空白。 */
function trimmedTextOf(value: string | null | undefined): string {
  return textOf(value).trim()
}

/**
 * 写载荷：内置项只提交 id 与 value，键/名称/类型都由服务端保护；
 * 自定义项固定带名称、值、类型与备注，值类型缺省 string。
 */
export function writeRequest(form: ParamForm | ParamRecord): ApiRequestConfig {
  const configValue = textOf(form.configValue)
  if (form.id) {
    if (isBuiltin(form)) {
      return {
        method: 'put',
        url: `${SYS_CONFIG_API}/update`,
        data: { id: form.id, configValue },
      }
    }
    return {
      method: 'put',
      url: `${SYS_CONFIG_API}/update`,
      data: {
        id: form.id,
        configKey: trimmedTextOf(form.configKey),
        configName: trimmedTextOf(form.configName),
        configValue,
        valueType: form.valueType || DEFAULT_VALUE_TYPE,
        remark: trimmedTextOf(form.remark),
      },
    }
  }
  return {
    method: 'post',
    url: `${SYS_CONFIG_API}/save`,
    data: {
      configKey: trimmedTextOf(form.configKey),
      configName: trimmedTextOf(form.configName),
      configValue,
      valueType: form.valueType || DEFAULT_VALUE_TYPE,
      remark: trimmedTextOf(form.remark),
    },
  }
}

export function emptyForm(): ParamForm {
  return {
    id: '',
    configKey: '',
    configValue: '',
    configName: '',
    valueType: DEFAULT_VALUE_TYPE,
    builtin: 0,
    remark: '',
  }
}

export function presentForm(record: ParamRecord = {}): ParamForm {
  return {
    id: record.id || '',
    configKey: record.configKey || '',
    configValue: textOf(record.configValue),
    configName: record.configName || '',
    valueType: record.valueType || DEFAULT_VALUE_TYPE,
    builtin: isBuiltin(record) ? 1 : 0,
    remark: textOf(record.remark),
  }
}

/** 必填文案镜像服务端 SysConfigSaveRequest（ADR 0034/0038）：键与名称必填，值可为空。 */
export function formRules(): Record<'configKey' | 'configName', RequiredRule[]> {
  return {
    configKey: [requiredRule('参数键不能为空')],
    configName: [requiredRule('参数名称不能为空')],
  }
}
