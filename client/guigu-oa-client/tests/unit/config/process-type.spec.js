import {
  emptyForm,
  findAllRequest,
  getRequest,
  listRequest,
  presentForm,
  presentListPage,
  removeRequest,
  writeRequest,
} from '@/config/process-type'

describe('config/process-type', () => {
  describe('presentListPage', () => {
    it('presents a paginated 审批类型 list with name, description, and times', () => {
      const page = presentListPage({
        records: [
          {
            id: 1,
            name: '请假',
            description: '员工请假',
            createTime: '2026-08-01 10:00:00',
            updateTime: '2026-08-02 11:00:00',
          },
        ],
        total: 1,
        pages: 1,
      })

      expect(page).toEqual({
        records: [
          {
            id: 1,
            name: '请假',
            description: '员工请假',
            createTime: '2026-08-01 10:00:00',
            updateTime: '2026-08-02 11:00:00',
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

  describe('writeRequest', () => {
    it('creates a 审批类型 with POST /admin/process/processType/save', () => {
      expect(writeRequest({ name: '请假', description: '员工请假' })).toEqual({
        method: 'post',
        url: '/admin/process/processType/save',
        data: { name: '请假', description: '员工请假' },
      })
    })

    it('updates a 审批类型 with PUT /admin/process/processType/update', () => {
      expect(writeRequest({ id: 1, name: '加班', description: '加班申请' })).toEqual({
        method: 'put',
        url: '/admin/process/processType/update',
        data: { id: 1, name: '加班', description: '加班申请' },
      })
    })

    it('treats a blank form id as create, matching 新建 dialog', () => {
      expect(writeRequest({ id: '', name: '外出', description: '外出申请' }).method).toBe('post')
    })
  })

  describe('审批类型 API', () => {
    it('pages 审批类型 with GET /admin/process/processType/{page}/{limit}', () => {
      expect(listRequest(1, 10)).toEqual({
        method: 'get',
        url: '/admin/process/processType/1/10',
      })
    })

    it('loads one 审批类型 with GET /admin/process/processType/get/{id}', () => {
      expect(getRequest(1)).toEqual({
        method: 'get',
        url: '/admin/process/processType/get/1',
      })
    })

    it('deletes a 审批类型 with DELETE /admin/process/processType/remove/{id}', () => {
      expect(removeRequest(1)).toEqual({
        method: 'delete',
        url: '/admin/process/processType/remove/1',
      })
    })

    it('lists all 审批类型 with GET /admin/process/processType/findAll', () => {
      expect(findAllRequest()).toEqual({
        method: 'get',
        url: '/admin/process/processType/findAll',
      })
    })
  })

  describe('form', () => {
    it('starts 新建 with a blank name and description', () => {
      expect(emptyForm()).toEqual({
        id: '',
        name: '',
        description: '',
      })
    })

    it('loads 编辑 from a getById payload', () => {
      expect(
        presentForm({
          id: 1,
          name: '请假',
          description: '员工请假',
          createTime: '2026-08-01 10:00:00',
        }),
      ).toEqual({
        id: 1,
        name: '请假',
        description: '员工请假',
      })
    })
  })
})
