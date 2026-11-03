import { describe, expect, it } from 'vitest'
import { emptyMessageOf } from '../support/form-rules'
import {
  DATA_SCOPE_OPTIONS,
  assignRolesRequest,
  dataScopeLabel,
  deptOptionsRequest,
  emptyForm,
  formRules,
  getRequest,
  listRequest,
  presentForm,
  presentListPage,
  removeRequest,
  toAssignRolesRequest,
  writeRequest,
} from '@/config/sys-role'

describe('config/sys-role', () => {
  describe('presentListPage', () => {
    it('presents a paginated 角色 list with name, code, and time', () => {
      expect(
        presentListPage({
          records: [
            {
              id: 3,
              roleName: '审批人',
              roleCode: 'approver',
              createTime: '2026-08-01 10:00:00',
            },
          ],
          total: 1,
          pages: 1,
        }),
      ).toEqual({
        records: [
          {
            id: 3,
            roleName: '审批人',
            roleCode: 'approver',
            createTime: '2026-08-01 10:00:00',
          },
        ],
        total: 1,
        pages: 1,
      })
    })
  })

  describe('角色 API', () => {
    it('pages 角色 with GET /admin/system/sysRole/{page}/{limit}', () => {
      expect(listRequest(1, 10, { roleName: '审批' })).toEqual({
        method: 'get',
        url: '/admin/system/sysRole/1/10',
        params: { roleName: '审批' },
      })
    })

    it('creates a 角色 with POST /admin/system/sysRole/save and the default scope', () => {
      expect(writeRequest({ roleName: '审批人', roleCode: 'approver' })).toEqual({
        method: 'post',
        url: '/admin/system/sysRole/save',
        data: { roleName: '审批人', roleCode: 'approver', dataScope: 5, deptIds: [] },
      })
    })

    it('updates a 角色 with PUT /admin/system/sysRole/update and its data scope', () => {
      expect(
        writeRequest({
          id: 3,
          roleName: '审批人',
          roleCode: 'approver',
          dataScope: 2,
          deptIds: [7, 8],
        }),
      ).toEqual({
        method: 'put',
        url: '/admin/system/sysRole/update',
        data: {
          id: 3,
          roleName: '审批人',
          roleCode: 'approver',
          dataScope: 2,
          deptIds: [7, 8],
        },
      })
    })

    it('loads one 角色 with GET /admin/system/sysRole/get/{id}', () => {
      expect(getRequest(3)).toEqual({
        method: 'get',
        url: '/admin/system/sysRole/get/3',
      })
    })

    it('deletes a 角色 with DELETE /admin/system/sysRole/remove/{id}', () => {
      expect(removeRequest(3)).toEqual({
        method: 'delete',
        url: '/admin/system/sysRole/remove/3',
      })
    })

    it('loads a 用户的角色 with GET /admin/system/sysRole/toAssign/{userId}', () => {
      expect(toAssignRolesRequest(2)).toEqual({
        method: 'get',
        url: '/admin/system/sysRole/toAssign/2',
      })
    })

    it('assigns 角色 to a 用户 with POST /admin/system/sysRole/doAssign', () => {
      expect(assignRolesRequest(2, [3])).toEqual({
        method: 'post',
        url: '/admin/system/sysRole/doAssign',
        data: { userId: 2, roleIdList: [3] },
      })
    })

    it('loads the data-scope dept options with GET /admin/system/sysRole/depts', () => {
      expect(deptOptionsRequest()).toEqual({
        method: 'get',
        url: '/admin/system/sysRole/depts',
      })
    })
  })

  describe('presentForm', () => {
    it('starts 新建 with the self-only default and no departments', () => {
      expect(emptyForm()).toEqual({
        id: '',
        roleName: '',
        roleCode: '',
        dataScope: 5,
        deptIds: [],
      })
    })

    it('loads an existing 角色 into the form with its scope and departments', () => {
      expect(
        presentForm({
          id: 3,
          roleName: '审批人',
          roleCode: 'approver',
          dataScope: 2,
          deptIds: [7, 8],
        }),
      ).toEqual({
        id: 3,
        roleName: '审批人',
        roleCode: 'approver',
        dataScope: 2,
        deptIds: [7, 8],
      })
    })

    it('falls back to the self-only scope when a legacy role has no data_scope', () => {
      expect(
        presentForm({
          id: 3,
          roleName: '审批人',
          roleCode: 'approver',
        }),
      ).toEqual({
        id: 3,
        roleName: '审批人',
        roleCode: 'approver',
        dataScope: 5,
        deptIds: [],
      })
    })
  })

  describe('数据范围', () => {
    it('exposes the five labels in tier order', () => {
      expect(DATA_SCOPE_OPTIONS.map((option) => option.value)).toEqual([1, 2, 3, 4, 5])
      expect(DATA_SCOPE_OPTIONS.map((option) => option.label)).toEqual([
        '全部',
        '自定部门',
        '本部门',
        '本部门及以下',
        '仅本人',
      ])
      expect(dataScopeLabel(2)).toBe('自定部门')
      expect(dataScopeLabel(undefined)).toBe('')
    })
  })

  describe('formRules', () => {
    it('mirrors the server 必填 messages for name and code', () => {
      const rules = formRules()
      expect(Object.keys(rules).sort()).toEqual(['deptIds', 'roleCode', 'roleName'])
      expect(emptyMessageOf(rules.roleName[0])).toBe('角色名称不能为空')
      expect(emptyMessageOf(rules.roleCode[0])).toBe('角色编码不能为空')
    })

    it('requires at least one department only for the custom-department scope', () => {
      expect(formRules(3).deptIds).toEqual([])
      expect(formRules(2).deptIds).toHaveLength(1)
      expect(emptyMessageOf(formRules(2).deptIds[0])).toBe('自定部门不能为空')

      let error
      formRules(2).deptIds[0].validator({}, [7], (result) => {
        error = result
      })
      expect(error).toBeUndefined()
    })
  })
})
