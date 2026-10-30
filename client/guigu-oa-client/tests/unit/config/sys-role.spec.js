import { describe, expect, it } from 'vitest'
import { emptyMessageOf } from '../support/form-rules'
import {
  assignRolesRequest,
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

    it('creates a 角色 with POST /admin/system/sysRole/save', () => {
      expect(writeRequest({ roleName: '审批人', roleCode: 'approver' })).toEqual({
        method: 'post',
        url: '/admin/system/sysRole/save',
        data: { roleName: '审批人', roleCode: 'approver' },
      })
    })

    it('updates a 角色 with PUT /admin/system/sysRole/update', () => {
      expect(
        writeRequest({
          id: 3,
          roleName: '审批人',
          roleCode: 'approver',
        }),
      ).toEqual({
        method: 'put',
        url: '/admin/system/sysRole/update',
        data: { id: 3, roleName: '审批人', roleCode: 'approver' },
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
  })

  describe('presentForm', () => {
    it('starts 新建 with an empty form', () => {
      expect(emptyForm()).toEqual({
        id: '',
        roleName: '',
        roleCode: '',
      })
    })

    it('loads an existing 角色 into the form', () => {
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
      })
    })
  })

  describe('formRules', () => {
    it('mirrors the server 必填 messages for name and code', () => {
      const rules = formRules()
      expect(Object.keys(rules).sort()).toEqual(['roleCode', 'roleName'])
      expect(emptyMessageOf(rules.roleName[0])).toBe('角色名称不能为空')
      expect(emptyMessageOf(rules.roleCode[0])).toBe('角色编码不能为空')
    })
  })
})
