import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    ElMessage: Object.assign(vi.fn(), { error: vi.fn(), success: vi.fn(), close: vi.fn() }),
  }
})

import ElementPlus from 'element-plus'
import { AxiosError } from 'axios'

import Workbench from '@/views/workbench/index.vue'
import { PATHS } from '@/router/paths'
import {
  createTestRouter,
  ensureDomApis,
  installHttp,
  ok,
  paged,
  setupSession,
} from '../support/harness'

ensureDomApis()

const pendingRecords = [1, 2, 3, 4, 5].map((index) => ({
  id: `p-${index}`,
  taskId: `t-${index}`,
  title: `审批 ${index}`,
  name: '张三',
  processTypeName: '请假审批',
  processTemplateName: '请假申请',
  createTime: '2026-11-02 09:00:00',
  status: index === 2 ? 2 : 1,
  formValues: JSON.stringify({ formShowData: { 请假原因: '家中有事' } }),
}))

const notices = [
  {
    id: 1,
    title: '系统维护通知',
    type: 1,
    publisherName: '管理员',
    publishTime: '2026-11-02 10:00:00',
    read: false,
  },
]

function workbenchRouter() {
  return createTestRouter([
    { path: '/', redirect: PATHS.WORKBENCH },
    { path: PATHS.WORKBENCH, component: Workbench },
    { path: PATHS.WORK_PENDING, component: { template: '<div />' } },
    { path: PATHS.WORK_PENDING_DETAIL, component: { template: '<div />' } },
    { path: PATHS.WORK_START, component: { template: '<div />' } },
    { path: PATHS.WORK_PROCESSED, component: { template: '<div />' } },
    { path: PATHS.WORK_STARTED, component: { template: '<div />' } },
    { path: PATHS.WORK_COPIED, component: { template: '<div />' } },
    { path: PATHS.NOTICE, component: { template: '<div />' } },
    { path: PATHS.NOTICE_DETAIL, component: { template: '<div />' } },
  ])
}

function respond(config, overrides = {}) {
  const url = config.url || ''
  if (url === '/admin/process/findPending/1/1') {
    return overrides.pendingTotal ?? ok({ records: [], total: 12, pages: 12 })
  }
  if (url === '/admin/process/findProcessed/1/1') return ok({ records: [], total: 8, pages: 8 })
  if (url === '/admin/process/findStarted/1/1') return ok({ records: [], total: 5, pages: 5 })
  if (url === '/admin/process/findCarbonCopied/1/1') return ok({ records: [], total: 3, pages: 3 })
  if (url === '/admin/process/findPending/1/5') {
    return overrides.pendingPage ?? paged(pendingRecords, 12)
  }
  if (url.endsWith('/admin/notices/unread')) return overrides.notices ?? ok(notices)
  if (url.endsWith('/admin/notices/unread-count')) return ok(overrides.unreadCount ?? 1)
  throw new Error(`Unexpected request ${url}`)
}

function mountWorkbench(responder) {
  const http = installHttp(responder)
  const { pinia } = setupSession()
  const router = workbenchRouter()
  const wrapper = mount(Workbench, {
    global: { plugins: [pinia, router, ElementPlus] },
  })
  return { wrapper, router, http }
}

let mounted

beforeEach(() => {
  vi.clearAllMocks()
})

afterEach(() => {
  mounted?.http.restore()
  mounted = undefined
})

describe('工作台页面', () => {
  it('renders the header, four KPI cards, recent approvals, notices and quick links', async () => {
    mounted = mountWorkbench((config) => respond(config))
    const { wrapper } = mounted
    await flushPromises()

    expect(wrapper.get('.workbench-title').text()).toBe('工作台')
    expect(wrapper.get('.workbench-date').text()).toMatch(/月\s+\d+\s+日\s+周[一二三四五六日]/)
    expect(wrapper.get('.pending-headline').text()).toBe('当前有 12 项待处理')

    const kpis = wrapper.findAll('.kpi-card')
    expect(kpis).toHaveLength(4)
    expect(kpis[0].classes()).toContain('is-primary')

    expect(wrapper.findAll('.el-table__row')).toHaveLength(5)
    expect(wrapper.text()).toContain('审批 1')
    expect(wrapper.text()).toContain('请假审批')
    expect(wrapper.text()).toContain('张三')

    expect(wrapper.findAll('.notice-item')).toHaveLength(1)
    expect(wrapper.get('.notice-item').text()).toContain('系统维护通知')
    expect(wrapper.get('.notice-item').text()).toContain('通知')

    expect(wrapper.findAll('.quick-link')).toHaveLength(4)
    expect(wrapper.get('.unread-count').text()).toBe('1 条未读')
  })

  it('opens the pending list, a detail row, a notice and a quick link', async () => {
    mounted = mountWorkbench((config) => respond(config))
    const { wrapper, router } = mounted
    await flushPromises()

    await wrapper.findAll('.kpi-card')[2].trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe(PATHS.WORK_STARTED)

    await wrapper.findAll('.el-table__row')[0].trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/work/pending/p-1/t-1')

    await wrapper.get('.notice-item').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/notice/1')

    const noticeQuick = wrapper
      .findAll('.quick-link')
      .find((link) => link.text().includes('通知公告'))
    await noticeQuick.trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe(PATHS.NOTICE)
  })

  it('shows the 8-row skeleton while the first read is in flight', async () => {
    let release
    const gate = new Promise((resolve) => {
      release = resolve
    })
    mounted = mountWorkbench(async (config) => {
      await gate
      return respond(config, {
        pendingTotal: ok({ records: [], total: 0, pages: 0 }),
        pendingPage: paged([], 0),
        notices: ok([]),
        unreadCount: 0,
      })
    })
    const { wrapper } = mounted
    await flushPromises()

    const skeleton = wrapper.get('.workbench-skeleton')
    expect(skeleton.attributes('data-rows')).toBe('8')
    expect(wrapper.findAll('.kpi-card')).toHaveLength(0)

    release()
    await flushPromises()
    expect(wrapper.find('.workbench-skeleton').exists()).toBe(false)
    expect(wrapper.findAll('.kpi-card')).toHaveLength(4)
  })

  it('shows el-empty with a start CTA when nothing is pending', async () => {
    mounted = mountWorkbench((config) =>
      respond(config, {
        pendingTotal: ok({ records: [], total: 0, pages: 0 }),
        pendingPage: paged([], 0),
        notices: ok([]),
        unreadCount: 0,
      }),
    )
    const { wrapper } = mounted
    await flushPromises()

    expect(wrapper.get('.pending-headline').text()).toBe('当前没有待处理事项')
    expect(wrapper.get('.workbench-empty').text()).toContain('当前没有待处理事项')
    const cta = wrapper.get('.workbench-empty .empty-start')
    expect(cta.text()).toBe('发起审批')
    expect(wrapper.findAll('.kpi-card')[0].get('.kpi-value').text()).toBe('0')
  })

  it('shows an error with retry and recovers', async () => {
    let failing = true
    mounted = mountWorkbench((config) => {
      if (failing && String(config.url).includes('/findPending/1/1')) {
        throw new AxiosError('工作台读取失败')
      }
      return respond(config)
    })
    const { wrapper } = mounted
    await flushPromises()

    expect(wrapper.get('.list-load-error').text()).toContain('工作台读取失败')
    expect(wrapper.find('.kpi-card').exists()).toBe(false)

    failing = false
    await wrapper.get('.list-load-error button').trigger('click')
    await flushPromises()

    expect(wrapper.find('.list-load-error').exists()).toBe(false)
    expect(wrapper.findAll('.kpi-card')).toHaveLength(4)
  })
})
