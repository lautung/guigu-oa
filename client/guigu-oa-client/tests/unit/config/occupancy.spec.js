import { describe, expect, it } from 'vitest'
import { configPermission } from '@/config/permission'
import { getDefaultPostLoginRoute } from '@/navigation/constants'

const USER_PAGE = [
  { path: '/system', title: '组织与权限', type: 0, sort: 1 },
  { path: '/system/sysUser', parentPath: '/system', title: '用户管理', type: 1, sort: 1 },
]

const ROLE_PAGE = [
  { path: '/system', title: '组织与权限', type: 0, sort: 1 },
  { path: '/system/sysRole', parentPath: '/system', title: '角色管理', type: 1, sort: 2 },
  {
    path: '/system/assignAuth',
    parentPath: '/system/sysRole',
    title: '菜单授权',
    type: 1,
    hidden: true,
    sort: 3,
  },
]

function occupancyOf(path, hasToken, menus = []) {
  return configPermission(menus).occupancyOf({ path, hasToken })
}

describe('后台页占用', () => {
  it('allows pages outside the 配置 group regardless of grant', () => {
    const menus = USER_PAGE

    expect(occupancyOf('/workbench', true, menus)).toEqual({ action: 'allow' })
    expect(occupancyOf('/work/pending', true, menus)).toEqual({ action: 'allow' })
    expect(occupancyOf('/notice', true, menus)).toEqual({ action: 'allow' })
    expect(occupancyOf('/login', false, menus)).toEqual({ action: 'allow' })
  })

  it('allows a tokenless 配置 path because the auth guard owns the anonymous redirect', () => {
    expect(occupancyOf('/system/sysUser', false, [])).toEqual({ action: 'allow' })
    expect(occupancyOf('/system/sysUser')).toEqual({ action: 'allow' })
  })

  it('allows a granted 配置 page and normalizes a trailing slash', () => {
    expect(occupancyOf('/system/sysUser', true, USER_PAGE)).toEqual({ action: 'allow' })
    expect(occupancyOf('/system/sysUser/', true, USER_PAGE)).toEqual({ action: 'allow' })
  })

  it('allows a hidden page through its granted visible parent', () => {
    expect(occupancyOf('/system/assignAuth', true, ROLE_PAGE)).toEqual({ action: 'allow' })
    expect(occupancyOf('/system/assignAuth/9', true, ROLE_PAGE)).toEqual({ action: 'allow' })
  })

  it('answers from the current grant on every call', () => {
    const menus = { current: USER_PAGE }
    const read = () => occupancyOf('/system/sysUser', true, menus.current)

    expect(read()).toEqual({ action: 'allow' })
    menus.current = ROLE_PAGE
    expect(read()).toEqual({ action: 'redirect', to: getDefaultPostLoginRoute() })
  })

  it('keeps a granted 配置 详情 path open and closes it with its list page', () => {
    const processPage = [
      { path: '/system/processMgr', title: '审批管理', type: 0, sort: 1 },
      {
        path: '/system/processMgr/process',
        parentPath: '/system/processMgr',
        title: '审批实例查询',
        type: 1,
        sort: 1,
      },
    ]

    expect(occupancyOf('/system/processMgr/process/9', true, processPage)).toEqual({
      action: 'allow',
    })
    expect(occupancyOf('/system/processMgr/process/9', true, USER_PAGE)).toEqual({
      action: 'redirect',
      to: getDefaultPostLoginRoute(),
    })
  })

  it('redirects an ungranted 配置 page to the default landing', () => {
    const landing = { action: 'redirect', to: getDefaultPostLoginRoute() }

    expect(occupancyOf('/system/sysRole', true, USER_PAGE)).toEqual(landing)
    expect(occupancyOf('/system/sysUser', true, [])).toEqual(landing)
    expect(
      occupancyOf('/system/assignAuth', true, [
        { path: '/system', title: '组织与权限', type: 0, sort: 1 },
        { path: '/system/sysRole', parentPath: '/system', title: '角色管理', type: 1, sort: 2 },
      ]),
    ).toEqual(landing)
  })
})
