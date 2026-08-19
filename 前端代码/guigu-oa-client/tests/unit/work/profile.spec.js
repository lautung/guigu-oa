import {
  getCurrentUserRequest,
  presentProfile
} from '@/work/profile'

describe('work/profile', () => {
  describe('presentProfile', () => {
    it('shows only 姓名 and 手机号 from getCurrentUser', () => {
      expect(presentProfile({
        name: '张三',
        phone: '13800001111',
        deptName: '研发部',
        postName: '工程师',
        username: 'zhangsan'
      })).toEqual({
        name: '张三',
        phone: '13800001111'
      })
    })

    it('treats a missing payload as empty 基本信息', () => {
      expect(presentProfile()).toEqual({
        name: '',
        phone: ''
      })
    })
  })

  describe('getCurrentUserRequest', () => {
    it('loads 基本信息 with GET /admin/process/getCurrentUser', () => {
      expect(getCurrentUserRequest()).toEqual({
        method: 'get',
        url: '/admin/process/getCurrentUser'
      })
    })
  })
})
