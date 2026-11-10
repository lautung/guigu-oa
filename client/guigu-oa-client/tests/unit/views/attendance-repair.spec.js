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
  fetchRepairUsers: vi.fn(),
  fetchRepairDay: vi.fn(),
  saveAttendanceRepair: vi.fn(),
}))

vi.mock('@/api/attendance', () => attendanceApi)

import {
  ensureDomApis,
  installHttp,
  mountOptions,
  ok,
  paged,
  setupSession,
} from '../support/harness'
import AttendanceRepairPage from '@/views/business/attendance/repair/index.vue'

ensureDomApis()

const users = [
  { id: 11, username: 't281-alice', name: '甲', deptId: 1, deptName: '研发部' },
  { id: 12, username: 't281-bob', name: '乙', deptId: 1, deptName: '研发部' },
]

const day = {
  date: '2026-03-09',
  weekday: 1,
  dayType: 1,
  workday: true,
  hasGroup: true,
  status: 1,
  clockInTime: '09:00:00',
  clockOutTime: '18:05:00',
}

const punch = {
  id: 9,
  userId: 11,
  username: 't281-alice',
  name: '甲',
  deptId: 1,
  deptName: '研发部',
  punchDate: '2026-03-09',
  slot: 1,
  punchTime: '2026-03-09 09:00:00',
  source: 1,
  operatorName: '',
}

let pinia
let http
beforeEach(() => {
  vi.clearAllMocks()
  messageMock.mockReset()
  messageMock.success.mockReset()
  messageMock.warning.mockReset()
  attendanceApi.fetchRepairUsers.mockResolvedValue(ok(users))
  attendanceApi.fetchRepairDay.mockResolvedValue(ok(day))
  attendanceApi.saveAttendanceRepair.mockResolvedValue(ok({ ...day, clockOutTime: '18:30:00' }))
  http = installHttp((config) => {
    if (config.url === '/admin/attendance/repair/1/10') return paged([punch])
    return ok(null)
  })
  ;({ pinia } = setupSession({ permissions: ['attendance:record:repair'] }))
})

afterEach(() => {
  http.restore()
  vi.clearAllMocks()
})

describe('补卡处理页', () => {
  it('loads scoped members and the paged punch list on mount', async () => {
    const wrapper = mount(AttendanceRepairPage, mountOptions(pinia))
    await flushPromises()

    expect(attendanceApi.fetchRepairUsers).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('补卡处理')
    expect(wrapper.vm.users).toHaveLength(2)
    expect(wrapper.vm.users[0].label).toBe('研发部 / 甲（t281-alice）')
    expect(wrapper.vm.rows).toHaveLength(1)
    expect(wrapper.vm.rows[0].slotLabel).toBe('上班卡')
  })

  it('queries the day, pre-fills both slots and saves a repair', async () => {
    const wrapper = mount(AttendanceRepairPage, mountOptions(pinia))
    await flushPromises()

    wrapper.vm.selectedUserId = 11
    wrapper.vm.repairDate = '2026-03-09'
    await wrapper.vm.loadDay()
    await flushPromises()

    expect(attendanceApi.fetchRepairDay).toHaveBeenCalledWith(11, '2026-03-09')
    expect(wrapper.vm.clockIn).toBe('09:00:00')
    expect(wrapper.vm.clockOut).toBe('18:05:00')
    expect(wrapper.vm.dayView.statusLabel).toBe('正常')

    await wrapper.vm.save()
    await flushPromises()

    expect(attendanceApi.saveAttendanceRepair).toHaveBeenCalledWith({
      userId: 11,
      repairDate: '2026-03-09',
      clockInTime: '09:00:00',
      clockOutTime: '18:05:00',
    })
    expect(messageMock.success).toHaveBeenCalledWith('补卡成功')
    expect(wrapper.vm.clockOut).toBe('18:30:00')
  })

  it('warns instead of saving when both slots are empty', async () => {
    const wrapper = mount(AttendanceRepairPage, mountOptions(pinia))
    await flushPromises()

    wrapper.vm.selectedUserId = 11
    wrapper.vm.repairDate = '2026-03-09'
    await wrapper.vm.loadDay()
    wrapper.vm.clockIn = ''
    wrapper.vm.clockOut = ''
    await wrapper.vm.save()

    expect(messageMock.warning).toHaveBeenCalledWith('至少补一个槽位')
    expect(attendanceApi.saveAttendanceRepair).not.toHaveBeenCalled()
  })

  it('removes the save button without attendance:record:repair', async () => {
    ;({ pinia } = setupSession({ permissions: [] }))
    const wrapper = mount(AttendanceRepairPage, mountOptions(pinia))
    await flushPromises()
    wrapper.vm.selectedUserId = 11
    wrapper.vm.repairDate = '2026-03-09'
    await wrapper.vm.loadDay()
    await flushPromises()

    expect(wrapper.find('.repair-save').exists()).toBe(false)
  })
})
