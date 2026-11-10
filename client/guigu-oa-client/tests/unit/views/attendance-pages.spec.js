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
  fetchLeaveTypeOptions: vi.fn(),
  fetchLedgerUsers: vi.fn(),
  saveOrUpdateLeaveType: vi.fn(),
  removeLeaveType: vi.fn(),
  grantAttendanceLeave: vi.fn(),
}))

vi.mock('@/api/attendance', () => attendanceApi)

import ElementPlus from 'element-plus'
import AttendanceGroupPage from '@/views/business/attendance/group/index.vue'
import AttendanceRecordPage from '@/views/business/attendance/record/index.vue'
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

const leaveTypes = [
  {
    id: 1,
    name: '年假',
    balanceRequired: 1,
    overtimeCredit: 0,
    status: 1,
    sortNo: 1,
    remark: '',
  },
  {
    id: 2,
    name: '事假',
    balanceRequired: 0,
    overtimeCredit: 0,
    status: 1,
    sortNo: 2,
    remark: '',
  },
  {
    id: 3,
    name: '调休',
    balanceRequired: 1,
    overtimeCredit: 1,
    status: 0,
    sortNo: 3,
    remark: '',
  },
]

const ledger = {
  id: 21,
  userId: 11,
  username: 't280-alice',
  name: '甲',
  deptName: '研发部',
  leaveTypeId: 1,
  leaveTypeName: '年假',
  entryType: 1,
  changeHours: -2.25,
  operatorName: '乙',
  remark: '冲抵错发',
  createTime: '2026-11-10 09:00:00',
}

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
  if (/\/attendance\/leave-type\/\d+\/\d+$/.test(url)) return paged(leaveTypes)
  if (/\/attendance\/ledger\/\d+\/\d+$/.test(url)) return paged([ledger])
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
  attendanceApi.fetchLeaveTypeOptions.mockReset().mockResolvedValue(ok(leaveTypes))
  attendanceApi.fetchLedgerUsers.mockReset().mockResolvedValue(
    ok([
      { id: 11, username: 't280-alice', name: '甲', deptName: '研发部' },
      { id: 12, username: 't280-bob', name: '乙', deptName: '研发部' },
    ]),
  )
  attendanceApi.saveOrUpdateLeaveType.mockReset().mockResolvedValue(ok(null))
  attendanceApi.removeLeaveType.mockReset().mockResolvedValue(ok(null))
  attendanceApi.grantAttendanceLeave.mockReset().mockResolvedValue(ok(2))
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
  const cases = [[AttendanceStatPage, '月度统计']]

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

describe('AttendanceLeavePage', () => {
  it('loads type rows, type options and ledger rows with joined labels', async () => {
    const { pinia } = setupSession()
    const wrapper = mount(AttendanceLeavePage, realOptions(pinia))
    await flushPromises()

    expect(wrapper.vm.typeRows).toEqual([
      expect.objectContaining({ name: '年假', balanceRequired: 1, overtimeCredit: 0 }),
      expect.objectContaining({ name: '事假', balanceRequired: 0 }),
      expect.objectContaining({ name: '调休', overtimeCredit: 1, status: 0 }),
    ])
    expect(wrapper.vm.typeOptions).toHaveLength(3)
    // 发放下拉只留启用且需要余额的类型。
    expect(wrapper.vm.grantableOptions.map((type) => type.name)).toEqual(['年假'])
    expect(wrapper.vm.ledgerRows).toEqual([
      expect.objectContaining({
        name: '甲',
        username: 't280-alice',
        deptName: '研发部',
        leaveTypeName: '年假',
        entryTypeLabel: '发放',
        changeHoursText: '-2.25',
        negative: true,
        operatorName: '乙',
      }),
    ])
    expect(wrapper.text()).toContain('发放流水')
    expect(wrapper.text()).toContain('冲抵错发')

    const urls = http.calls.map((call) => call.url)
    expect(urls).toContain('/admin/attendance/leave-type/1/10')
    expect(urls).toContain('/admin/attendance/ledger/1/10')
  })

  it('creates a type, blocks an empty name, and saves disable edits as full-form updates', async () => {
    const { pinia } = setupSession()
    const wrapper = mount(AttendanceLeavePage, realOptions(pinia))
    await flushPromises()

    wrapper.vm.openTypeCreate()
    await flushPromises()
    expect(wrapper.vm.typeDialogTitle).toBe('新建假期类型')
    await wrapper.vm.saveType()
    await new Promise((resolve) => setTimeout(resolve, 150))
    await flushPromises()
    expect(attendanceApi.saveOrUpdateLeaveType).not.toHaveBeenCalled()

    wrapper.vm.typeForm = {
      ...wrapper.vm.typeForm,
      name: '育儿假',
      balanceRequired: 0,
      sortNo: 9,
    }
    await flushPromises()
    await wrapper.vm.saveType()
    await flushPromises()
    expect(attendanceApi.saveOrUpdateLeaveType).toHaveBeenCalledTimes(1)
    expect(attendanceApi.saveOrUpdateLeaveType.mock.calls[0][0]).toEqual({
      id: '',
      name: '育儿假',
      balanceRequired: 0,
      overtimeCredit: 0,
      status: 1,
      sortNo: 9,
      remark: '',
    })
    expect(wrapper.vm.typeDialogVisible).toBe(false)

    // 停用走编辑表单的 status=0，服务端完整覆盖。
    wrapper.vm.openTypeEdit(leaveTypes[2])
    expect(wrapper.vm.typeDialogTitle).toBe('编辑假期类型')
    expect(wrapper.vm.typeForm).toEqual(
      expect.objectContaining({ id: 3, name: '调休', overtimeCredit: 1, status: 0 }),
    )
    await flushPromises()
    await wrapper.vm.saveType()
    await flushPromises()
    expect(attendanceApi.saveOrUpdateLeaveType.mock.calls.at(-1)[0]).toEqual(
      expect.objectContaining({ id: 3, name: '调休', status: 0 }),
    )
  })

  it('grants multi-user adjustments and validates required fields locally', async () => {
    const { pinia } = setupSession()
    const wrapper = mount(AttendanceLeavePage, realOptions(pinia))
    await flushPromises()

    await wrapper.vm.openGrant()
    await flushPromises()
    expect(wrapper.vm.grantDialogVisible).toBe(true)
    expect(wrapper.vm.userOptions).toHaveLength(2)
    expect(attendanceApi.fetchLedgerUsers).toHaveBeenCalledWith('')

    await wrapper.vm.saveGrant()
    expect(messageMock.warning).toHaveBeenCalledWith('假期类型不能为空')

    wrapper.vm.grantForm = {
      leaveTypeId: 1,
      userIds: [],
      changeHours: -2.5,
      remark: '',
    }
    await wrapper.vm.saveGrant()
    expect(messageMock.warning).toHaveBeenCalledWith('发放成员不能为空')

    wrapper.vm.grantForm = {
      leaveTypeId: 1,
      userIds: [11, 12],
      changeHours: 0,
      remark: '',
    }
    await wrapper.vm.saveGrant()
    expect(messageMock.warning).toHaveBeenCalledWith('调整小时数不能为 0')

    wrapper.vm.grantForm = {
      leaveTypeId: 1,
      userIds: [11, 12],
      changeHours: -2.5,
      remark: '冲抵错发',
    }
    await flushPromises()
    await wrapper.vm.saveGrant()
    await flushPromises()
    expect(attendanceApi.grantAttendanceLeave).toHaveBeenCalledTimes(1)
    expect(attendanceApi.grantAttendanceLeave.mock.calls[0][0]).toEqual({
      leaveTypeId: 1,
      userIds: [11, 12],
      changeHours: -2.5,
      remark: '冲抵错发',
    })
    expect(wrapper.vm.grantDialogVisible).toBe(false)
    expect(messageMock.success).toHaveBeenCalledWith('已写入 2 条发放流水')
  })

  it('filters the ledger by type and date, then resets', async () => {
    const { pinia } = setupSession()
    const wrapper = mount(AttendanceLeavePage, realOptions(pinia))
    await flushPromises()

    wrapper.vm.ledgerTypeId = 1
    wrapper.vm.ledgerDateRange = ['2026-11-01', '2026-11-10']
    wrapper.vm.searchLedger()
    await flushPromises()
    const last = http.calls.at(-1)
    expect(last.url).toBe('/admin/attendance/ledger/1/10')
    expect(last.params).toEqual({
      leaveTypeId: 1,
      beginDate: '2026-11-01',
      endDate: '2026-11-10',
    })

    wrapper.vm.resetLedgerSearch()
    await flushPromises()
    expect(http.calls.at(-1).params).toEqual({})
  })
})
