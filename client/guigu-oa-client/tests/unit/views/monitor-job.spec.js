import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

const { messageMock, confirmMock } = vi.hoisted(() => ({
  messageMock: Object.assign(vi.fn(), { error: vi.fn(), success: vi.fn(), close: vi.fn() }),
  confirmMock: vi.fn(),
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    ElMessage: messageMock,
    ElMessageBox: { confirm: confirmMock, close: vi.fn() },
  }
})

const jobApi = vi.hoisted(() => ({
  updateJob: vi.fn(),
  runJob: vi.fn(),
}))

vi.mock('@/api/monitor', () => jobApi)

import ElementPlus from 'element-plus'
import { BusinessRequestFailure } from '@/request/request-failure'

import MonitorJobPage from '@/views/monitor/job/index.vue'
import { hasPermi } from '@/permission'
import { ensureDomApis, installHttp, ok, setupSession } from '../support/harness'

ensureDomApis()

const EDIT_PERMISSION = 'monitor:job:edit'
const RUN_PERMISSION = 'monitor:job:run'

const healthyJob = {
  id: 1,
  name: '演示：示例日志任务',
  beanName: 'demoLogMonitorJob',
  cron: '0 0/10 * * * ?',
  enabled: 0,
  concurrency: 0,
  remark: '演示用，默认停用',
  available: true,
  lastTriggerType: 2,
  lastStatus: 1,
  lastStartedAt: '2026-01-01 08:00:00',
  lastDurationMs: 45,
  lastMessage: 'ok',
}

const brokenJob = {
  id: 2,
  name: '孤儿任务',
  beanName: 'removedJobBean',
  cron: '0 0 3 * * ?',
  enabled: 1,
  concurrency: 0,
  remark: '',
  available: false,
}

let jobs = []
let responderOverride = null
let http

function responder(config, callIndex) {
  const url = config.url || ''
  if (responderOverride) return responderOverride(config, callIndex)
  if (url === '/admin/monitor/jobs') return ok(jobs)
  if (/^\/admin\/monitor\/jobs\/\d+\/logs$/.test(url)) return ok(logsOf(url))
  throw new Error(`Unexpected request ${url}`)
}

function logsOf(url) {
  const id = Number(url.match(/\/jobs\/(\d+)\/logs/)[1])
  return Array.from({ length: 20 }, (_unused, index) => ({
    id: 100 - index,
    jobId: id,
    jobName: '演示：示例日志任务',
    triggerType: index === 0 ? 2 : 1,
    status: index === 1 ? 2 : index === 2 ? 3 : 1,
    message:
      index === 1 ? 'java.lang.IllegalStateException: boom\n\tat Demo.execute(Demo.java:1)' : '',
    startedAt: '2026-01-01 08:00:00',
    durationMs: 12,
  }))
}

function realOptions(pinia) {
  return {
    global: {
      plugins: [pinia, ElementPlus],
      stubs: { DictTag: true },
      directives: { hasPermi },
    },
  }
}

function jobsRequests() {
  return http.calls.filter((call) => call.url === '/admin/monitor/jobs')
}

function rowOf(wrapper, text) {
  return wrapper.findAll('.el-table__row').find((node) => node.text().includes(text))
}

function buttonOf(row, text) {
  return row.findAll('button').find((node) => node.text() === text)
}

beforeEach(() => {
  vi.clearAllMocks()
  messageMock.mockReset()
  confirmMock.mockReset().mockResolvedValue('confirm')
  jobApi.updateJob.mockReset().mockResolvedValue(ok(null))
  jobApi.runJob.mockReset().mockResolvedValue(ok(null))
  jobs = [{ ...healthyJob }, { ...brokenJob }]
  responderOverride = null
  http = installHttp(responder)
})

afterEach(() => http.restore())

describe('定时任务页', () => {
  it('loads job rows and renders the columns with the last-run summary', async () => {
    const { pinia } = setupSession({ permissions: [EDIT_PERMISSION, RUN_PERMISSION] })
    const wrapper = mount(MonitorJobPage, realOptions(pinia))
    await flushPromises()

    expect(jobsRequests()).toHaveLength(1)
    const demoRow = rowOf(wrapper, 'demoLogMonitorJob')
    expect(demoRow.text()).toContain('演示：示例日志任务')
    expect(demoRow.text()).toContain('0 0/10 * * * ?')
    expect(demoRow.text()).toContain('禁止')
    expect(wrapper.vm.rows[0].lastStatus).toBe(1)
    expect(demoRow.text()).toContain('2026-01-01 08:00:00')
    expect(demoRow.find('.broken-tag').exists()).toBe(false)
  })

  it('marks available=false rows with 异常 and disables edit, run and re-enable', async () => {
    const { pinia } = setupSession({ permissions: [EDIT_PERMISSION, RUN_PERMISSION] })
    const wrapper = mount(MonitorJobPage, realOptions(pinia))
    await flushPromises()

    const brokenRow = rowOf(wrapper, 'removedJobBean')
    expect(brokenRow.find('.el-tag').text()).toBe('异常')
    expect(buttonOf(brokenRow, '编辑').attributes('disabled')).toBeDefined()
    expect(buttonOf(brokenRow, '执行一次').attributes('disabled')).toBeDefined()
    expect(brokenRow.find('.el-switch').classes()).toContain('is-disabled')

    const demoRow = rowOf(wrapper, 'demoLogMonitorJob')
    expect(demoRow.find('.broken-tag').exists()).toBe(false)
    expect(buttonOf(demoRow, '编辑').attributes('disabled')).toBeUndefined()
  })

  it('toggles the enabled switch through the edit payload and refreshes', async () => {
    const { pinia } = setupSession({ permissions: [EDIT_PERMISSION, RUN_PERMISSION] })
    const wrapper = mount(MonitorJobPage, realOptions(pinia))
    await flushPromises()

    await wrapper.vm.toggleEnabled({ ...healthyJob, enabled: true }, true)
    await flushPromises()

    expect(jobApi.updateJob).toHaveBeenCalledTimes(1)
    expect(jobApi.updateJob).toHaveBeenCalledWith(1, { enabled: 1 })
    expect(jobsRequests().length).toBeGreaterThanOrEqual(2)
    expect(messageMock).toHaveBeenCalledWith(
      expect.objectContaining({ type: 'success', message: expect.any(String) }),
    )
  })

  it('reverts the switch locally when the toggle fails', async () => {
    jobApi.updateJob.mockRejectedValueOnce(new Error('boom'))
    const { pinia } = setupSession({ permissions: [EDIT_PERMISSION, RUN_PERMISSION] })
    const wrapper = mount(MonitorJobPage, realOptions(pinia))
    await flushPromises()

    const row = wrapper.vm.rows[0]
    row.enabled = true
    await wrapper.vm.toggleEnabled(row, true)
    await flushPromises()

    expect(row.enabled).toBe(false)
    expect(jobsRequests()).toHaveLength(1)
  })

  it('saves the edit dialog with the whole payload and refreshes the list', async () => {
    const { pinia } = setupSession({ permissions: [EDIT_PERMISSION, RUN_PERMISSION] })
    const wrapper = mount(MonitorJobPage, realOptions(pinia))
    await flushPromises()

    await wrapper.vm.openEdit(wrapper.vm.rows[0])
    wrapper.vm.form.cron = '0 0 2 * * ?'
    wrapper.vm.form.enabled = true
    wrapper.vm.form.concurrency = true
    wrapper.vm.form.remark = '每天凌晨两点'
    await wrapper.vm.saveEdit()
    await flushPromises()

    expect(jobApi.updateJob).toHaveBeenCalledWith(1, {
      cron: '0 0 2 * * ?',
      enabled: 1,
      concurrency: 1,
      remark: '每天凌晨两点',
    })
    expect(jobsRequests().length).toBeGreaterThanOrEqual(2)
    expect(wrapper.vm.dialogVisible).toBe(false)
    expect(messageMock).toHaveBeenCalledWith(
      expect.objectContaining({ type: 'success', message: expect.any(String) }),
    )
  })

  it('keeps the dialog open with the server 201 message when cron is invalid', async () => {
    jobApi.updateJob.mockRejectedValueOnce(
      new BusinessRequestFailure('retryable', 'cron 表达式不合法', 201),
    )
    const { pinia } = setupSession({ permissions: [EDIT_PERMISSION, RUN_PERMISSION] })
    const wrapper = mount(MonitorJobPage, realOptions(pinia))
    await flushPromises()

    await wrapper.vm.openEdit(wrapper.vm.rows[0])
    await wrapper.vm.saveEdit()
    await flushPromises()

    expect(wrapper.vm.dialogVisible).toBe(true)
    expect(wrapper.vm.saveError).toBe('cron 表达式不合法')
    expect(jobsRequests()).toHaveLength(1)
  })

  it('blocks an empty cron in the dialog without sending the request', async () => {
    const { pinia } = setupSession({ permissions: [EDIT_PERMISSION, RUN_PERMISSION] })
    const wrapper = mount(MonitorJobPage, realOptions(pinia))
    await flushPromises()

    await wrapper.vm.openEdit(wrapper.vm.rows[0])
    wrapper.vm.form.cron = '   '
    await wrapper.vm.saveEdit()
    await flushPromises()

    expect(jobApi.updateJob).not.toHaveBeenCalled()
  })

  it('confirms before running once, toasts 已触发 and does not refresh yet', async () => {
    const { pinia } = setupSession({ permissions: [EDIT_PERMISSION, RUN_PERMISSION] })
    const wrapper = mount(MonitorJobPage, realOptions(pinia))
    await flushPromises()

    await wrapper.vm.runOnce(wrapper.vm.rows[0])
    await flushPromises()

    expect(confirmMock).toHaveBeenCalledTimes(1)
    expect(confirmMock.mock.calls[0][0]).toContain('演示：示例日志任务')
    expect(jobApi.runJob).toHaveBeenCalledWith(1)
    const success = messageMock.mock.calls.find(
      (call) => call[0] && typeof call[0] === 'object' && call[0].type === 'success',
    )
    expect(success[0].message).toBe('已触发')
    expect(jobsRequests()).toHaveLength(1)
  })

  it('does not run when the confirmation is cancelled', async () => {
    const { pinia } = setupSession({ permissions: [EDIT_PERMISSION, RUN_PERMISSION] })
    const wrapper = mount(MonitorJobPage, realOptions(pinia))
    await flushPromises()

    confirmMock.mockRejectedValueOnce(new Error('cancel'))
    await wrapper.vm.runOnce(wrapper.vm.rows[0])
    await flushPromises()

    expect(jobApi.runJob).not.toHaveBeenCalled()
  })

  it('loads the latest 20 log rows into the drawer with status and multiline info', async () => {
    const { pinia } = setupSession({ permissions: [EDIT_PERMISSION, RUN_PERMISSION] })
    const wrapper = mount(MonitorJobPage, realOptions(pinia))
    await flushPromises()

    await wrapper.vm.openLogs(wrapper.vm.rows[0])
    await flushPromises()

    expect(http.calls.filter((call) => call.url === '/admin/monitor/jobs/1/logs')).toHaveLength(1)
    expect(http.calls.at(-1).params).toEqual({ limit: 20 })
    expect(wrapper.vm.logRows).toHaveLength(20)
    expect(wrapper.vm.drawerVisible).toBe(true)
    expect(wrapper.vm.logRows[0].triggerLabel).toBe('手动执行')
    expect(wrapper.vm.logRows[1]).toMatchObject({
      statusKind: 'danger',
      failed: true,
    })
    expect(wrapper.vm.logRows[2].status).toBe(3)
    expect(wrapper.vm.logRows[1].message).toContain('\n\tat Demo.execute')
  })

  it('drops a late log window when another row opens its drawer faster', async () => {
    const { pinia } = setupSession({ permissions: [EDIT_PERMISSION, RUN_PERMISSION] })
    const wrapper = mount(MonitorJobPage, realOptions(pinia))
    await flushPromises()

    let release
    const stuck = new Promise((resolve) => (release = resolve))
    responderOverride = (config, callIndex) => {
      if (callIndex === 2) return stuck
      if (callIndex === 3) return ok([{ id: 9, jobId: 2, triggerType: 1, status: 1 }])
      return responder(config, callIndex)
    }

    const late = wrapper.vm.openLogs(wrapper.vm.rows[0])
    const latest = wrapper.vm.openLogs(wrapper.vm.rows[1])
    await latest
    await flushPromises()

    release(ok([]))
    await late
    await flushPromises()

    expect(wrapper.vm.logRows).toHaveLength(1)
    expect(wrapper.vm.logRows[0].id).toBe(9)
    expect(http.calls.at(-1).url).toBe('/admin/monitor/jobs/2/logs')
  })

  it('removes every action entry without the edit and run permissions', async () => {
    const { pinia } = setupSession({ permissions: [] })
    const wrapper = mount(MonitorJobPage, realOptions(pinia))
    await flushPromises()

    const texts = wrapper.findAll('.el-table__row button').map((node) => node.text())
    expect(texts).not.toContain('编辑')
    expect(texts).not.toContain('执行一次')
    expect(wrapper.findAll('.el-switch')).toHaveLength(0)
    // 执行记录是页面授予覆盖的读入口，不挂按钮点。
    expect(texts).toContain('执行记录')
  })

  it('shows the load error state with retry when the list read fails', async () => {
    responderOverride = () => {
      throw new Error('network down')
    }
    const { pinia } = setupSession({ permissions: [EDIT_PERMISSION, RUN_PERMISSION] })
    const wrapper = mount(MonitorJobPage, realOptions(pinia))
    await flushPromises()

    expect(wrapper.find('.list-load-error').exists()).toBe(true)

    responderOverride = null
    await wrapper.find('.list-load-error button').trigger('click')
    await flushPromises()
    expect(rowOf(wrapper, 'demoLogMonitorJob')).toBeDefined()
  })

  it('offers no create or remove entry anywhere on the page (ADR 0060)', async () => {
    const { pinia } = setupSession({ permissions: [EDIT_PERMISSION, RUN_PERMISSION] })
    const wrapper = mount(MonitorJobPage, realOptions(pinia))
    await flushPromises()

    const texts = wrapper.findAll('button').map((node) => node.text())
    expect(texts).not.toContain('新增')
    expect(texts).not.toContain('删除')
  })
})
