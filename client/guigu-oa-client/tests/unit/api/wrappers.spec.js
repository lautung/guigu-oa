import { beforeEach, describe, expect, it, vi } from 'vitest'

const { requestMock } = vi.hoisted(() => ({ requestMock: vi.fn() }))

vi.mock('@/request/request', () => ({ default: requestMock }))

import { findCarbonCopyRecipients } from '@/api/carbon-copy'
import {
  findAll as findAllProcessTypes,
  getById as getProcessType,
  removeById as removeProcessType,
  saveOrUpdate as saveProcessType,
} from '@/api/processType'
import {
  doAssign,
  findNodes,
  getByIdForAssign,
  removeById as removeMenu,
  saveOrUpdate as saveMenu,
} from '@/api/sysMenu'
import {
  getById as getRole,
  removeById as removeRole,
  saveOrUpdate as saveRole,
} from '@/api/sysRole'
import {
  getById as getUser,
  removeById as removeUser,
  saveOrUpdate as saveUser,
  updateStatus,
} from '@/api/sysUser'
import { getInfo, login } from '@/api/user'
import {
  getRequest as processTypeGetRequest,
  findAllRequest as processTypeFindAllRequest,
  removeRequest as processTypeRemoveRequest,
  writeRequest as processTypeWriteRequest,
} from '@/config/process-type'
import {
  doAssignRequest,
  findNodesRequest,
  removeRequest as menuRemoveRequest,
  toAssignRequest,
  writeRequest as menuWriteRequest,
} from '@/config/sys-menu'
import {
  getRequest as roleGetRequest,
  removeRequest as roleRemoveRequest,
  writeRequest as roleWriteRequest,
} from '@/config/sys-role'
import {
  getRequest as userGetRequest,
  removeRequest as userRemoveRequest,
  updateStatusRequest,
  writeRequest as userWriteRequest,
} from '@/config/sys-user'

describe('api wrappers', () => {
  beforeEach(() => {
    requestMock.mockReset()
    requestMock.mockImplementation(async (config) => ({ code: 200, data: config }))
  })

  it('sends 用户 requests through the configured client', async () => {
    await getUser(7)
    await saveUser({ id: 7, username: 'zhangsan' })
    await removeUser(7)
    await updateStatus(7, 0)

    expect(requestMock.mock.calls.map(([config]) => config)).toEqual([
      userGetRequest(7),
      userWriteRequest({ id: 7, username: 'zhangsan' }),
      userRemoveRequest(7),
      updateStatusRequest(7, 0),
    ])
  })

  it('sends 角色 requests through the configured client', async () => {
    await getRole(3)
    await saveRole({ id: 3, roleName: '审批人' })
    await removeRole(3)

    expect(requestMock.mock.calls.map(([config]) => config)).toEqual([
      roleGetRequest(3),
      roleWriteRequest({ id: 3, roleName: '审批人' }),
      roleRemoveRequest(3),
    ])
  })

  it('sends 菜单 requests through the configured client', async () => {
    await findNodes()
    await getByIdForAssign(3)
    await doAssign(3, [1, 2])
    await saveMenu({ id: '', name: '菜单' })
    await removeMenu(9)

    expect(requestMock.mock.calls.map(([config]) => config)).toEqual([
      findNodesRequest(),
      toAssignRequest(3),
      doAssignRequest(3, [1, 2]),
      menuWriteRequest({ id: '', name: '菜单' }),
      menuRemoveRequest(9),
    ])
  })

  it('sends 审批类型 requests through the configured client', async () => {
    await getProcessType(2)
    await saveProcessType({ id: 2, name: '请假' })
    await removeProcessType(2)
    await findAllProcessTypes()

    expect(requestMock.mock.calls.map(([config]) => config)).toEqual([
      processTypeGetRequest(2),
      processTypeWriteRequest({ id: 2, name: '请假' }),
      processTypeRemoveRequest(2),
      processTypeFindAllRequest(),
    ])
  })

  it('sends login, info, and carbon-copy requests', async () => {
    await login({ username: 'alice', password: '111111' })
    await getInfo()
    await findCarbonCopyRecipients()

    expect(requestMock.mock.calls.map(([config]) => config)).toEqual([
      {
        url: '/admin/system/index/login',
        method: 'post',
        data: { username: 'alice', password: '111111' },
      },
      { url: '/admin/system/index/info', method: 'get' },
      { url: '/admin/process/carbonCopyRecipients', method: 'get' },
    ])
  })
})
