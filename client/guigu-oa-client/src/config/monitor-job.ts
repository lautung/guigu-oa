import { requiredRule, type RequiredRule } from '@/config/form-rules'
import { formatEpochMs } from '@/config/monitor-data'
import type {
  ApiRequestConfig,
  EntityId,
  MonitorJobLogRecord,
  MonitorJobRecord,
} from '@/types/domain'

/**
 * 定时任务页的投影（ADR 0060，#242）：任务行由启动登记产生，页面只有
 * cron / 启停 / 并发 / 备注编辑与手动执行，不提供新增与删除（删掉的行下次
 * 启动会被补回）。读不设点（页面授予覆盖读），动作只用 edit / run 两个按钮点。
 */

/** 定时任务页的按钮点（ADR 0052/0060）：启停并入 edit，任务日志读不设点。 */
export const MONITOR_JOB_PERMISSIONS = {
  edit: 'monitor:job:edit',
  run: 'monitor:job:run',
} as const

/** 执行记录抽屉的条数：与服务端缺省一致（ADR 0060 的保留口径也是每任务 20 条）。 */
export const DEFAULT_LOG_LIMIT = 20

const JOB_API = '/admin/monitor/jobs'

export function jobsRequest(): ApiRequestConfig {
  return { method: 'get', url: JOB_API }
}

export interface MonitorJobUpdatePayload {
  cron?: string
  enabled?: number
  concurrency?: number
  remark?: string
}

export function updateJobRequest(id: EntityId, payload: MonitorJobUpdatePayload): ApiRequestConfig {
  return {
    method: 'put',
    url: `${JOB_API}/${encodeURIComponent(String(id))}`,
    data: payload,
  }
}

export function runJobRequest(id: EntityId): ApiRequestConfig {
  return { method: 'post', url: `${JOB_API}/${encodeURIComponent(String(id))}/run` }
}

export function jobLogsRequest(id: EntityId, limit: number = DEFAULT_LOG_LIMIT): ApiRequestConfig {
  return {
    method: 'get',
    url: `${JOB_API}/${encodeURIComponent(String(id))}/logs`,
    params: { limit },
  }
}

/** 并发两档（ADR 0060）：0 禁止（默认，前一执行未完记「跳过」）/ 1 允许；无第三档。 */
export const CONCURRENCY_FORBID = 0
export const CONCURRENCY_ALLOW = 1

const TRIGGER_LABELS: Record<number, string> = { 1: '自动调度', 2: '手动执行' }
const STATUS_LABELS: Record<number, string> = { 1: '成功', 2: '失败', 3: '跳过' }
const STATUS_KINDS: Record<number, 'success' | 'danger' | 'info'> = {
  1: 'success',
  2: 'danger',
  3: 'info',
}

export type StatusKind = '' | 'success' | 'danger' | 'info'

function labelOf(labels: Record<number, string>, value: number | null | undefined): string {
  return typeof value === 'number' && Object.hasOwn(labels, value) ? labels[value] : '—'
}

/**
 * 任务时间：服务端 dev profile 把 Date 序列化成 `yyyy-MM-dd HH:mm:ss` 字符串，
 * 原样展示；epoch 毫秒兜底换算（与慢 SQL 时间戳同一读法）。
 */
export function formatJobTime(value: string | number | null | undefined): string {
  if (typeof value === 'string' && value.trim()) return value
  if (typeof value === 'number' && Number.isFinite(value)) return formatEpochMs(value)
  return '—'
}

/** 耗时：1 秒内直显毫秒，往上换算成秒（两位小数）。 */
export function formatDurationMs(value: number | null | undefined): string {
  if (typeof value !== 'number' || !Number.isFinite(value) || value < 0) return '—'
  return value < 1000 ? `${value} ms` : `${(value / 1000).toFixed(2)} 秒`
}

export interface MonitorJobRow {
  id: EntityId
  name: string
  beanName: string
  cron: string
  enabled: boolean
  /** 原值 0/1，供编辑表单回读。 */
  concurrency: number
  concurrencyLabel: string
  remark: string
  /** false = bean 已不存在（「异常」行）：禁用编辑、执行与再次启用。 */
  available: boolean
  lastStatus: number | null
  lastStatusLabel: string
  lastStatusKind: StatusKind
  lastStartedAtLabel: string
  lastMessage: string
}

/** 任务行投影：enabled/concurrency 0|1 收成布尔，缺失字段落占位符而不是 undefined。 */
export function presentJobRow(record: MonitorJobRecord | undefined): MonitorJobRow {
  const concurrency =
    record?.concurrency === CONCURRENCY_ALLOW ? CONCURRENCY_ALLOW : CONCURRENCY_FORBID
  const lastStatus = typeof record?.lastStatus === 'number' ? record.lastStatus : null
  return {
    id: record?.id ?? '',
    name: record?.name || '',
    beanName: record?.beanName || '',
    cron: record?.cron || '',
    enabled: record?.enabled === 1,
    concurrency,
    concurrencyLabel: concurrency === CONCURRENCY_ALLOW ? '允许' : '禁止',
    remark: record?.remark || '',
    available: record?.available !== false,
    lastStatus,
    lastStatusLabel: labelOf(STATUS_LABELS, lastStatus),
    lastStatusKind: lastStatus === null ? '' : (STATUS_KINDS[lastStatus] ?? ''),
    lastStartedAtLabel: formatJobTime(record?.lastStartedAt),
    lastMessage: record?.lastMessage || '',
  }
}

export interface MonitorJobLogRow {
  id: EntityId
  jobName: string
  triggerLabel: string
  statusLabel: string
  statusKind: StatusKind
  startedAtLabel: string
  durationLabel: string
  /** 完整信息（失败堆栈含换行），单元格折叠、展开行全文展示。 */
  message: string
  failed: boolean
}

/** 执行记录行投影：失败行带 failed 标记供展开行着色。 */
export function presentJobLogRow(record: MonitorJobLogRecord | undefined): MonitorJobLogRow {
  const status = typeof record?.status === 'number' ? record.status : null
  return {
    id: record?.id ?? '',
    jobName: record?.jobName || '',
    triggerLabel: labelOf(TRIGGER_LABELS, record?.triggerType),
    statusLabel: labelOf(STATUS_LABELS, status),
    statusKind: status === null ? '' : (STATUS_KINDS[status] ?? ''),
    startedAtLabel: formatJobTime(record?.startedAt),
    durationLabel: formatDurationMs(record?.durationMs),
    message: record?.message || '',
    failed: status === 2,
  }
}

export interface MonitorJobForm {
  cron: string
  enabled: boolean
  /** true = 允许并发。 */
  concurrency: boolean
  remark: string
}

/** 行 → 编辑弹窗表单。 */
export function jobFormOf(row: MonitorJobRow): MonitorJobForm {
  return {
    cron: row.cron,
    enabled: row.enabled,
    concurrency: row.concurrency === CONCURRENCY_ALLOW,
    remark: row.remark,
  }
}

/** 表单 → 更新载荷（服务端 null 不覆盖；这里整单提交，cron/备注 trim 掉意外空白）。 */
export function jobUpdatePayloadOf(form: MonitorJobForm): MonitorJobUpdatePayload {
  return {
    cron: form.cron.trim(),
    enabled: form.enabled ? 1 : 0,
    concurrency: form.concurrency ? CONCURRENCY_ALLOW : CONCURRENCY_FORBID,
    remark: form.remark.trim(),
  }
}

/** 弹窗本地必填：cron 只判非空，表达式合法性由服务端 CronExpression 权威判定（201 文案回显）。 */
export function formRules(): Record<string, RequiredRule[]> {
  return {
    cron: [requiredRule('cron 不能为空')],
  }
}

/** 常用 cron 提示（Spring 6 字段：秒 分 时 日 月 周）；仅提示不代填。 */
export const CRON_HINTS: readonly { expr: string; label: string }[] = [
  { expr: '0 * * * * ?', label: '每分钟' },
  { expr: '0 0/30 * * * ?', label: '每 30 分钟' },
  { expr: '0 0 * * * ?', label: '每小时' },
  { expr: '0 0 2 * * ?', label: '每天 02:00' },
  { expr: '0 0 2 1 * ?', label: '每月 1 日 02:00' },
  { expr: '0 0 2 ? * MON', label: '每周一 02:00' },
]
