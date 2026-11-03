import { NOTICE_UNREAD_LIMIT } from '@/notice/config'
import { PATHS, WORK_LIST_KINDS, type WorkListKind } from '@/router/paths'
import { presentWorkList, workListRequest } from '@/work/work-list'
import type {
  ApiRequestConfig,
  NoticeFeedItem,
  PageResult,
  PresentedWorkListRecord,
  ProcessInstanceRecord,
} from '@/types/domain'

/**
 * 工作台（ADR 0059）的首屏投影：四张 KPI、近期待处理、未读公告与快捷入口。
 *
 * 数字只来自现有办事列表的 `total`（findPending/findProcessed/findStarted/
 * findCarbonCopied），不为工作台新增服务端统计。现有接口没有「今日已办/当天到期」的
 * 口径，所以卡面写「已处理」而不是「今日已办」，页头也只报当前待处理数——不编造日期聚合。
 */

export const WORKBENCH_RECENT_LIMIT = 5
/** 右栏与顶栏铃铛共用 #219 的未读上限，避免两处各写一个 5。 */
export const WORKBENCH_NOTICE_LIMIT = NOTICE_UNREAD_LIMIT
export const WORKBENCH_SKELETON_ROWS = 8

export interface WorkbenchTotals {
  pending: number
  processed: number
  started: number
  copied: number
}

export interface WorkbenchKpi {
  key: keyof WorkbenchTotals
  label: string
  value: number
  hint: string
  path: string
  emphasis: boolean
}

export interface WorkbenchQuickLink {
  key: 'start' | 'pending' | 'started' | 'notice'
  title: string
  icon: string
  path: string
}

const KPI_DEFINITIONS: readonly Omit<WorkbenchKpi, 'value'>[] = [
  {
    key: 'pending',
    label: '待处理',
    hint: '等待我处理',
    path: PATHS.WORK_PENDING,
    emphasis: true,
  },
  {
    key: 'processed',
    label: '已处理',
    hint: '我已处理',
    path: PATHS.WORK_PROCESSED,
    emphasis: false,
  },
  {
    key: 'started',
    label: '我发起的',
    hint: '我发起的审批',
    path: PATHS.WORK_STARTED,
    emphasis: false,
  },
  {
    key: 'copied',
    label: '抄送我的',
    hint: '抄送给我',
    path: PATHS.WORK_COPIED,
    emphasis: false,
  },
]

export const WORKBENCH_QUICK_LINKS: readonly WorkbenchQuickLink[] = [
  { key: 'start', title: '发起审批', icon: 'EditPen', path: PATHS.WORK_START },
  { key: 'pending', title: '待处理', icon: 'AlarmClock', path: PATHS.WORK_PENDING },
  { key: 'started', title: '已发起', icon: 'Promotion', path: PATHS.WORK_STARTED },
  { key: 'notice', title: '通知公告', icon: 'Bell', path: PATHS.NOTICE },
]

const WEEKDAYS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'] as const

/** 本地日历日期：`11 月 3 日 周二`。不做时区换算，用户看到的就是本机当天。 */
export function formatWorkbenchDate(date: Date): string {
  return `${date.getMonth() + 1} 月 ${date.getDate()} 日 ${WEEKDAYS[date.getDay()]}`
}

function countOf(value: number | null | undefined): number {
  return typeof value === 'number' && Number.isFinite(value) ? Math.max(0, Math.trunc(value)) : 0
}

/** 页头的待处理数只说「当前」，不声称当天到期或今日新增。 */
export function pendingHeadline(total: number | null | undefined): string {
  const count = countOf(total)
  return count > 0 ? `当前有 ${count} 项待处理` : '当前没有待处理事项'
}

export function workbenchTotals(
  pages: Partial<Record<WorkListKind, PageResult<ProcessInstanceRecord>>> = {},
): WorkbenchTotals {
  return {
    pending: countOf(pages[WORK_LIST_KINDS.PENDING]?.total),
    processed: countOf(pages[WORK_LIST_KINDS.PROCESSED]?.total),
    started: countOf(pages[WORK_LIST_KINDS.STARTED]?.total),
    copied: countOf(pages[WORK_LIST_KINDS.COPIED]?.total),
  }
}

export function workbenchKpis(totals: WorkbenchTotals): WorkbenchKpi[] {
  return KPI_DEFINITIONS.map((definition) => ({ ...definition, value: totals[definition.key] }))
}

export function workbenchRecent(
  page: PageResult<ProcessInstanceRecord> = {},
): PresentedWorkListRecord[] {
  return presentWorkList(WORK_LIST_KINDS.PENDING, page).records.slice(0, WORKBENCH_RECENT_LIMIT)
}

/** 入参来自 `src/notice/unread` 的读数源，类型/时间/读态已由 #219 投影。 */
export function workbenchNotices(items: readonly NoticeFeedItem[] = []): NoticeFeedItem[] {
  return items.slice(0, WORKBENCH_NOTICE_LIMIT)
}

export function workbenchUnreadCount(value: unknown): number {
  return typeof value === 'number' ? countOf(value) : 0
}

export interface WorkbenchRequests {
  total: Record<WorkListKind, ApiRequestConfig>
  recent: ApiRequestConfig
}

/** 四个 total 各读一页一条；近期审批再读待处理前五条。 */
export function workbenchRequests(): WorkbenchRequests {
  return {
    total: {
      [WORK_LIST_KINDS.PENDING]: workListRequest(WORK_LIST_KINDS.PENDING, 1, 1),
      [WORK_LIST_KINDS.PROCESSED]: workListRequest(WORK_LIST_KINDS.PROCESSED, 1, 1),
      [WORK_LIST_KINDS.STARTED]: workListRequest(WORK_LIST_KINDS.STARTED, 1, 1),
      [WORK_LIST_KINDS.COPIED]: workListRequest(WORK_LIST_KINDS.COPIED, 1, 1),
    },
    recent: workListRequest(WORK_LIST_KINDS.PENDING, 1, WORKBENCH_RECENT_LIMIT),
  }
}

export interface WorkbenchDataInput {
  totals: WorkbenchTotals
  recentPage?: PageResult<ProcessInstanceRecord>
  notices?: readonly NoticeFeedItem[]
  unreadCount?: unknown
}

export interface WorkbenchView {
  pendingHeadline: string
  pendingTotal: number
  kpis: WorkbenchKpi[]
  recent: PresentedWorkListRecord[]
  notices: NoticeFeedItem[]
  unreadCount: number
  quickLinks: readonly WorkbenchQuickLink[]
}

export function presentWorkbench(input: WorkbenchDataInput): WorkbenchView {
  const totals = input.totals

  return {
    pendingHeadline: pendingHeadline(totals.pending),
    pendingTotal: totals.pending,
    kpis: workbenchKpis(totals),
    recent: workbenchRecent(input.recentPage),
    notices: workbenchNotices(input.notices),
    unreadCount: workbenchUnreadCount(input.unreadCount),
    quickLinks: WORKBENCH_QUICK_LINKS,
  }
}
