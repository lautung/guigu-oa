import { describe, expect, it, vi } from 'vitest'
import { getCurrentUserRequest, presentProfile, readProfile } from '@/work/profile'

describe('work/profile', () => {
  describe('presentProfile', () => {
    it('shows only 姓名 and 手机号 from getCurrentUser', () => {
      expect(
        presentProfile({
          name: '张三',
          phone: '13800001111',
          deptName: '研发部',
          postName: '工程师',
          username: 'zhangsan',
        }),
      ).toEqual({
        name: '张三',
        phone: '13800001111',
      })
    })

    it('treats a missing payload as empty 基本信息', () => {
      expect(presentProfile()).toEqual({
        name: '',
        phone: '',
      })
    })
  })

  describe('getCurrentUserRequest', () => {
    it('loads 基本信息 with GET /admin/process/getCurrentUser', () => {
      expect(getCurrentUserRequest()).toEqual({
        method: 'get',
        url: '/admin/process/getCurrentUser',
      })
    })
  })

  describe('readProfile', () => {
    it('loads 基本信息 through the request module and presents only 姓名/手机号', async () => {
      const request = vi.fn(async () => ({
        code: 200,
        data: { name: '张三', phone: '13800001111', deptName: '研发部' },
      }))

      expect(await readProfile(request)).toEqual({ name: '张三', phone: '13800001111' })
      expect(request).toHaveBeenCalledWith({
        method: 'get',
        url: '/admin/process/getCurrentUser',
      })
    })

    it('treats a missing payload as empty and passes failures through', async () => {
      expect(await readProfile(async () => ({ code: 200, data: null }))).toEqual({
        name: '',
        phone: '',
      })
      await expect(
        readProfile(async () => {
          throw new Error('boom')
        }),
      ).rejects.toThrow('boom')
    })
  })
})
