import { describe, expect, it } from 'vitest'
import { emptyMessageOf } from '../support/form-rules'
import {
  POST_STATUS_OPTIONS,
  emptyForm,
  formRules,
  listRequest,
  postStatusLabel,
  presentForm,
  presentListPage,
  removeRequest,
  writeRequest,
} from '@/config/sys-post'

describe('config/sys-post', () => {
  describe('presentListPage', () => {
    it('presents a paginated 岗位 list with code, name, sort, status, and time', () => {
      expect(
        presentListPage({
          records: [
            {
              id: 4,
              postCode: 't248_clerk',
              name: '文员',
              sortValue: 1,
              status: 1,
              createTime: '2026-11-04 10:00:00',
            },
            {
              id: 5,
              postCode: 't248_auditor',
              name: '审核员',
              sortValue: 2,
              status: 0,
              createTime: '2026-11-04 10:01:00',
            },
          ],
          total: 2,
          pages: 1,
        }),
      ).toEqual({
        records: [
          {
            id: 4,
            postCode: 't248_clerk',
            name: '文员',
            sortValue: 1,
            status: 1,
            statusLabel: '正常',
            createTime: '2026-11-04 10:00:00',
          },
          {
            id: 5,
            postCode: 't248_auditor',
            name: '审核员',
            sortValue: 2,
            status: 0,
            statusLabel: '停用',
            createTime: '2026-11-04 10:01:00',
          },
        ],
        total: 2,
        pages: 1,
      })
    })
  })

  describe('岗位 API', () => {
    it('pages 岗位 with GET /admin/system/sysPost/{page}/{limit}', () => {
      expect(listRequest(1, 10, { name: 'T248' })).toEqual({
        method: 'get',
        url: '/admin/system/sysPost/1/10',
        params: { name: 'T248' },
      })
    })

    it('creates a 岗位 with POST /admin/system/sysPost/save', () => {
      expect(writeRequest({ postCode: 't248_clerk', name: '文员' })).toEqual({
        method: 'post',
        url: '/admin/system/sysPost/save',
        data: { postCode: 't248_clerk', name: '文员', sortValue: 0, status: 1 },
      })
    })

    it('updates a 岗位 with PUT /admin/system/sysPost/update and its id', () => {
      expect(
        writeRequest({ id: 4, postCode: 't248_clerk', name: '文员', sortValue: 3, status: 0 }),
      ).toEqual({
        method: 'put',
        url: '/admin/system/sysPost/update',
        data: { id: 4, postCode: 't248_clerk', name: '文员', sortValue: 3, status: 0 },
      })
    })

    it('deletes a 岗位 with DELETE /admin/system/sysPost/remove/{id}', () => {
      expect(removeRequest(4)).toEqual({
        method: 'delete',
        url: '/admin/system/sysPost/remove/4',
      })
    })
  })

  describe('presentForm', () => {
    it('starts 新建 enabled with an empty sort', () => {
      expect(emptyForm()).toEqual({
        id: '',
        postCode: '',
        name: '',
        sortValue: 0,
        status: 1,
      })
    })

    it('loads an existing 岗位 into the form', () => {
      expect(
        presentForm({
          id: 4,
          postCode: 't248_clerk',
          name: '文员',
          sortValue: 3,
          status: 0,
        }),
      ).toEqual({
        id: 4,
        postCode: 't248_clerk',
        name: '文员',
        sortValue: 3,
        status: 0,
      })
    })
  })

  describe('状态与表单校验', () => {
    it('exposes the two status labels', () => {
      expect(POST_STATUS_OPTIONS.map((option) => option.value)).toEqual([1, 0])
      expect(POST_STATUS_OPTIONS.map((option) => option.label)).toEqual(['正常', '停用'])
      expect(postStatusLabel(1)).toBe('正常')
      expect(postStatusLabel(0)).toBe('停用')
      expect(postStatusLabel(undefined)).toBe('')
    })

    it('mirrors the server 必填 messages for code and name', () => {
      const rules = formRules()
      expect(Object.keys(rules).sort()).toEqual(['name', 'postCode'])
      expect(emptyMessageOf(rules.postCode[0])).toBe('岗位编码不能为空')
      expect(emptyMessageOf(rules.name[0])).toBe('岗位名称不能为空')
    })
  })
})
