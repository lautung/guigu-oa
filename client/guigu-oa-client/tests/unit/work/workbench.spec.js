import { describe, expect, it } from 'vitest'

import { PATHS } from '@/router/paths'
import {
  WORKBENCH_QUICK_LINKS,
  WORKBENCH_RECENT_LIMIT,
  WORKBENCH_SKELETON_ROWS,
  formatWorkbenchDate,
  pendingHeadline,
  presentWorkbench,
  workbenchKpis,
  workbenchNotices,
  workbenchRecent,
  workbenchRequests,
  workbenchTotals,
  workbenchUnreadCount,
} from '@/work/workbench'

function record(index, overrides = {}) {
  return {
    id: `p-${index}`,
    taskId: `t-${index}`,
    title: `审批 ${index}`,
    name: '张三',
    processTypeName: '请假审批',
    processTemplateName: '请假申请',
    createTime: '2026-11-02 09:00:00',
    status: 1,
    formValues: JSON.stringify({ formShowData: { 请假原因: '家中有事' } }),
    ...overrides,
  }
}

describe('工作台日期与待处理文案', () => {
  it('formats the local calendar date with the Chinese weekday', () => {
    expect(formatWorkbenchDate(new Date(2026, 10, 2))).toBe('11 月 2 日 周一')
    expect(formatWorkbenchDate(new Date(2026, 10, 3))).toBe('11 月 3 日 周二')
  })

  it('labels the pending count honestly and never shows a negative number', () => {
    expect(pendingHeadline(0)).toBe('当前没有待处理事项')
    expect(pendingHeadline(3)).toBe('当前有 3 项待处理')
    expect(pendingHeadline(-2)).toBe('当前没有待处理事项')
    expect(pendingHeadline(undefined)).toBe('当前没有待处理事项')
  })
})

describe('工作台 KPI 投影', () => {
  it('takes the four totals from the existing list reads', () => {
    expect(
      workbenchTotals({
        pending: { total: 12 },
        processed: { total: 8 },
        started: { total: 5 },
        copied: { total: 3 },
      }),
    ).toEqual({ pending: 12, processed: 8, started: 5, copied: 3 })
    expect(workbenchTotals({})).toEqual({ pending: 0, processed: 0, started: 0, copied: 0 })
  })

  it('renders four KPIs with the pending card as the only primary emphasis', () => {
    const kpis = workbenchKpis({ pending: 12, processed: 8, started: 5, copied: 3 })

    expect(kpis.map((kpi) => [kpi.key, kpi.label, kpi.value, kpi.path, kpi.emphasis])).toEqual([
      ['pending', '待处理', 12, PATHS.WORK_PENDING, true],
      ['processed', '已处理', 8, PATHS.WORK_PROCESSED, false],
      ['started', '我发起的', 5, PATHS.WORK_STARTED, false],
      ['copied', '抄送我的', 3, PATHS.WORK_COPIED, false],
    ])
    expect(kpis[0].hint).toBe('等待我处理')
  })

  it('requests every total with the one-row list read and the recent rows with five', () => {
    const requests = workbenchRequests()

    expect(requests.total.pending).toMatchObject({
      method: 'get',
      url: '/admin/process/findPending/1/1',
    })
    expect(requests.total.processed.url).toBe('/admin/process/findProcessed/1/1')
    expect(requests.total.started.url).toBe('/admin/process/findStarted/1/1')
    expect(requests.total.copied.url).toBe('/admin/process/findCarbonCopied/1/1')
    expect(requests.recent).toMatchObject({
      method: 'get',
      url: `/admin/process/findPending/1/${WORKBENCH_RECENT_LIMIT}`,
    })
    expect(WORKBENCH_SKELETON_ROWS).toBe(8)
  })
})

describe('工作台近期审批与公告投影', () => {
  it('keeps the first five pending rows with their detail paths and status tags', () => {
    const rows = workbenchRecent({
      total: 6,
      records: [
        record(1, { status: 1 }),
        record(2, { status: 2 }),
        record(3, { status: -1 }),
        record(4),
        record(5),
        record(6),
      ],
    })

    expect(rows).toHaveLength(WORKBENCH_RECENT_LIMIT)
    expect(rows[0]).toMatchObject({
      title: '审批 1',
      processTypeName: '请假审批',
      name: '张三',
      statusLabel: '审批中',
      statusTagType: 'warning',
      createTime: '2026-11-02 09:00:00',
      detailPath: '/work/pending/p-1/t-1',
    })
    expect(rows[1].statusLabel).toBe('审批通过')
    expect(rows[1].statusTagType).toBe('success')
    expect(rows[2].statusLabel).toBe('审批拒绝')
    expect(rows[2].statusTagType).toBe('danger')
  })

  it('keeps the unread feed top five already presented by #219', () => {
    const notices = workbenchNotices(
      [1, 2, 3, 4, 5, 6].map((index) => ({
        id: index,
        title: `公告 ${index}`,
        type: index === 1 ? 2 : 1,
        typeLabel: index === 1 ? '公告' : '通知',
        publisherName: '管理员',
        publishTime: '2026-11-02 10:00:00',
        read: false,
      })),
    )

    expect(notices).toHaveLength(5)
    expect(notices[0]).toMatchObject({
      id: 1,
      title: '公告 1',
      typeLabel: '公告',
      publisherName: '管理员',
      publishTime: '2026-11-02 10:00:00',
      read: false,
    })
    expect(notices[1].typeLabel).toBe('通知')
  })

  it('normalizes an unread count from the wire', () => {
    expect(workbenchUnreadCount(4)).toBe(4)
    expect(workbenchUnreadCount(-1)).toBe(0)
    expect(workbenchUnreadCount(undefined)).toBe(0)
    expect(workbenchUnreadCount('3')).toBe(0)
  })
})

describe('工作台整体投影', () => {
  it('assembles the header text, KPIs, recent rows, notices and 2x2 quick links', () => {
    const view = presentWorkbench({
      totals: { pending: 2, processed: 8, started: 5, copied: 3 },
      recentPage: { total: 2, records: [record(1), record(2)] },
      notices: [
        {
          id: 1,
          title: '维护通知',
          type: 1,
          typeLabel: '通知',
          publisherName: '管理员',
          publishTime: '11-02',
          read: false,
        },
      ],
      unreadCount: 1,
    })

    expect(view.pendingHeadline).toBe('当前有 2 项待处理')
    expect(view.pendingTotal).toBe(2)
    expect(view.kpis.map((kpi) => kpi.value)).toEqual([2, 8, 5, 3])
    expect(view.recent.map((row) => row.title)).toEqual(['审批 1', '审批 2'])
    expect(view.notices.map((notice) => notice.title)).toEqual(['维护通知'])
    expect(view.unreadCount).toBe(1)
    expect(view.quickLinks).toEqual(WORKBENCH_QUICK_LINKS)
    expect(WORKBENCH_QUICK_LINKS.map((link) => link.path)).toEqual([
      PATHS.WORK_START,
      PATHS.WORK_PENDING,
      PATHS.WORK_STARTED,
      PATHS.NOTICE,
    ])
  })
})
