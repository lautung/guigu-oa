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

const attendanceApi = vi.hoisted(() => ({
  saveOrUpdateShift: vi.fn(),
  removeShift: vi.fn(),
  saveOrUpdateGroup: vi.fn(),
  removeGroup: vi.fn(),
  saveOrUpdateHoliday: vi.fn(),
  removeHoliday: vi.fn(),
}))

vi.mock('@/api/attendance', () => attendanceApi)

import ElementPlus from 'element-plus'
import AttendanceGroupPage from '@/views/business/attendance/group/index.vue'
import AttendanceRecordPage from '@/views/business/attendance/record/index.vue'
import AttendanceRepairPage from '@/views/business/attendance/repair/index.vue'
import AttendanceLeavePage from '@/views/business/attendance/leave/index.vue'
import AttendanceStatPage from '@/views/business/attendance/stat/index.vue'
import { hasPermi } from '@/permission'
import { ensureDomApis, installHttp, ok, paged, setupSession } from '../support/harness'

ensureDomApis()

const shift = {
  id: 1,
  name: '标准班',
  startTime: '09:00:00',
  endTime: '18:00:00',
  lunchStart: '12:00:00',
  lunchEnd: '13:00:00',
  graceMinutes: 10,
  remark: '研发',
  createTime: '2026-11-10 09:00:00',
}

const group = {
  id: 5,
  name: '研发组',
  shiftId: 1,
  shiftName: '标准班',
  workdays: '1,2,3,4,5',
  memberCount: 2,
  remark: '',
  createTime: '2026-11-10 09:00:00',
}

const holiday = { id: 2, holidayDate: '2026-10-01', name: '国庆', dayType: 1 }

const punch = {
  id: 9,
  userId: 11,
  username: 't278-alice',
  name: '甲',
  deptId: 2,
  deptName: '研发部',
  punchDate: '2026-11-10',
  slot: 2,
  punchTime: '2026-11-10 18:30:00',
  source: 2,
  operatorName: '乙',
}

const members = [
  { id: 1, userId: 11, username: 't278-alice', name: '甲' },
  { id: 2, userId: 12, username: 't278-bob', name: '乙' },
]

const users = [
  { id: 11, username: 't278-alice', name: '甲' },
  { id: 12, username: 't278-bob', name: '乙' },
  { id: 13, username: 't278-carol', name: '停用丙' },
]

function responder(config) {
  const url = config.url || ''
  if (/\/attendance\/shift\/\d+\/\d+$/.test(url)) return paged([shift])
  if (/\/attendance\/shift\/options$/.test(url)) return ok([shift])
  if (/\/attendance\/group\/\d+\/\d+$/.test(url)) return paged([group])
  if (/\/attendance\/group\/userOptions$/.test(url)) return ok(users)
  if (/\/attendance\/group\/\d+\/members$/.test(url)) return ok(members)
  if (/\/attendance\/holiday\/\d+\/\d+$/.test(url)) return paged([holiday])
  if (/\/attendance\/punch\/\d+\/\d+$/.test(url)) return paged([punch])
  return ok(null)
}

function realOptions(pinia) {
  return {
    global: {
      plugins: [pinia, ElementPlus],
      directives: { hasPermi },
    },
  }
}

let http
beforeEach(() => {
  vi.clearAllMocks()
  messageMock.mockReset()
  confirmMock.mockReset().mockResolvedValue('confirm')
  attendanceApi.saveOrUpdateShift.mockReset().mockResolvedValue(ok(null))
  attendanceApi.removeShift.mockReset().mockResolvedValue(ok(null))
  attendanceApi.saveOrUpdateGroup.mockReset().mockResolvedValue(ok(null))
  attendanceApi.removeGroup.mockReset().mockResolvedValue(ok(null))
  attendanceApi.saveOrUpdateHoliday.mockReset().mockResolvedValue(ok(null))
  attendanceApi.removeHoliday.mockReset().mockResolvedValue(ok(null))
  http = installHttp(responder)
})

afterEach(() => http.restore())

describe('AttendanceGroupPage', () => {
  it('loads shift, group, and holiday blocks with joined shift name and workday labels', async () => {
    const { pinia } = setupSession()
    const wrapper = mount(AttendanceGroupPage, realOptions(pinia))
    await flushPromises()

    expect(wrapper.vm.shiftRows).toEqual([expect.objectContaining({ name: '标准班' })])
    expect(wrapper.vm.groupRows).toEqual([
      expect.objectContaining({ shiftName: '标准班', memberCount: 2 }),
    ])
    expect(wrapper.vm.holidayRows).toEqual([expect.objectContaining({ name: '国庆' })])
    expect(wrapper.findComponent(AttendanceGroupPage).exists()).toBe(true)

    const groupTableText = wrapper.findAll('.el-table')[1].text()
    expect(groupTableText).toContain('研发组')
    expect(groupTableText).toContain('周一、周二、周三、周四、周五')

    const urls = http.calls.map((call) => call.url)
    expect(urls).toContain('/admin/attendance/shift/1/10')
    expect(urls).toContain('/admin/attendance/shift/options')
    expect(urls).toContain('/admin/attendance/group/1/10')
    expect(urls).toContain('/admin/attendance/group/userOptions')
    expect(urls).toContain('/admin/attendance/holiday/1/10')
  })

  it('opens the shift dialog and blocks save on empty name or a half lunch pair', async () => {
    const { pinia } = setupSession()
    const wrapper = mount(AttendanceGroupPage, realOptions(pinia))
    await flushPromises()

    wrapper.vm.openShiftCreate()
    await flushPromises()
    expect(wrapper.vm.shiftDialogTitle).toBe('新建班次')
    expect(wrapper.vm.shiftForm).toEqual({
      id: '',
      name: '',
      startTime: '',
      endTime: '',
      lunchStart: '',
      lunchEnd: '',
      graceMinutes: 0,
      remark: '',
    })

    await wrapper.vm.saveShift()
    await new Promise((resolve) => setTimeout(resolve, 150))
    await flushPromises()
    expect(attendanceApi.saveOrUpdateShift).not.toHaveBeenCalled()
    expect(wrapper.vm.shiftDialogVisible).toBe(true)

    wrapper.vm.shiftForm = {
      id: '',
      name: '晚班',
      startTime: '14:00:00',
      endTime: '22:00:00',
      lunchStart: '18:00:00',
      lunchEnd: '',
      graceMinutes: 0,
      remark: '',
    }
    await flushPromises()
    await wrapper.vm.saveShift()
    expect(attendanceApi.saveOrUpdateShift).not.toHaveBeenCalled()
    expect(messageMock.warning).toHaveBeenCalledWith('午休开始与结束必须同时填写')
    expect(wrapper.vm.shiftDialogVisible).toBe(true)
  })

  it('saves a well-formed shift, closes the dialog, and refreshes the options', async () => {
    const { pinia } = setupSession()
    const wrapper = mount(AttendanceGroupPage, realOptions(pinia))
    await flushPromises()

    wrapper.vm.openShiftCreate()
    wrapper.vm.shiftForm = {
      id: '',
      name: '晚班',
      startTime: '14:00:00',
      endTime: '22:00:00',
      lunchStart: '',
      lunchEnd: '',
      graceMinutes: 5,
      remark: '',
    }
    await flushPromises()
    await wrapper.vm.saveShift()
    await flushPromises()
    expect(attendanceApi.saveOrUpdateShift).toHaveBeenCalledTimes(1)
    expect(wrapper.vm.shiftDialogVisible).toBe(false)
    const optionCalls = http.calls.filter((call) => call.url === '/admin/attendance/shift/options')
    expect(optionCalls.length).toBeGreaterThanOrEqual(2)
  })

  it('loads the current member ids into the group edit dialog', async () => {
    const { pinia } = setupSession()
    const wrapper = mount(AttendanceGroupPage, realOptions(pinia))
    await flushPromises()

    await wrapper.vm.openGroupEdit({ ...group })
    await flushPromises()
    expect(http.calls.at(-1).url).toBe('/admin/attendance/group/5/members')
    expect(wrapper.vm.groupForm).toEqual({
      id: 5,
      name: '研发组',
      shiftId: 1,
      workdays: [1, 2, 3, 4, 5],
      memberUserIds: [11, 12],
      remark: '',
    })
    expect(wrapper.vm.groupDialogTitle).toBe('编辑考勤组')

    wrapper.vm.groupForm = { ...wrapper.vm.groupForm, memberUserIds: [] }
    await flushPromises()
    await wrapper.vm.saveGroup()
    await flushPromises()
    expect(attendanceApi.saveOrUpdateGroup).toHaveBeenCalledTimes(1)
    expect(wrapper.vm.groupDialogVisible).toBe(false)
  })

  it('opens the holiday dialog and saves a holiday row', async () => {
    const { pinia } = setupSession()
    const wrapper = mount(AttendanceGroupPage, realOptions(pinia))
    await flushPromises()

    wrapper.vm.openHolidayCreate()
    expect(wrapper.vm.holidayDialogTitle).toBe('新建节假日')
    expect(wrapper.vm.holidayForm).toEqual({ id: '', holidayDate: '', name: '', dayType: 1 })
    wrapper.vm.holidayForm = { id: '', holidayDate: '2026-10-01', name: '国庆', dayType: 1 }
    await flushPromises()
    await wrapper.vm.saveHoliday()
    await flushPromises()
    expect(attendanceApi.saveOrUpdateHoliday).toHaveBeenCalledTimes(1)
    expect(wrapper.vm.holidayDialogVisible).toBe(false)

    wrapper.vm.openHolidayEdit(2)
    expect(wrapper.vm.holidayForm).toEqual({
      id: 2,
      holidayDate: '2026-10-01',
      name: '国庆',
      dayType: 1,
    })
  })
})

describe('考勤占位壳页', () => {
  const cases = [
    [AttendanceRepairPage, '补卡处理'],
    [AttendanceLeavePage, '假期类型与发放'],
    [AttendanceStatPage, '月度统计'],
  ]

  it.each(cases)('renders %s as an empty placeholder', async (component, title) => {
    const { pinia } = setupSession()
    const wrapper = mount(component, realOptions(pinia))
    await flushPromises()
    expect(wrapper.text()).toContain(title)
    expect(wrapper.find('.el-empty').exists()).toBe(true)
    expect(http.calls).toHaveLength(0)
  })
})

describe('AttendanceRecordPage', () => {
  it('loads punch rows with joined name, department, slot and source labels', async () => {
    const { pinia } = setupSession()
    const wrapper = mount(AttendanceRecordPage, realOptions(pinia))
    await flushPromises()

    expect(http.calls[0].url).toBe('/admin/attendance/punch/1/10')
    expect(wrapper.vm.rows).toEqual([
      expect.objectContaining({
        name: '甲',
        deptName: '研发部',
        slotLabel: '下班卡',
        sourceLabel: '补卡',
        operatorName: '乙',
      }),
    ])
    expect(wrapper.text()).toContain('研发部')
    expect(wrapper.text()).toContain('下班卡')
  })

  it('searches by person, date range and source, then resets', async () => {
    const { pinia } = setupSession()
    const wrapper = mount(AttendanceRecordPage, realOptions(pinia))
    await flushPromises()

    wrapper.vm.name = '甲'
    wrapper.vm.dateRange = ['2026-11-01', '2026-11-10']
    wrapper.vm.source = 1
    wrapper.vm.search()
    await flushPromises()

    const last = http.calls.at(-1)
    expect(last.url).toBe('/admin/attendance/punch/1/10')
    expect(last.params).toEqual({
      name: '甲',
      beginDate: '2026-11-01',
      endDate: '2026-11-10',
      source: 1,
    })

    wrapper.vm.reset()
    await flushPromises()
    expect(http.calls.at(-1).params).toEqual({})
  })
})
