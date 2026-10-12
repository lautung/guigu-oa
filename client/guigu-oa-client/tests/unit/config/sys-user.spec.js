import {
  emptyForm,
  getRequest,
  listRequest,
  presentForm,
  presentListPage,
  removeRequest,
  updateStatusRequest,
  writeRequest
} from '@/config/sys-user'

describe('config/sys-user', () => {
  describe('presentListPage', () => {
    it('presents a paginated 用户 list with username, name, phone, and status', () => {
      const page = presentListPage({
        records: [{
          id: 2,
          username: 'zhangsan',
          name: '张三',
          phone: '13800001111',
          status: 1,
          createTime: '2026-08-01 10:00:00'
        }],
        total: 1,
        pages: 1
      })

      expect(page).toEqual({
        records: [{
          id: 2,
          username: 'zhangsan',
          name: '张三',
          phone: '13800001111',
          status: 1,
          statusEnabled: true,
          createTime: '2026-08-01 10:00:00'
        }],
        total: 1,
        pages: 1
      })
    })

    it('treats a missing payload as an empty page', () => {
      expect(presentListPage()).toEqual({
        records: [],
        total: 0,
        pages: 0
      })
    })
  })

  describe('用户 API', () => {
    it('pages 用户 with GET /admin/system/sysUser/{page}/{limit}', () => {
      expect(listRequest(1, 10, { keyword: '张三' })).toEqual({
        method: 'get',
        url: '/admin/system/sysUser/1/10',
        params: { keyword: '张三' }
      })
    })

    it('loads one 用户 with GET /admin/system/sysUser/get/{id}', () => {
      expect(getRequest(2)).toEqual({
        method: 'get',
        url: '/admin/system/sysUser/get/2'
      })
    })

    it('creates a 用户 with POST /admin/system/sysUser/save', () => {
      expect(writeRequest({
        username: 'lisi',
        password: '123456',
        name: '李四',
        phone: '13900002222'
      })).toEqual({
        method: 'post',
        url: '/admin/system/sysUser/save',
        data: {
          username: 'lisi',
          password: '123456',
          name: '李四',
          phone: '13900002222',
          status: 1
        }
      })
    })

    it('updates a 用户 with PUT /admin/system/sysUser/update and omits password', () => {
      expect(writeRequest({
        id: 2,
        username: 'zhangsan',
        password: 'should-not-send',
        name: '张三',
        phone: '13800001111',
        status: 1
      })).toEqual({
        method: 'put',
        url: '/admin/system/sysUser/update',
        data: {
          id: 2,
          username: 'zhangsan',
          name: '张三',
          phone: '13800001111',
          status: 1
        }
      })
    })

    it('deletes a 用户 with DELETE /admin/system/sysUser/remove/{id}', () => {
      expect(removeRequest(2)).toEqual({
        method: 'delete',
        url: '/admin/system/sysUser/remove/2'
      })
    })

    it('enables or disables a 用户 with GET /admin/system/sysUser/updateStatus/{id}/{status}', () => {
      expect(updateStatusRequest(2, 0)).toEqual({
        method: 'get',
        url: '/admin/system/sysUser/updateStatus/2/0'
      })
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
        status: 1
      })
    })

    it('loads an existing 用户 without echoing the password', () => {
      expect(presentForm({
        id: 2,
        username: 'zhangsan',
        password: 'hashed',
        name: '张三',
        phone: '13800001111',
        status: 1
      })).toEqual({
        id: 2,
        username: 'zhangsan',
        password: '',
        name: '张三',
        phone: '13800001111',
        status: 1
      })
    })
  })
})
