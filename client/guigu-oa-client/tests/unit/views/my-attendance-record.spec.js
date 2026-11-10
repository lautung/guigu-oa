import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

import { ensureDomApis, installHttp, mountOptions, ok, setupSession } from '../support/harness'

import MyAttendanceRecordPage from '@/views/work/my-attendance-record/index.vue'

ensureDomApis()

const month = {
  month: '2026-03',
  days: [
    {
      date: '2026-03-02',
      weekday: 1,
      dayType: 1,
      workday: true,
      status: 1,
      clockInTime: '09:00:00',
      clockOutTime: '18:00:00',
    },
    {
      date: '2026-03-03',
      weekday: 2,
      dayType: 1,
      workday: true,
      status: 2,
      clockInTime: '09:15:00',
      clockOutTime: '18:00:00',
    },
    {
      date: '2026-03-04',
      weekday: 3,
      dayType: 1,
      workday: true,
      status: 3,
      clockInTime: '09:00:00',
      clockOutTime: '17:40:00',
    },
    {
      date: '2026-03-05',
      weekday: 4,
      dayType: 1,
      workday: true,
      status: 4,
      clockInTime: '09:15:00',
      clockOutTime: '17:40:00',
    },
    {
      date: '2026-03-06',
      weekday: 5,
      dayType: 1,
      workday: true,
      status: 5,
      clockInTime: '09:15:00',
      clockOutTime: null,
    },
    {
      date: '2026-03-07',
      weekday: 6,
      dayType: 1,
      workday: true,
      status: 6,
      leaveTypeName: '年假',
      clockInTime: null,
      clockOutTime: null,
    },
    {
      date: '2026-03-08',
      weekday: 7,
      dayType: 2,
      workday: false,
      status: 7,
      clockInTime: null,
      clockOutTime: null,
    },
    {
      date: '2026-03-09',
      weekday: 1,
      dayType: 3,
      workday: false,
      status: 8,
      clockInTime: null,
      clockOutTime: null,
    },
    {
      date: '2026-03-10',
      weekday: 2,
      dayType: 4,
      workday: true,
      status: 9,
      clockInTime: null,
      clockOutTime: null,
    },
    {
      date: '2026-03-11',
      weekday: 3,
      dayType: 1,
      workday: true,
      pending: true,
      status: null,
      clockInTime: '09:00:00',
      clockOutTime: null,
    },
    {
      date: '2026-03-12',
      weekday: 4,
      dayType: 1,
      workday: true,
      pending: false,
      status: null,
      clockInTime: null,
      clockOutTime: null,
    },
  ],
}

let pinia
let http
beforeEach(() => {
  http = installHttp((config) => {
    if (config.url === '/admin/attendance/my/records') return ok(month)
    return ok(null)
  })
  ;({ pinia } = setupSession())
})

afterEach(() => {
  http.restore()
})

describe('办事考勤记录页', () => {
  it('renders the nine statuses plus 未打卡 and the empty future day', async () => {
    const wrapper = mount(MyAttendanceRecordPage, mountOptions(pinia))
    await flushPromises()

    expect(wrapper.text()).toContain('考勤记录')
    expect(wrapper.text()).toContain('2026-03')
    for (const label of [
      '正常',
      '迟到',
      '早退',
      '迟到且早退',
      '缺卡',
      '请假·年假',
      '休息日',
      '休假日',
      '补班日',
      '未打卡',
    ]) {
      expect(wrapper.text()).toContain(label)
    }
    expect(wrapper.vm.monthView.days).toHaveLength(11)
    expect(wrapper.vm.monthView.days[10].statusLabel).toBe('—')
  })

  it('loads the month picked in the picker', async () => {
    const wrapper = mount(MyAttendanceRecordPage, mountOptions(pinia))
    await flushPromises()

    wrapper.vm.loadMonth('2026-04')
    await flushPromises()

    const queries = http.calls
      .filter((call) => call.url === '/admin/attendance/my/records')
      .map((call) => call.params || {})
    expect(queries).toContainEqual({ month: '2026-04' })
  })
})
