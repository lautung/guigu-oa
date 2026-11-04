import { describe, expect, it } from 'vitest'
import { emptyMessageOf } from '../support/form-rules'
import {
  DEPT_PERMISSIONS,
  DEPT_STATUS_OPTIONS,
  emptyForm,
  findTreeRequest,
  formRules,
  parentOptions,
  presentDeptTree,
  presentForm,
  removeRequest,
  writeRequest,
} from '@/config/sys-dept'

const TREE = [
  {
    id: 1,
    parentId: 0,
    ancestors: ',0,1,',
    name: '总部',
    leader: '张三',
    sortValue: 1,
    status: 1,
    createTime: '2026-01-01 10:00:00',
    children: [
      {
        id: 2,
        parentId: 1,
        ancestors: ',0,1,2,',
        name: '研发部',
        leader: '',
        sortValue: 2,
        status: 0,
        children: [{ id: 3, parentId: 2, ancestors: ',0,1,2,3,', name: '平台组' }],
      },
    ],
  },
]

describe('config/sys-dept', () => {
  describe('树投影', () => {
    it('maps the server tree recursively and keeps the wrapped ancestors path', () => {
      const tree = presentDeptTree(TREE)

      expect(tree).toHaveLength(1)
      expect(tree[0]).toMatchObject({
        id: 1,
        name: '总部',
        leader: '张三',
        ancestors: ',0,1,',
        status: 1,
      })
      expect(tree[0].children[0]).toMatchObject({
        id: 2,
        name: '研发部',
        ancestors: ',0,1,2,',
        status: 0,
        leader: '',
      })
      expect(tree[0].children[0].children[0]).toMatchObject({ id: 3, name: '平台组', children: [] })
    })

    it('fills display defaults for missing optional fields', () => {
      expect(presentDeptTree([{ id: 9, name: '空部门' }])[0]).toMatchObject({
        parentId: 0,
        ancestors: '',
        leader: '',
        sortValue: 0,
        status: 1,
        children: [],
      })
    })
  })

  describe('部门 API', () => {
    it('loads the tree with GET /admin/system/sysDept/findTree', () => {
      expect(findTreeRequest()).toEqual({
        method: 'get',
        url: '/admin/system/sysDept/findTree',
      })
    })

    it('creates a 部门 with POST /admin/system/sysDept/save', () => {
      expect(
        writeRequest({ parentId: 1, name: '研发部', leader: '李四', sortValue: 2, status: 1 }),
      ).toEqual({
        method: 'post',
        url: '/admin/system/sysDept/save',
        data: { parentId: 1, name: '研发部', leader: '李四', sortValue: 2, status: 1 },
      })
    })

    it('updates a 部门 with PUT /admin/system/sysDept/update and keeps the parent move', () => {
      expect(
        writeRequest({
          id: 2,
          parentId: 0,
          name: '研发部',
          leader: '',
          sortValue: 0,
          status: 0,
        }),
      ).toEqual({
        method: 'put',
        url: '/admin/system/sysDept/update',
        data: { id: 2, parentId: 0, name: '研发部', leader: '', sortValue: 0, status: 0 },
      })
    })

    it('deletes a 部门 with DELETE /admin/system/sysDept/remove/{id}', () => {
      expect(removeRequest(2)).toEqual({
        method: 'delete',
        url: '/admin/system/sysDept/remove/2',
      })
    })

    it('trims name and leader before writing', () => {
      expect(
        writeRequest({ parentId: 1, name: '  研发部 ', leader: ' 李四 ', sortValue: 2, status: 1 })
          .data,
      ).toEqual({ parentId: 1, name: '研发部', leader: '李四', sortValue: 2, status: 1 })
    })

    it('exposes the page button points (no :query)', () => {
      expect(DEPT_PERMISSIONS).toEqual({
        add: 'system:dept:add',
        edit: 'system:dept:edit',
        remove: 'system:dept:remove',
      })
    })

    it('exposes two status labels', () => {
      expect(DEPT_STATUS_OPTIONS).toEqual([
        { value: 1, label: '正常' },
        { value: 0, label: '停用' },
      ])
    })
  })

  describe('表单投影', () => {
    it('starts 新建 under the given parent with normal status', () => {
      expect(emptyForm()).toEqual({
        id: '',
        parentId: 0,
        name: '',
        leader: '',
        sortValue: 0,
        status: 1,
      })
      expect(emptyForm(2)).toMatchObject({ parentId: 2 })
    })

    it('loads an existing 部门 into the form', () => {
      expect(presentForm(TREE[0].children[0])).toEqual({
        id: 2,
        parentId: 1,
        name: '研发部',
        leader: '',
        sortValue: 2,
        status: 0,
      })
    })
  })

  describe('formRules', () => {
    it('mirrors the server 必填 message for the department name', () => {
      const rules = formRules()
      expect(Object.keys(rules)).toEqual(['name'])
      expect(emptyMessageOf(rules.name[0])).toBe('部门名称不能为空')
    })
  })

  describe('父级选项', () => {
    it('lists the root option and every node depth-first', () => {
      expect(parentOptions(TREE)).toEqual([
        { id: 0, name: '顶级部门', depth: 0 },
        { id: 1, name: '总部', depth: 1 },
        { id: 2, name: '研发部', depth: 2 },
        { id: 3, name: '平台组', depth: 3 },
      ])
    })

    it('excludes the edited node and its whole subtree to prevent a cycle', () => {
      expect(parentOptions(TREE, 2).map((option) => option.id)).toEqual([0, 1])
      expect(parentOptions(TREE, 1).map((option) => option.id)).toEqual([0])
    })
  })
})
