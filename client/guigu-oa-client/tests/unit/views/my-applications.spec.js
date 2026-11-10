import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

const { messageMock, confirmMock } = vi.hoisted(() => ({
  messageMock: Object.assign(vi.fn(), {
    error: vi.fn(),
    success: vi.fn(),
    warning: vi.fn(),
    close: vi.fn(),
  }),
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

import MyApplicationsPage from '@/views/work/my-applications/index.vue'
import {
  ensureDomApis,
  installHttp,
  mountOptions,
  ok,
  paged,
  setupSession,
} from '../support/harness'

ensureDomApis()

const leaveTypes = [
  { id: 1, name: '年假', balanceRequired: 1, availableHours: 8 },
  { id: 2, name: '事假', balanceRequired: 0, availableHours: null },
]

const submittedLeave = {
  id: 501,
  leaveTypeId: 1,
  leaveTypeName: '年假',
  startDate: '2026-12-07',
  endDate: '2026-12-08',
  days: 2,
  hours: 16,
  reason: '回家',
  status: 1,
  submitTime: '2026-11-10 09:00:00',
  approval: {
    processId: 9001,
    status: 1,
    currentApprover: '张三',
    description: '等待张三审批',
  },
}

const rejectedLeave = {
  id: 502,
  leaveTypeId: 2,
  leaveTypeName: '事假',
  startDate: '2026-12-09',
  endDate: '2026-12-09',
  days: 1,
  hours: 8,
  reason: '办事',
  status: -1,
  finishTime: '2026-11-10 10:00:00',
}

const draftOvertime = {
  id: 601,
  overtimeDate: '2026-12-12',
  startTime: '18:00:00',
  endTime: '20:00:00',
  hours: 0,
  reason: '上线',
  status: 0,
}

let http
let leaveRecords
let overtimeRecords

function mountPage() {
  const { pinia } = setupSession()
  return mount(MyApplicationsPage, mountOptions(pinia))
}

function responder(config) {
  const url = config.url || ''
  if (/^\/admin\/attendance\/my\/leave\/\d+\/\d+$/.test(url)) return paged(leaveRecords)
  if (/^\/admin\/attendance\/my\/overtime\/\d+\/\d+$/.test(url)) return paged(overtimeRecords)
  if (url === '/admin/attendance/my/leave/types') return ok(leaveTypes)
  if (/^\/admin\/attendance\/my\/leave\/\d+$/.test(url)) {
    return ok(leaveRecords.find((record) => `/admin/attendance/my/leave/${record.id}` === url))
  }
  if (/^\/admin\/attendance\/my\/overtime\/\d+$/.test(url)) {
    return ok(
      overtimeRecords.find((record) => `/admin/attendance/my/overtime/${record.id}` === url),
    )
  }
  if (url === '/admin/attendance/my/leave/save') return ok({ id: 503, status: 0 })
  if (url === '/admin/attendance/my/leave/update') return ok({ id: 502, status: -1 })
  if (url === '/admin/attendance/my/leave/remove/502') return ok(null)
  if (url === '/admin/attendance/my/leave/502/submit') {
    return ok({ ...rejectedLeave, status: 1, submitTime: '2026-11-10 11:00:00' })
  }
  if (url === '/admin/attendance/my/overtime/save') return ok({ id: 602, status: 0 })
  if (url === '/admin/attendance/my/overtime/update') return ok({ id: 601, status: 0 })
  if (url === '/admin/attendance/my/overtime/remove/601') return ok(null)
  if (url === '/admin/attendance/my/overtime/601/submit') {
    return ok({ ...draftOvertime, status: 1, hours: 2 })
  }
  throw new Error(`Unexpected request ${config.method} ${url}`)
}

beforeEach(() => {
  leaveRecords = [submittedLeave, rejectedLeave]
  overtimeRecords = [draftOvertime]
  messageMock.mockReset()
  messageMock.error.mockReset()
  messageMock.success.mockReset()
  messageMock.warning.mockReset()
  confirmMock.mockReset()
  http = installHttp(responder)
})

afterEach(() => {
  http.restore()
  vi.clearAllMocks()
})

describe('WorkMyApplications', () => {
  it('loads both tabs with status labels and editability', async () => {
    const wrapper = mountPage()
    await flushPromises()

    expect(http.calls.map((call) => call.url)).toEqual(
      expect.arrayContaining([
        '/admin/attendance/my/leave/1/10',
        '/admin/attendance/my/overtime/1/10',
        '/admin/attendance/my/leave/types',
      ]),
    )
    expect(wrapper.vm.leaveRows).toHaveLength(2)
    expect(wrapper.vm.leaveRows[0]).toEqual(
      expect.objectContaining({
        id: 501,
        typeName: '年假',
        statusLabel: '审批中',
        editable: false,
        submittable: false,
        submitTimeText: '2026-11-10 09:00',
      }),
    )
    expect(wrapper.vm.leaveRows[1]).toEqual(
      expect.objectContaining({
        id: 502,
        statusLabel: '已拒绝',
        editable: true,
        submittable: true,
        finishTimeText: '2026-11-10 10:00',
      }),
    )
    expect(wrapper.vm.overtimeRows[0]).toEqual(
      expect.objectContaining({
        id: 601,
        durationText: '提交后计算',
        statusLabel: '草稿',
        editable: true,
      }),
    )
    expect(wrapper.vm.leaveTypeOptions.map((type) => type.name)).toEqual(['年假', '事假'])
    expect(wrapper.vm.leaveTypeOptionLabel(wrapper.vm.leaveTypeOptions[0])).toBe(
      '年假（可用 8 小时）',
    )
  })

  it('validates and creates a leave draft', async () => {
    const wrapper = mountPage()
    await flushPromises()

    wrapper.vm.openLeaveCreate()
    expect(wrapper.vm.leaveFormVisible).toBe(true)
    await wrapper.vm.saveLeave()
    expect(http.calls.some((call) => call.url === '/admin/attendance/my/leave/save')).toBe(false)
    expect(messageMock.warning).toHaveBeenCalledWith('假期类型不能为空')

    wrapper.vm.leaveForm = {
      id: '',
      leaveTypeId: 1,
      startDate: '2026-12-10',
      endDate: '2026-12-09',
      reason: '调休',
    }
    await wrapper.vm.saveLeave()
    expect(http.calls.some((call) => call.url === '/admin/attendance/my/leave/save')).toBe(false)
    expect(messageMock.warning).toHaveBeenCalledWith('开始日期不能晚于结束日期')

    wrapper.vm.leaveForm = {
      id: '',
      leaveTypeId: 1,
      startDate: '2026-12-09',
      endDate: '2026-12-10',
      reason: ' 调休 ',
    }
    await wrapper.vm.saveLeave()
    await flushPromises()
    const save = http.calls.find((call) => call.url === '/admin/attendance/my/leave/save')
    expect(JSON.parse(save.data)).toEqual({
      leaveTypeId: 1,
      startDate: '2026-12-09',
      endDate: '2026-12-10',
      reason: '调休',
    })
    expect(wrapper.vm.leaveFormVisible).toBe(false)
    expect(
      http.calls.filter((call) => call.url === '/admin/attendance/my/leave/1/10'),
    ).toHaveLength(2)
  })

  it('edits a rejected leave and resubmits with a fresh idempotency key', async () => {
    const wrapper = mountPage()
    await flushPromises()

    const rejectedRow = wrapper.vm.leaveRows[1]
    wrapper.vm.openLeaveEdit(rejectedRow)
    expect(wrapper.vm.leaveForm).toEqual({
      id: 502,
      leaveTypeId: 2,
      startDate: '2026-12-09',
      endDate: '2026-12-09',
      reason: '办事',
    })
    expect(wrapper.vm.leaveFormTitle).toBe('编辑请假')

    await wrapper.vm.submitLeaveRow(rejectedRow)
    await flushPromises()
    const submit = http.calls.find((call) => call.url === '/admin/attendance/my/leave/502/submit')
    const payload = JSON.parse(submit.data)
    expect(typeof payload.idempotencyKey).toBe('string')
    expect(payload.idempotencyKey.length).toBeGreaterThan(8)
    expect(payload.idempotencyKey.length).toBeLessThanOrEqual(64)
    expect(wrapper.vm.leaveRows[1]).toEqual(
      expect.objectContaining({ status: 1, statusLabel: '审批中', editable: false }),
    )
  })

  it('saves overtime drafts with server-computed duration hint and opens detail', async () => {
    const wrapper = mountPage()
    await flushPromises()

    wrapper.vm.openOvertimeCreate()
    wrapper.vm.overtimeForm = {
      id: '',
      overtimeDate: '2026-12-13',
      startTime: '18:00:00',
      endTime: '18:00:00',
      reason: '上线',
    }
    await wrapper.vm.saveOvertime()
    expect(http.calls.some((call) => call.url === '/admin/attendance/my/overtime/save')).toBe(false)
    expect(messageMock.warning).toHaveBeenCalledWith('结束时刻必须晚于开始时刻')

    wrapper.vm.overtimeForm = {
      id: '',
      overtimeDate: '2026-12-13',
      startTime: '18:00:00',
      endTime: '21:00:00',
      reason: '上线',
    }
    await wrapper.vm.saveOvertime()
    await flushPromises()
    const save = http.calls.find((call) => call.url === '/admin/attendance/my/overtime/save')
    expect(JSON.parse(save.data)).toEqual({
      overtimeDate: '2026-12-13',
      startTime: '18:00:00',
      endTime: '21:00:00',
      reason: '上线',
    })
    expect(wrapper.vm.overtimeFormVisible).toBe(false)

    await wrapper.vm.openDetail(wrapper.vm.overtimeRows[0])
    await flushPromises()
    expect(http.calls.some((call) => call.url === '/admin/attendance/my/overtime/601')).toBe(true)
    expect(wrapper.vm.detailVisible).toBe(true)
    expect(wrapper.vm.detailRow.id).toBe(601)
  })

  it('deletes an editable application after confirmation', async () => {
    confirmMock.mockResolvedValue('confirm')
    const wrapper = mountPage()
    await flushPromises()

    await wrapper.vm.removeLeaveRow(wrapper.vm.leaveRows[1])
    await flushPromises()
    expect(confirmMock).toHaveBeenCalled()
    expect(http.calls.some((call) => call.url === '/admin/attendance/my/leave/remove/502')).toBe(
      true,
    )
    expect(
      http.calls.filter((call) => call.url === '/admin/attendance/my/leave/1/10'),
    ).toHaveLength(2)
  })
})
