import {
  ROUTES,
  WORK_BUILTIN_ITEMS,
  buildSidebarTree,
  resolveNavigationGuard
} from '@/navigation/access-policy'

const ONLY_USER_MENUS = [
  {
    path: '/system',
    component: 'Layout',
    meta: { title: '组织与权限' },
    children: [
      { path: 'sysUser', component: 'system/sysUser/list', meta: { title: '用户管理' } }
    ]
  }
]

const ROLE_MENUS_AFTER_ASSIGN = [
  {
    path: '/system',
    component: 'Layout',
    meta: { title: '组织与权限' },
    children: [
      { path: 'sysRole', component: 'system/sysRole/list', meta: { title: '角色管理' } },
      { path: 'assignAuth', component: 'system/sysRole/assignAuth', meta: { title: '菜单授权' }, hidden: true }
    ]
  }
]

const MIXED_WITH_EXCLUDED = [
  {
    path: '/system',
    component: 'Layout',
    meta: { title: '组织与权限' },
    children: [
      { path: 'sysUser', component: 'system/sysUser/list', meta: { title: '用户管理' } },
      { path: 'sysDept', component: 'system/sysDept/list', meta: { title: '部门管理' } },
      { path: 'sysPost', component: 'system/sysPost/list', meta: { title: '岗位管理' } },
      { path: 'sysOperLog', component: 'system/sysOperLog/list', meta: { title: '操作日志' } }
    ]
  },
  {
    path: '/dashboard',
    component: 'dashboard/index',
    meta: { title: 'Dashboard' }
  }
]

function flattenTitles(items = []) {
  return items.reduce((titles, item) => {
    titles.push(item.title)
    if (item.children) {
      titles.push(...flattenTitles(item.children))
    }
    return titles
  }, [])
}

describe('配置权限驱动的侧栏与路由', () => {
  it('always keeps 办事 built-in entries when 配置 menus are partial', () => {
    const tree = buildSidebarTree({ menus: ONLY_USER_MENUS, viewportWidth: 1200 })
    expect(tree[0].items.map(item => item.title)).toEqual(
      WORK_BUILTIN_ITEMS.map(item => item.title)
    )
  })

  it('shows only the granted 配置 menus, not the full static tree', () => {
    const tree = buildSidebarTree({ menus: ONLY_USER_MENUS, viewportWidth: 1200 })
    const configGroup = tree.find(group => group.key === 'config')
    expect(flattenTitles(configGroup.items)).toEqual(['组织与权限', '用户管理'])
  })

  it('hides the 配置 group when the user has no 配置权限', () => {
    const tree = buildSidebarTree({ menus: [], viewportWidth: 1200 })
    expect(tree.some(group => group.key === 'config')).toBe(false)
  })

  it('drops 部门 / 岗位 / 日志 / Dashboard from the 配置 group', () => {
    const tree = buildSidebarTree({ menus: MIXED_WITH_EXCLUDED, viewportWidth: 1200 })
    const titles = flattenTitles(tree.find(group => group.key === 'config').items)
    expect(titles).toEqual(['组织与权限', '用户管理'])
    expect(titles).not.toContain('部门管理')
    expect(titles).not.toContain('岗位管理')
    expect(titles).not.toContain('操作日志')
    expect(titles).not.toContain('Dashboard')
  })

  it('blocks 配置 routes the role was not granted', () => {
    expect(resolveNavigationGuard({
      path: '/processSet/processType',
      hasToken: true,
      viewportWidth: 1200,
      menus: ONLY_USER_MENUS
    })).toEqual({
      action: 'redirect',
      to: ROUTES.PENDING
    })

    expect(resolveNavigationGuard({
      path: '/system/sysRole',
      hasToken: true,
      viewportWidth: 1200,
      menus: ONLY_USER_MENUS
    })).toEqual({
      action: 'redirect',
      to: ROUTES.PENDING
    })
  })

  it('allows granted 配置 routes and the parent directory', () => {
    expect(resolveNavigationGuard({
      path: '/system/sysUser',
      hasToken: true,
      viewportWidth: 1200,
      menus: ONLY_USER_MENUS
    })).toEqual({ action: 'allow' })

    expect(resolveNavigationGuard({
      path: '/system',
      hasToken: true,
      viewportWidth: 1200,
      menus: ONLY_USER_MENUS
    })).toEqual({ action: 'allow' })
  })

  it('allows hidden 菜单授权 after 角色管理 is granted', () => {
    expect(resolveNavigationGuard({
      path: '/system/assignAuth',
      hasToken: true,
      viewportWidth: 1200,
      menus: ROLE_MENUS_AFTER_ASSIGN
    })).toEqual({ action: 'allow' })
  })

  it('allows hidden 菜单授权 when only 角色管理 leaf is granted', () => {
    expect(resolveNavigationGuard({
      path: '/system/assignAuth',
      hasToken: true,
      viewportWidth: 1200,
      menus: [{
        path: '/system',
        meta: { title: '组织与权限' },
        children: [
          { path: 'sysRole', component: 'system/sysRole/list', meta: { title: '角色管理' } }
        ]
      }]
    })).toEqual({ action: 'allow' })
  })

  it('does not treat a hidden-only directory as granting every sibling 配置 route', () => {
    expect(resolveNavigationGuard({
      path: '/system/sysUser',
      hasToken: true,
      viewportWidth: 1200,
      menus: [{
        path: '/system',
        meta: { title: '组织与权限' },
        children: [
          { path: 'assignAuth', component: 'system/sysRole/assignAuth', meta: { title: '菜单授权' }, hidden: true }
        ]
      }]
    })).toEqual({
      action: 'redirect',
      to: ROUTES.PENDING
    })
  })

  it('allows 审批实例详情 under a granted 审批查询 leaf', () => {
    expect(resolveNavigationGuard({
      path: '/processMgr/process/9',
      hasToken: true,
      viewportWidth: 1200,
      menus: [{
        path: '/processMgr',
        meta: { title: '审批查询' },
        children: [
          { path: 'process', component: 'processMgr/process/list', meta: { title: '审批实例查询' } }
        ]
      }]
    })).toEqual({ action: 'allow' })
  })

  it('changes 配置 sidebar when routers change after doAssign and 重新进入', () => {
    const before = flattenTitles(
      buildSidebarTree({ menus: ONLY_USER_MENUS, viewportWidth: 1200 })
        .find(group => group.key === 'config').items
    )
    expect(before).toEqual(['组织与权限', '用户管理'])

    const after = flattenTitles(
      buildSidebarTree({ menus: ROLE_MENUS_AFTER_ASSIGN, viewportWidth: 1200 })
        .find(group => group.key === 'config').items
    )
    expect(after).toEqual(['组织与权限', '角色管理'])
    expect(after).not.toContain('用户管理')
  })
})
