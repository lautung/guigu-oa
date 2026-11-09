import { describe, expect, it } from 'vitest'
import { emptyMessageOf } from '../support/form-rules'
import {
  ATTENDANCE_PERMISSIONS,
  DEFAULT_WORKDAYS,
  WORKDAY_OPTIONS,
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
  presentShiftListPage,
  presentUserOptions,
  shiftFormRules,
  shiftListRequest,
  shiftWriteRequest,
  shiftOptionsRequest,
  shiftRemoveRequest,
  userOptionsRequest,
  validateShiftLunch,
  workdayLabels,
} from '@/config/attendance'

describe('config/attendance', () => {
  it('declares the three group-page button points without query points', () => {
    expect(ATTENDANCE_PERMISSIONS).toEqual({
      add: 'attendance:group:add',
      edit: 'attendance:group:edit',
      remove: 'attendance:group:remove',
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
})
