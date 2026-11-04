import { describe, expect, it } from 'vitest'
import { emptyMessageOf } from '../support/form-rules'
import {
  emptyForm,
  emptyResetPasswordForm,
  formRules,
  getRequest,
  listRequest,
  presentForm,
  presentListPage,
  removeRequest,
  resetPasswordRequest,
  resetPasswordRules,
  SYS_USER_PERMISSIONS,
  updateStatusRequest,
  writeRequest,
} from '@/config/sys-user'

function messageOf(rule, value) {
  let error
  rule.validator({}, value, (result) => {
    error = result
  })
  return error && error.message
}

describe('config/sys-user', () => {
  describe('presentListPage', () => {
    it('presents a paginated 用户 list with username, name, phone, and status', () => {
      const page = presentListPage({
        records: [
          {
            id: 2,
            username: 'zhangsan',
            name: '张三',
            phone: '13800001111',
            status: 1,
            createTime: '2026-08-01 10:00:00',
          },
        ],
        total: 1,
        pages: 1,
      })

      expect(page).toEqual({
        records: [
          {
            id: 2,
            username: 'zhangsan',
            name: '张三',
            phone: '13800001111',
            status: 1,
            statusEnabled: true,
            createTime: '2026-08-01 10:00:00',
          },
        ],
        total: 1,
        pages: 1,
      })
    })

    it('treats a missing payload as an empty page', () => {
      expect(presentListPage()).toEqual({
        records: [],
        total: 0,
        pages: 0,
      })
    })
  })

  describe('用户 API', () => {
    it('pages 用户 with GET /admin/system/sysUser/{page}/{limit}', () => {
      expect(listRequest(1, 10, { keyword: '张三' })).toEqual({
        method: 'get',
        url: '/admin/system/sysUser/1/10',
        params: { keyword: '张三' },
      })
    })

    it('loads one 用户 with GET /admin/system/sysUser/get/{id}', () => {
      expect(getRequest(2)).toEqual({
        method: 'get',
        url: '/admin/system/sysUser/get/2',
      })
    })

    it('creates a 用户 with POST /admin/system/sysUser/save', () => {
      expect(
        writeRequest({
          username: 'lisi',
          password: '123456',
          name: '李四',
          phone: '13900002222',
        }),
      ).toEqual({
        method: 'post',
        url: '/admin/system/sysUser/save',
        data: {
          username: 'lisi',
          password: '123456',
          name: '李四',
          phone: '13900002222',
          status: 1,
        },
      })
    })

    it('updates a 用户 with PUT /admin/system/sysUser/update and omits password', () => {
      expect(
        writeRequest({
          id: 2,
          username: 'zhangsan',
          password: 'should-not-send',
          name: '张三',
          phone: '13800001111',
          status: 1,
        }),
      ).toEqual({
        method: 'put',
        url: '/admin/system/sysUser/update',
        data: {
          id: 2,
          username: 'zhangsan',
          name: '张三',
          phone: '13800001111',
          status: 1,
        },
      })
    })

    it('deletes a 用户 with DELETE /admin/system/sysUser/remove/{id}', () => {
      expect(removeRequest(2)).toEqual({
        method: 'delete',
        url: '/admin/system/sysUser/remove/2',
      })
    })

    it('enables or disables a 用户 with PUT /admin/system/sysUser/updateStatus/{id}/{status}', () => {
      expect(updateStatusRequest(2, 0)).toEqual({
        method: 'put',
        url: '/admin/system/sysUser/updateStatus/2/0',
      })
    })

    it('resets a 用户 password with POST /admin/system/sysUser/{id}/resetPassword', () => {
      expect(resetPasswordRequest(2, '654321')).toEqual({
        method: 'post',
        url: '/admin/system/sysUser/2/resetPassword',
        data: { newPassword: '654321' },
      })
    })
  })

  describe('重置密码', () => {
    it('names the standalone button point and starts from an empty form', () => {
      expect(SYS_USER_PERMISSIONS.resetPassword).toBe('system:user:resetPwd')
      expect(emptyResetPasswordForm()).toEqual({ newPassword: '', confirmPassword: '' })
    })

    it('mirrors the server min 6 / max 100 password policy', () => {
      const form = emptyResetPasswordForm()
      const rules = resetPasswordRules(() => form)

      expect(messageOf(rules.newPassword[0], '')).toBe('密码不能为空')
      expect(messageOf(rules.newPassword[1], '12345')).toBe('密码长度必须在 6 到 100 位之间')
      expect(messageOf(rules.newPassword[1], '1'.repeat(101))).toBe(
        '密码长度必须在 6 到 100 位之间',
      )
      expect(messageOf(rules.newPassword[1], '123456')).toBeUndefined()
    })

    it('requires the confirmation to match the new password', () => {
      const form = emptyResetPasswordForm()
      const rules = resetPasswordRules(() => form)

      expect(messageOf(rules.confirmPassword[0], '')).toBe('确认密码不能为空')

      form.newPassword = '123456'
      expect(messageOf(rules.confirmPassword[1], '123456')).toBeUndefined()
      expect(messageOf(rules.confirmPassword[1], '654321')).toBe('两次输入的密码不一致')
      expect(messageOf(rules.confirmPassword[1], '123456')).toBeUndefined()
    })
  })

  describe('presentForm', () => {
    it('starts 新建 with an empty form', () => {
      expect(emptyForm()).toEqual({
        id: '',
        username: '',
        password: '',
        name: '',
        phone: '',
        status: 1,
      })
    })

    it('loads an existing 用户 without echoing the password', () => {
      expect(
        presentForm({
          id: 2,
          username: 'zhangsan',
          password: 'hashed',
          name: '张三',
          phone: '13800001111',
          status: 1,
        }),
      ).toEqual({
        id: 2,
        username: 'zhangsan',
        password: '',
        name: '张三',
        phone: '13800001111',
        status: 1,
      })
    })
  })

  describe('formRules', () => {
    it('mirrors the server 必填 messages and leaves phone optional', () => {
      const rules = formRules()
      expect(Object.keys(rules).sort()).toEqual(['name', 'password', 'username'])
      expect(emptyMessageOf(rules.username[0])).toBe('用户名不能为空')
      expect(emptyMessageOf(rules.password[0])).toBe('密码不能为空')
      expect(emptyMessageOf(rules.name[0])).toBe('姓名不能为空')
    })
  })
})
