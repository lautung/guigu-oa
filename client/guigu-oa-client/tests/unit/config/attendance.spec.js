import { describe, expect, it } from 'vitest'
import { emptyMessageOf } from '../support/form-rules'
import {
  ATTENDANCE_DAY_TYPE_OPTIONS,
  ATTENDANCE_PERMISSIONS,
  DEFAULT_WORKDAYS,
  attendanceHoursText,
  balancesRequest,
  emptyGrantForm,
  emptyLeaveTypeForm,
  grantableLeaveTypes,
  ledgerGrantRequest,
  ledgerListRequest,
  ledgerQueryOf,
  ledgerUsersRequest,
  leaveTypeFormRules,
  leaveTypeListRequest,
  leaveTypeOptionsRequest,
  leaveTypeRemoveRequest,
  leaveTypeWriteRequest,
  presentBalances,
  presentLeaveTypeForm,
  presentLeaveTypeListPage,
  presentLedgerListPage,
  PUNCH_SOURCE_OPTIONS,
  WORKDAY_OPTIONS,
  attendanceDayTypeLabel,
  emptyGroupForm,
  emptyHolidayForm,
  emptyShiftForm,
  groupFormRules,
  groupListRequest,
  groupMembersRequest,
  groupWriteRequest,
  holidayFormRules,
  holidayListRequest,
  holidayWriteRequest,
  parseWorkdays,
  presentGroupForm,
  presentGroupListPage,
  presentHolidayListPage,
  presentMemberIds,
  presentPunchListPage,
  presentShiftListPage,
  presentToday,
  presentUserOptions,
  punchListRequest,
  punchRequest,
  punchSlotLabel,
  punchSourceLabel,
  attendanceDayStatusLabel,
  attendanceDayStatusTagType,
  monthRecordsRequest,
  presentMonth,
  applicationEditable,
  applicationStatusLabel,
  applicationStatusTagType,
  emptyLeaveApplyForm,
  emptyOvertimeApplyForm,
  leaveApplyFormRules,
  leaveRemoveRequest,
  leaveSaveRequest,
  leaveSubmitRequest,
  myLeavePageRequest,
  myLeaveTypesRequest,
  myOvertimePageRequest,
  overtimeApplyFormRules,
  overtimeRemoveRequest,
  overtimeSaveRequest,
  overtimeSubmitRequest,
  presentLeaveApplication,
  presentMyLeaveTypes,
  presentOvertimeApplication,
  repairDayRequest,
  repairSaveRequest,
  repairUsersRequest,
  shiftFormRules,
  shiftListRequest,
  shiftWriteRequest,
  shiftOptionsRequest,
  shiftRemoveRequest,
  todayRequest,
  userOptionsRequest,
  validateShiftLunch,
  workdayLabels,
} from '@/config/attendance'

describe('config/attendance', () => {
  it('declares the group-page and repair button points without query points', () => {
    expect(ATTENDANCE_PERMISSIONS).toEqual({
      add: 'attendance:group:add',
      edit: 'attendance:group:edit',
      remove: 'attendance:group:remove',
      repair: 'attendance:record:repair',
      typeAdd: 'attendance:type:add',
      typeEdit: 'attendance:type:edit',
      typeRemove: 'attendance:type:remove',
      grant: 'attendance:ledger:grant',
    })
  })

  describe('班次', () => {
    it('pages shifts with GET /admin/attendance/shift/{page}/{limit}', () => {
      expect(shiftListRequest(2, 20, { name: '早班' })).toEqual({
        method: 'get',
        url: '/admin/attendance/shift/2/20',
        params: { name: '早班' },
      })
    })

    it('reads shift options for the group form dropdown', () => {
      expect(shiftOptionsRequest()).toEqual({
        method: 'get',
        url: '/admin/attendance/shift/options',
      })
    })

    it('presents shift rows unchanged for the table', () => {
      expect(
        presentShiftListPage({
          records: [
            {
              id: 1,
              name: '标准班',
              startTime: '09:00:00',
              endTime: '18:00:00',
              lunchStart: '12:00:00',
              lunchEnd: '13:00:00',
              graceMinutes: 10,
              remark: '研发',
              createTime: '2026-11-10 09:00:00',
            },
          ],
          total: 1,
          pages: 1,
        }),
      ).toEqual({
        records: [
          {
            id: 1,
            name: '标准班',
            startTime: '09:00:00',
            endTime: '18:00:00',
            lunchStart: '12:00:00',
            lunchEnd: '13:00:00',
            graceMinutes: 10,
            remark: '研发',
            createTime: '2026-11-10 09:00:00',
          },
        ],
        total: 1,
        pages: 1,
      })
    })

    it('writes shifts as full-form payloads: create defaults lunch to null', () => {
      expect(
        shiftWriteRequest({
          ...emptyShiftForm(),
          name: '晚班',
          startTime: '14:00:00',
          endTime: '22:00:00',
          graceMinutes: 5,
          remark: ' 客服 ',
        }),
      ).toEqual({
        method: 'post',
        url: '/admin/attendance/shift/save',
        data: {
          name: '晚班',
          startTime: '14:00:00',
          endTime: '22:00:00',
          lunchStart: null,
          lunchEnd: null,
          graceMinutes: 5,
          remark: '客服',
        },
      })
    })

    it('updates shifts with the id and an explicit empty lunch pair clears it', () => {
      expect(
        shiftWriteRequest({
          id: 3,
          name: '标准班',
          startTime: '09:00:00',
          endTime: '18:00:00',
          lunchStart: '',
          lunchEnd: '',
          graceMinutes: 0,
          remark: '',
        }),
      ).toEqual({
        method: 'put',
        url: '/admin/attendance/shift/update',
        data: {
          id: 3,
          name: '标准班',
          startTime: '09:00:00',
          endTime: '18:00:00',
          lunchStart: null,
          lunchEnd: null,
          graceMinutes: 0,
          remark: '',
        },
      })
    })

    it('keeps a filled lunch pair on update', () => {
      const request = shiftWriteRequest({
        id: 3,
        name: '标准班',
        startTime: '09:00:00',
        endTime: '18:00:00',
        lunchStart: '12:00:00',
        lunchEnd: '13:00:00',
        graceMinutes: 0,
        remark: '',
      })
      expect(request.data.lunchStart).toBe('12:00:00')
      expect(request.data.lunchEnd).toBe('13:00:00')
    })

    it('removes shifts by id', () => {
      expect(shiftRemoveRequest(7)).toEqual({
        method: 'delete',
        url: '/admin/attendance/shift/remove/7',
      })
    })

    it('mirrors the server required messages and validates the lunch pair locally', () => {
      expect(emptyMessageOf(shiftFormRules().name[0])).toBe('班次名称不能为空')
      expect(emptyMessageOf(shiftFormRules().startTime[0])).toBe('上班时刻不能为空')
      expect(emptyMessageOf(shiftFormRules().endTime[0])).toBe('下班时刻不能为空')
      expect(
        validateShiftLunch({ ...emptyShiftForm(), lunchStart: '12:00:00', lunchEnd: '' }),
      ).toBe('午休开始与结束必须同时填写')
      expect(
        validateShiftLunch({ ...emptyShiftForm(), lunchStart: '', lunchEnd: '13:00:00' }),
      ).toBe('午休开始与结束必须同时填写')
      expect(
        validateShiftLunch({ ...emptyShiftForm(), lunchStart: '13:00:00', lunchEnd: '12:00:00' }),
      ).toBe('午休结束必须晚于午休开始')
      expect(
        validateShiftLunch({ ...emptyShiftForm(), lunchStart: '12:00:00', lunchEnd: '13:00:00' }),
      ).toBe('')
    })
  })

  describe('考勤组', () => {
    it('pages groups with GET /admin/attendance/group/{page}/{limit} and reads members', () => {
      expect(groupListRequest(1, 10, { name: '研发' })).toEqual({
        method: 'get',
        url: '/admin/attendance/group/1/10',
        params: { name: '研发' },
      })
      expect(groupMembersRequest(9)).toEqual({
        method: 'get',
        url: '/admin/attendance/group/9/members',
      })
      expect(userOptionsRequest()).toEqual({
        method: 'get',
        url: '/admin/attendance/group/userOptions',
      })
    })

    it('projects group rows with shift name and member count from the server join', () => {
      expect(
        presentGroupListPage({
          records: [
            {
              id: 5,
              name: '研发组',
              shiftId: 1,
              shiftName: '标准班',
              workdays: '1,2,3,4,5',
              memberCount: 3,
              remark: '',
              createTime: '2026-11-10 09:00:00',
            },
          ],
          total: 1,
          pages: 1,
        }).records,
      ).toEqual([expect.objectContaining({ id: 5, shiftName: '标准班', memberCount: 3 })])
    })

    it('parses workday strings into checkbox arrays and back to labels', () => {
      expect(parseWorkdays('1,2,3,4,5')).toEqual([1, 2, 3, 4, 5])
      expect(parseWorkdays('')).toEqual([])
      expect(parseWorkdays(null)).toEqual([])
      expect(workdayLabels('1,2,3,4,5')).toBe('周一、周二、周三、周四、周五')
      expect(workdayLabels('6')).toBe('周六')
      expect(WORKDAY_OPTIONS).toHaveLength(7)
      expect(DEFAULT_WORKDAYS).toEqual([1, 2, 3, 4, 5])
    })

    it('projects member candidates as 姓名（用户名） options', () => {
      expect(
        presentUserOptions([
          { id: 1, username: 'alice', name: '张三' },
          { id: 2, username: 'bob', name: '' },
        ]),
      ).toEqual([
        { id: 1, username: 'alice', name: '张三', label: '张三（alice）' },
        { id: 2, username: 'bob', name: '', label: 'bob' },
      ])
      expect(presentUserOptions([{ username: 'noid' }])).toEqual([])
    })

    it('flattens member rows into user id lists for the edit dialog', () => {
      expect(
        presentMemberIds([
          { id: 1, userId: 11, username: 'alice', name: '张三' },
          { id: 2, userId: 12, username: 'bob', name: '李四' },
        ]),
      ).toEqual([11, 12])
      expect(presentMemberIds([])).toEqual([])
    })

    it('creates a group with members; updates replace them explicitly (postIds semantics)', () => {
      expect(
        groupWriteRequest({
          ...emptyGroupForm(),
          name: '研发组',
          shiftId: 1,
          workdays: [5, 1, 3],
          memberUserIds: [11, 12],
          remark: '研',
        }),
      ).toEqual({
        method: 'post',
        url: '/admin/attendance/group/save',
        data: {
          name: '研发组',
          shiftId: 1,
          workdays: [5, 1, 3],
          memberUserIds: [11, 12],
          remark: '研',
        },
      })
      expect(
        groupWriteRequest({
          ...presentGroupForm({ id: 5, name: '研发组', shiftId: 1, workdays: '1', remark: '' }),
          memberUserIds: [],
        }),
      ).toEqual({
        method: 'put',
        url: '/admin/attendance/group/update',
        data: {
          id: 5,
          name: '研发组',
          shiftId: 1,
          workdays: [1],
          memberUserIds: [],
          remark: '',
        },
      })
    })

    it('defaults the group form to Monday-Friday with no members', () => {
      expect(emptyGroupForm()).toEqual({
        id: '',
        name: '',
        shiftId: '',
        workdays: [1, 2, 3, 4, 5],
        memberUserIds: [],
        remark: '',
      })
    })

    it('mirrors the group required messages', () => {
      expect(emptyMessageOf(groupFormRules().name[0])).toBe('考勤组名称不能为空')
      expect(emptyMessageOf(groupFormRules().shiftId[0])).toBe('绑定的班次不能为空')
    })
  })

  describe('节假日', () => {
    it('pages holidays with GET /admin/attendance/holiday/{page}/{limit}', () => {
      expect(holidayListRequest(1, 10)).toEqual({
        method: 'get',
        url: '/admin/attendance/holiday/1/10',
        params: {},
      })
    })

    it('presents holiday rows with day type labels', () => {
      const presented = presentHolidayListPage({
        records: [
          { id: 1, holidayDate: '2026-10-01', name: '国庆', dayType: 1 },
          { id: 2, holidayDate: '2026-09-28', name: '调休上班', dayType: 2 },
        ],
        total: 2,
        pages: 1,
      })
      expect(presented.records).toEqual([
        expect.objectContaining({ id: 1, name: '国庆', dayType: 1 }),
        expect.objectContaining({ id: 2, name: '调休上班', dayType: 2 }),
      ])
    })

    it('creates and updates holidays by id', () => {
      expect(
        holidayWriteRequest({
          ...emptyHolidayForm(),
          holidayDate: '2026-10-01',
          name: '国庆',
          dayType: 1,
        }),
      ).toEqual({
        method: 'post',
        url: '/admin/attendance/holiday/save',
        data: { holidayDate: '2026-10-01', name: '国庆', dayType: 1 },
      })
      expect(
        holidayWriteRequest({ id: 2, holidayDate: '2026-10-02', name: '补班', dayType: 2 }),
      ).toEqual({
        method: 'put',
        url: '/admin/attendance/holiday/update',
        data: { id: 2, holidayDate: '2026-10-02', name: '补班', dayType: 2 },
      })
    })

    it('mirrors the holiday required messages', () => {
      expect(emptyMessageOf(holidayFormRules().holidayDate[0])).toBe('节假日日期不能为空')
      expect(emptyMessageOf(holidayFormRules().name[0])).toBe('节假日名称不能为空')
    })
  })

  describe('打卡', () => {
    it('reads today status and posts the two punch slots', () => {
      expect(todayRequest()).toEqual({
        method: 'get',
        url: '/admin/attendance/my/today',
      })
      expect(punchRequest(1)).toEqual({
        method: 'post',
        url: '/admin/attendance/my/punch/in',
      })
      expect(punchRequest(2)).toEqual({
        method: 'post',
        url: '/admin/attendance/my/punch/out',
      })
    })

    it('pages punch records with person/date/source filters', () => {
      expect(
        punchListRequest(2, 20, {
          name: '甲',
          beginDate: '2026-11-01',
          endDate: '2026-11-10',
          source: 1,
        }),
      ).toEqual({
        method: 'get',
        url: '/admin/attendance/punch/2/20',
        params: { name: '甲', beginDate: '2026-11-01', endDate: '2026-11-10', source: 1 },
      })
    })

    it('projects today status into display labels and HH:mm card times', () => {
      expect(
        presentToday({
          date: '2026-11-10',
          weekday: 3,
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
          clockInTime: '08:30:00',
          clockOutTime: null,
        }),
      ).toEqual({
        date: '2026-11-10',
        weekdayLabel: '周三',
        dayType: 1,
        dayTypeLabel: '工作日',
        workday: true,
        hasGroup: true,
        groupName: '研发组',
        shift: {
          name: '标准班',
          timeRange: '09:00 - 18:00',
          lunchText: '12:00 - 13:00',
          graceMinutes: 10,
        },
        clockInTime: '08:30',
        clockOutTime: '',
      })

      expect(presentToday({ hasGroup: false, dayType: 0 })).toEqual({
        date: '',
        weekdayLabel: '',
        dayType: 0,
        dayTypeLabel: '未分组',
        workday: false,
        hasGroup: false,
        groupName: '',
        shift: null,
        clockInTime: '',
        clockOutTime: '',
      })
    })

    it('labels day types, slots, and sources', () => {
      expect(ATTENDANCE_DAY_TYPE_OPTIONS.map((option) => option.label)).toEqual([
        '未分组',
        '工作日',
        '休息日',
        '休假日',
        '补班日',
      ])
      expect(attendanceDayTypeLabel(2)).toBe('休息日')
      expect(attendanceDayTypeLabel(undefined)).toBe('未分组')
      expect(punchSlotLabel(1)).toBe('上班卡')
      expect(punchSlotLabel(2)).toBe('下班卡')
      expect(PUNCH_SOURCE_OPTIONS.map((option) => option.label)).toEqual(['网页打卡', '补卡'])
      expect(punchSourceLabel(2)).toBe('补卡')
    })

    it('projects punch record rows with joined names and labels', () => {
      expect(
        presentPunchListPage({
          records: [
            {
              id: 9,
              userId: 11,
              username: 'alice',
              name: '甲',
              deptId: 2,
              deptName: '研发部',
              punchDate: '2026-11-10',
              slot: 2,
              punchTime: '2026-11-10 18:30:00',
              source: 2,
              operatorName: '乙',
            },
          ],
          total: 1,
          pages: 1,
        }),
      ).toEqual({
        records: [
          {
            id: 9,
            userId: 11,
            username: 'alice',
            name: '甲',
            deptName: '研发部',
            punchDate: '2026-11-10',
            slot: 2,
            slotLabel: '下班卡',
            punchTime: '2026-11-10 18:30:00',
            source: 2,
            sourceLabel: '补卡',
            operatorName: '乙',
          },
        ],
        total: 1,
        pages: 1,
      })
    })
  })

  describe('逐日状态与补卡（#281）', () => {
    it('labels the nine statuses, pending and leave type', () => {
      expect(attendanceDayStatusLabel(1)).toBe('正常')
      expect(attendanceDayStatusLabel(4)).toBe('迟到且早退')
      expect(attendanceDayStatusLabel(6, false, '年假')).toBe('请假·年假')
      expect(attendanceDayStatusLabel(null, true)).toBe('未打卡')
      expect(attendanceDayStatusLabel(null, false)).toBe('—')
      expect(attendanceDayStatusTagType(1)).toBe('success')
      expect(attendanceDayStatusTagType(5)).toBe('danger')
      expect(attendanceDayStatusTagType(null, true)).toBe('info')
    })

    it('projects the month and omits an empty month parameter', () => {
      expect(monthRecordsRequest()).toEqual({
        method: 'get',
        url: '/admin/attendance/my/records',
        params: {},
      })
      expect(presentMonth({ month: '2026-03', days: [{ date: '2026-03-02', status: 1 }] })).toEqual(
        {
          month: '2026-03',
          days: [
            expect.objectContaining({
              date: '2026-03-02',
              status: 1,
              statusLabel: '正常',
            }),
          ],
        },
      )
    })

    it('builds repair requests with only the filled slots', () => {
      expect(repairUsersRequest(' 甲 ')).toEqual({
        method: 'get',
        url: '/admin/attendance/repair/users',
        params: { name: '甲' },
      })
      expect(repairDayRequest(11, '2026-03-09')).toEqual({
        method: 'get',
        url: '/admin/attendance/repair/day',
        params: { userId: 11, date: '2026-03-09' },
      })
      expect(
        repairSaveRequest({
          userId: 11,
          repairDate: '2026-03-09',
          clockInTime: '',
          clockOutTime: '18:30:00',
        }),
      ).toEqual({
        method: 'post',
        url: '/admin/attendance/repair/save',
        data: { userId: 11, repairDate: '2026-03-09', clockOutTime: '18:30:00' },
      })
    })
  })

  describe('假期类型与发放（#280）', () => {
    it('lists types and options, and only offers balance-required enabled types for grants', () => {
      expect(leaveTypeListRequest(2, 20, { name: '年假' })).toEqual({
        method: 'get',
        url: '/admin/attendance/leave-type/2/20',
        params: { name: '年假' },
      })
      expect(leaveTypeOptionsRequest()).toEqual({
        method: 'get',
        url: '/admin/attendance/leave-type/options',
      })
      const options = [
        { id: 1, name: '年假', balanceRequired: 1, status: 1 },
        { id: 2, name: '事假', balanceRequired: 0, status: 1 },
        { id: 3, name: '调休', balanceRequired: 1, status: 0 },
      ]
      expect(grantableLeaveTypes(options)).toEqual([
        expect.objectContaining({ id: 1, name: '年假' }),
      ])
      expect(presentLeaveTypeListPage({ records: options, total: 3, pages: 1 })).toEqual({
        records: [
          expect.objectContaining({ id: 1, name: '年假', status: 1, sortNo: 0 }),
          expect.objectContaining({ id: 2, name: '事假', balanceRequired: 0 }),
          expect.objectContaining({ id: 3, name: '调休', status: 0 }),
        ],
        total: 3,
        pages: 1,
      })
    })

    it('writes types as full-form payloads and mirrors the required name message', () => {
      expect(emptyMessageOf(leaveTypeFormRules().name[0])).toBe('假期类型名称不能为空')
      expect(
        leaveTypeWriteRequest({
          ...emptyLeaveTypeForm(),
          name: ' 育儿假 ',
          remark: ' 备注 ',
        }),
      ).toEqual({
        method: 'post',
        url: '/admin/attendance/leave-type/save',
        data: {
          name: '育儿假',
          balanceRequired: 1,
          overtimeCredit: 0,
          status: 1,
          sortNo: 0,
          remark: '备注',
        },
      })
      expect(
        leaveTypeWriteRequest({
          id: 7,
          name: '调休',
          balanceRequired: 1,
          overtimeCredit: 1,
          status: 0,
          sortNo: 4,
          remark: '',
        }),
      ).toEqual({
        method: 'put',
        url: '/admin/attendance/leave-type/update',
        data: {
          id: 7,
          name: '调休',
          balanceRequired: 1,
          overtimeCredit: 1,
          status: 0,
          sortNo: 4,
          remark: '',
        },
      })
      expect(presentLeaveTypeForm({ id: 7, name: '调休', overtimeCredit: 1 })).toEqual(
        expect.objectContaining({ id: 7, name: '调休', overtimeCredit: 1, status: 1 }),
      )
      expect(leaveTypeRemoveRequest(7)).toEqual({
        method: 'delete',
        url: '/admin/attendance/leave-type/remove/7',
      })
    })

    it('pages ledger rows and projects signed hours with entry labels', () => {
      expect(ledgerListRequest(1, 10, { leaveTypeId: 1 })).toEqual({
        method: 'get',
        url: '/admin/attendance/ledger/1/10',
        params: { leaveTypeId: 1 },
      })
      expect(ledgerQueryOf({ leaveTypeId: '', beginDate: '2026-03-01' })).toEqual({
        beginDate: '2026-03-01',
      })
      expect(ledgerUsersRequest('甲')).toEqual({
        method: 'get',
        url: '/admin/attendance/ledger/users',
        params: { name: '甲' },
      })
      expect(
        presentLedgerListPage({
          records: [
            {
              id: 1,
              name: '甲',
              leaveTypeName: '年假',
              entryType: 1,
              changeHours: -2.25,
              remark: '',
            },
            {
              id: 2,
              name: '乙',
              leaveTypeName: '调休',
              entryType: 3,
              changeHours: 8,
              remark: '',
            },
          ],
          total: 2,
          pages: 1,
        }).records,
      ).toEqual([
        expect.objectContaining({
          id: 1,
          entryTypeLabel: '发放',
          changeHoursText: '-2.25',
          negative: true,
        }),
        expect.objectContaining({
          id: 2,
          entryTypeLabel: '加班入账',
          changeHoursText: '+8',
          negative: false,
        }),
      ])
    })

    it('builds multi-user grant payloads with signed hours', () => {
      expect(emptyGrantForm()).toEqual({
        leaveTypeId: '',
        userIds: [],
        changeHours: 0,
        remark: '',
      })
      expect(
        ledgerGrantRequest({
          leaveTypeId: 1,
          userIds: [11, 12],
          changeHours: -2.5,
          remark: ' 冲抵 ',
        }),
      ).toEqual({
        method: 'post',
        url: '/admin/attendance/ledger/grant',
        data: { leaveTypeId: 1, userIds: [11, 12], changeHours: -2.5, remark: '冲抵' },
      })
    })

    it('projects personal balances with pending and negative states', () => {
      expect(balancesRequest()).toEqual({
        method: 'get',
        url: '/admin/attendance/my/balances',
      })
      expect(
        presentBalances([
          { leaveTypeId: 1, leaveTypeName: '年假', availableHours: 5.5, pendingHours: 2 },
        ]),
      ).toEqual([
        expect.objectContaining({
          leaveTypeId: 1,
          leaveTypeName: '年假',
          availableHoursText: '5.5',
          pendingText: '在途 2',
          negative: false,
        }),
      ])
      expect(
        presentBalances([{ leaveTypeId: 3, leaveTypeName: '调休', availableHours: -1 }])[0],
      ).toEqual(
        expect.objectContaining({ availableHoursText: '-1', negative: true, pendingText: '' }),
      )
      expect(attendanceHoursText(8)).toBe('8')
      expect(attendanceHoursText(5.5)).toBe('5.5')
      expect(attendanceHoursText(-2.25)).toBe('-2.25')
    })
  })

  describe('我的申请（#282）', () => {
    it('labels five application states and only draft/rejected/withdrawn are editable', () => {
      expect(applicationStatusLabel(0)).toBe('草稿')
      expect(applicationStatusLabel(1)).toBe('审批中')
      expect(applicationStatusLabel(2)).toBe('已通过')
      expect(applicationStatusLabel(-1)).toBe('已拒绝')
      expect(applicationStatusLabel(-2)).toBe('已撤回')
      expect(applicationStatusTagType(2)).toBe('success')
      expect(applicationStatusTagType(-1)).toBe('danger')
      expect([0, -1, -2].every(applicationEditable)).toBe(true)
      expect([1, 2].some(applicationEditable)).toBe(false)
    })

    it('projects leave/overtime rows and keeps draft durations pending until submit', () => {
      expect(
        presentLeaveApplication({
          id: 501,
          leaveTypeName: '年假',
          startDate: '2026-12-07',
          endDate: '2026-12-08',
          days: 2,
          hours: 16,
          reason: '回家',
          status: 1,
          submitTime: '2026-11-10 09:00:00',
        }),
      ).toEqual(
        expect.objectContaining({
          id: 501,
          kind: 'leave',
          dateText: '2026-12-07 至 2026-12-08',
          durationText: '2 天（16 小时）',
          statusLabel: '审批中',
          editable: false,
          submitTimeText: '2026-11-10 09:00',
        }),
      )
      expect(presentLeaveApplication({ id: 502, status: 0, days: 0, hours: 0 })).toEqual(
        expect.objectContaining({
          durationText: '提交后按班次与日历定稿',
          editable: true,
          submittable: true,
        }),
      )
      expect(
        presentOvertimeApplication({
          id: 601,
          overtimeDate: '2026-12-12',
          startTime: '18:00:00',
          endTime: '20:30:00',
          hours: 2.5,
          status: -2,
        }),
      ).toEqual(
        expect.objectContaining({
          kind: 'overtime',
          dateText: '2026-12-12 18:00-20:30',
          durationText: '2.5 小时',
          statusLabel: '已撤回',
          editable: true,
          submittable: true,
        }),
      )
      expect(presentOvertimeApplication({ id: 602, status: 0 })).toEqual(
        expect.objectContaining({ durationText: '提交后计算' }),
      )
    })

    it('builds application requests with idempotency keys and trimmed payloads', () => {
      expect(myLeavePageRequest(2, 10, -1)).toEqual({
        method: 'get',
        url: '/admin/attendance/my/leave/2/10',
        params: { status: -1 },
      })
      expect(myLeavePageRequest(1, 10)).toEqual({
        method: 'get',
        url: '/admin/attendance/my/leave/1/10',
        params: {},
      })
      expect(myOvertimePageRequest(1, 10)).toEqual({
        method: 'get',
        url: '/admin/attendance/my/overtime/1/10',
        params: {},
      })
      expect(myLeaveTypesRequest()).toEqual({
        method: 'get',
        url: '/admin/attendance/my/leave/types',
      })
      expect(emptyLeaveApplyForm()).toEqual({
        id: '',
        leaveTypeId: '',
        startDate: '',
        endDate: '',
        reason: '',
      })
      expect(emptyOvertimeApplyForm()).toEqual({
        id: '',
        overtimeDate: '',
        startTime: '',
        endTime: '',
        reason: '',
      })
      expect(
        leaveSaveRequest({
          id: '',
          leaveTypeId: 1,
          startDate: '2026-12-07',
          endDate: '2026-12-08',
          reason: ' 回家 ',
        }),
      ).toEqual({
        method: 'post',
        url: '/admin/attendance/my/leave/save',
        data: {
          leaveTypeId: 1,
          startDate: '2026-12-07',
          endDate: '2026-12-08',
          reason: '回家',
        },
      })
      expect(
        overtimeSaveRequest({
          id: '',
          overtimeDate: '2026-12-12',
          startTime: '18:00:00',
          endTime: '20:00:00',
          reason: ' 上线 ',
        }),
      ).toEqual({
        method: 'post',
        url: '/admin/attendance/my/overtime/save',
        data: {
          overtimeDate: '2026-12-12',
          startTime: '18:00:00',
          endTime: '20:00:00',
          reason: '上线',
        },
      })
      expect(leaveSubmitRequest(501, 'key-1')).toEqual({
        method: 'post',
        url: '/admin/attendance/my/leave/501/submit',
        data: { idempotencyKey: 'key-1' },
      })
      expect(overtimeSubmitRequest(601, 'key-2')).toEqual({
        method: 'post',
        url: '/admin/attendance/my/overtime/601/submit',
        data: { idempotencyKey: 'key-2' },
      })
      expect(leaveRemoveRequest(501)).toEqual({
        method: 'delete',
        url: '/admin/attendance/my/leave/remove/501',
      })
      expect(overtimeRemoveRequest(601)).toEqual({
        method: 'delete',
        url: '/admin/attendance/my/overtime/remove/601',
      })
      expect(Object.keys(leaveApplyFormRules())).toEqual(['leaveTypeId', 'startDate', 'endDate'])
      expect(Object.keys(overtimeApplyFormRules())).toEqual([
        'overtimeDate',
        'startTime',
        'endTime',
      ])
    })

    it('projects enabled leave types with available balance only for balance types', () => {
      expect(
        presentMyLeaveTypes([
          { id: 1, name: '年假', balanceRequired: 1, availableHours: 8 },
          { id: 2, name: '事假', balanceRequired: 0, availableHours: null },
        ]),
      ).toEqual([
        { id: 1, name: '年假', balanceRequired: 1, availableHoursText: '8' },
        { id: 2, name: '事假', balanceRequired: 0, availableHoursText: '' },
      ])
    })
  })
})
