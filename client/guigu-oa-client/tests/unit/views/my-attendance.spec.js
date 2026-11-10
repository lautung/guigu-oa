import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

const { messageMock } = vi.hoisted(() => ({
  messageMock: Object.assign(vi.fn(), {
    error: vi.fn(),
    success: vi.fn(),
    warning: vi.fn(),
    close: vi.fn(),
  }),
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal()
  return { ...actual, ElMessage: messageMock }
})

const attendanceApi = vi.hoisted(() => ({
  fetchTodayAttendance: vi.fn(),
  punchAttendance: vi.fn(),
}))

vi.mock('@/api/attendance', () => attendanceApi)

import ElementPlus from 'element-plus'
import MyAttendancePage from '@/views/work/my-attendance/index.vue'
import { ensureDomApis, installHttp, ok, setupSession } from '../support/harness'

ensureDomApis()

const today = {
  date: '2026-11-10',
  weekday: 2,
  dayType: 1,
  workday: true,
  hasGroup: true,
  groupName: '研发组',
  shift: {
    id: 1,
    name: '标准班',
    startTime: '09:00:00',
    endTime: '18:00:00',
    lunchStart: '12:00:00',
    lunchEnd: '13:00:00',
    graceMinutes: 10,
  },
  clockInTime: null,
  clockOutTime: null,
}

function realOptions(pinia) {
  return { global: { plugins: [pinia, ElementPlus] } }
}

let pinia
let http
let todayResponse
beforeEach(() => {
  vi.clearAllMocks()
  messageMock.mockReset()
  messageMock.success.mockReset()
  todayResponse = today
  attendanceApi.punchAttendance.mockReset()
  http = installHttp((config) => {
    if (config.url === '/admin/attendance/my/today') return ok(todayResponse)
    return ok(null)
  })
  ;({ pinia } = setupSession())
})

afterEach(() => {
  http.restore()
  vi.clearAllMocks()
})

describe('办事打卡页', () => {
  it('loads today shift and two empty punch cards with two buttons', async () => {
    const wrapper = mount(MyAttendancePage, realOptions(pinia))
    await flushPromises()

    expect(http.calls.filter((call) => call.url === '/admin/attendance/my/today')).toHaveLength(1)
    const text = wrapper.text()
    expect(text).toContain('今日打卡')
    expect(text).toContain('标准班')
    expect(text).toContain('09:00 - 18:00')
    expect(text).toContain('上班打卡')
    expect(text).toContain('下班打卡')
    expect(wrapper.vm.canPunch).toBe(true)
    expect(wrapper.vm.today.clockInLabel).toBe('未打卡')
    expect(wrapper.vm.today.clockOutLabel).toBe('未打卡')
  })

  it('punches in and shows the returned server time on the card', async () => {
    attendanceApi.punchAttendance.mockResolvedValue(ok({ ...today, clockInTime: '08:30:00' }))
    const wrapper = mount(MyAttendancePage, realOptions(pinia))
    await flushPromises()

    await wrapper.vm.punch(1)
    await flushPromises()

    expect(attendanceApi.punchAttendance).toHaveBeenCalledTimes(1)
    expect(attendanceApi.punchAttendance).toHaveBeenCalledWith(1)
    expect(wrapper.vm.today.clockInTime).toBe('08:30')
    expect(wrapper.vm.today.clockInLabel).toBe('已打卡 08:30')
    expect(messageMock.success).toHaveBeenCalledWith('上班打卡成功')
    expect(http.calls.filter((call) => call.url === '/admin/attendance/my/today')).toHaveLength(1)
  })

  it('disables both buttons on a non-workday and explains why', async () => {
    todayResponse = { ...today, workday: false, dayType: 2 }
    const wrapper = mount(MyAttendancePage, realOptions(pinia))
    await flushPromises()

    expect(wrapper.vm.canPunch).toBe(false)
    expect(wrapper.text()).toContain('今日无排班，无需打卡')
    const buttons = wrapper.findAll('button')
    expect(buttons.some((button) => button.text() === '上班打卡')).toBe(true)
    expect(buttons.every((button) => button.attributes('disabled') !== undefined)).toBe(true)
  })

  it('shows the no-group empty state instead of punch buttons', async () => {
    todayResponse = {
      date: '2026-11-10',
      weekday: 2,
      dayType: 0,
      workday: false,
      hasGroup: false,
    }
    const wrapper = mount(MyAttendancePage, realOptions(pinia))
    await flushPromises()

    expect(wrapper.text()).toContain('未加入考勤组')
    expect(wrapper.text()).not.toContain('上班打卡')
  })
})
