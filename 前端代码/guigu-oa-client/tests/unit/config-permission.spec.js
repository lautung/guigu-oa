import { configPermission } from '@/config-permission'

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

describe('配置权限', () => {
  it('has no 配置权限 when the menu tree is empty', () => {
    const permission = configPermission([])

    expect(permission.hasConfigPermission).toBe(false)
  })

  it('has 配置权限 when at least one 配置类菜单 is granted', () => {
    const permission = configPermission(ONLY_USER_MENUS)

    expect(permission.hasConfigPermission).toBe(true)
  })

  it('has no 配置权限 when only non-配置 menus are granted', () => {
    expect(configPermission([
      { path: 'sysDept', component: 'system/sysDept/list', meta: { title: '部门管理' } }
    ]).hasConfigPermission).toBe(false)

    expect(configPermission([
      { path: '/dashboard', component: 'dashboard/index', meta: { title: 'Dashboard' } },
      {
        path: '/wechat',
        component: 'Layout',
        meta: { title: '公众号菜单' },
        children: [
          { path: 'menu', component: 'wechat/menu/list', meta: { title: '菜单列表' } }
        ]
      }
    ]).hasConfigPermission).toBe(false)
  })

  it('returns granted 配置类菜单 as 组织与权限 / 用户管理 when only 用户管理 is granted', () => {
    const permission = configPermission(ONLY_USER_MENUS)

    expect(flattenTitles(permission.grantedConfigMenus)).toEqual(['组织与权限', '用户管理'])
  })

  it('drops 部门 / 岗位 / 日志 / Dashboard / 公众号 / 在线流程设计器 from granted 配置类菜单', () => {
    const permission = configPermission([
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
        path: '/processSet',
        component: 'Layout',
        meta: { title: '审批设置' },
        children: [
          { path: 'processTemplate', component: 'processSet/processTemplate/list', meta: { title: '审批模板' } },
          { path: 'onlineProcessSet', component: 'processSet/processTemplate/onlineProcessSet', meta: { title: '在线流程设计器' } }
        ]
      },
      { path: '/dashboard', component: 'dashboard/index', meta: { title: 'Dashboard' } },
      {
        path: '/wechat',
        component: 'Layout',
        meta: { title: '公众号菜单' },
        children: [
          { path: 'menu', component: 'wechat/menu/list', meta: { title: '菜单列表' } }
        ]
      }
    ])

    expect(flattenTitles(permission.grantedConfigMenus)).toEqual([
      '组织与权限',
      '用户管理',
      '审批设置',
      '审批模板'
    ])
  })

  it('does not list hidden 配置 pages among granted 配置类菜单', () => {
    const permission = configPermission([
      {
        path: '/system',
        component: 'Layout',
        meta: { title: '组织与权限' },
        children: [
          { path: 'sysRole', component: 'system/sysRole/list', meta: { title: '角色管理' } },
          { path: 'assignAuth', component: 'system/sysRole/assignAuth', meta: { title: '菜单授权' }, hidden: true }
        ]
      }
    ])

    expect(flattenTitles(permission.grantedConfigMenus)).toEqual(['组织与权限', '角色管理'])
  })

  it('has no 配置权限 when 组织与权限 only contains excluded menus', () => {
    const permission = configPermission([
      {
        path: '/system',
        component: 'Layout',
        meta: { title: '组织与权限' },
        children: [
          { path: 'sysDept', component: 'system/sysDept/list', meta: { title: '部门管理' } }
        ]
      }
    ])

    expect(permission.hasConfigPermission).toBe(false)
    expect(permission.grantedConfigMenus).toEqual([])
  })

  it('can occupy 用户管理 and its parent grouping when only 用户管理 is granted', () => {
    const permission = configPermission(ONLY_USER_MENUS)

    expect(permission.canOccupy('/system/sysUser')).toBe(true)
    expect(permission.canOccupy('/system')).toBe(true)
  })

  it('cannot occupy 角色管理 when it was not granted', () => {
    const permission = configPermission(ONLY_USER_MENUS)

    expect(permission.canOccupy('/system/sysRole')).toBe(false)
  })

  it('can occupy hidden 菜单授权 when 角色管理 is granted', () => {
    const permission = configPermission([
      {
        path: '/system',
        meta: { title: '组织与权限' },
        children: [
          { path: 'sysRole', component: 'system/sysRole/list', meta: { title: '角色管理' } }
        ]
      }
    ])

    expect(permission.canOccupy('/system/assignAuth')).toBe(true)
  })

  it('cannot occupy 用户管理 when only a hidden 菜单授权 node is present', () => {
    const permission = configPermission([
      {
        path: '/system',
        meta: { title: '组织与权限' },
        children: [
          { path: 'assignAuth', component: 'system/sysRole/assignAuth', meta: { title: '菜单授权' }, hidden: true }
        ]
      }
    ])

    expect(permission.hasConfigPermission).toBe(false)
    expect(permission.canOccupy('/system/sysUser')).toBe(false)
    expect(permission.canOccupy('/system/assignAuth')).toBe(false)
  })

  it('can occupy the 审批模板 wizard when 审批模板 is granted', () => {
    const permission = configPermission([
      {
        path: '/processSet',
        meta: { title: '审批设置' },
        children: [
          { path: 'processTemplate', component: 'processSet/processTemplate/list', meta: { title: '审批模板' } }
        ]
      }
    ])

    expect(permission.canOccupy('/processSet/templateSet')).toBe(true)
  })

  it('cannot occupy the 审批模板 wizard when only 审批类型 is granted', () => {
    const permission = configPermission([
      {
        path: '/processSet',
        meta: { title: '审批设置' },
        children: [
          { path: 'processType', component: 'processSet/processType/list', meta: { title: '审批类型' } }
        ]
      }
    ])

    expect(permission.canOccupy('/processSet/templateSet')).toBe(false)
  })

  it('can occupy 审批实例详情 when 审批实例查询 is granted', () => {
    const permission = configPermission([
      {
        path: '/processMgr',
        meta: { title: '审批查询' },
        children: [
          { path: 'process', component: 'processMgr/process/list', meta: { title: '审批实例查询' } }
        ]
      }
    ])

    expect(permission.canOccupy('/processMgr/process/9')).toBe(true)
  })

  it('ignores button nodes when answering the three questions', () => {
    const permission = configPermission([
      {
        path: '/system',
        meta: { title: '组织与权限' },
        children: [
          {
            path: 'sysUser',
            component: 'system/sysUser/list',
            meta: { title: '用户管理' },
            children: [
              { path: 'btn', perms: 'bnt.sysUser.add', type: 2, meta: { title: '添加' } }
            ]
          }
        ]
      }
    ])

    expect(permission.hasConfigPermission).toBe(true)
    expect(flattenTitles(permission.grantedConfigMenus)).toEqual(['组织与权限', '用户管理'])
    expect(permission.canOccupy('/system/sysUser')).toBe(true)
  })

  it('answers from the current 配置类菜单 tree, not a previous grant', () => {
    expect(flattenTitles(configPermission(ONLY_USER_MENUS).grantedConfigMenus)).toEqual([
      '组织与权限',
      '用户管理'
    ])

    const after = configPermission([
      {
        path: '/system',
        meta: { title: '组织与权限' },
        children: [
          { path: 'sysRole', component: 'system/sysRole/list', meta: { title: '角色管理' } }
        ]
      }
    ])
    expect(flattenTitles(after.grantedConfigMenus)).toEqual(['组织与权限', '角色管理'])
    expect(after.canOccupy('/system/sysUser')).toBe(false)
    expect(after.canOccupy('/system/sysRole')).toBe(true)
  })

  it('does not treat 办事 menus as 配置权限', () => {
    const permission = configPermission([
      { path: '/work/pending', meta: { title: '待处理' } }
    ])

    expect(permission.hasConfigPermission).toBe(false)
    expect(permission.canOccupy('/work/pending')).toBe(false)
  })
})

function flattenTitles(items = []) {
  return items.reduce((titles, item) => {
    titles.push(item.title)
    if (item.children) {
      titles.push(...flattenTitles(item.children))
    }
    return titles
  }, [])
}
