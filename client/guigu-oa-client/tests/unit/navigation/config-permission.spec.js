import { buildSidebarTree } from '@/navigation'
import { DESKTOP_CONFIG_HINT } from '@/navigation/constants'

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

describe('配置权限驱动的侧栏', () => {
  it('always includes the six 办事 built-ins and omits the 配置 group without 配置权限', () => {
    const tree = buildSidebarTree({ menus: [], viewportWidth: 1200 })

    expect(tree.map(group => group.key)).toEqual(['work'])
    expect(tree[0].items).toEqual([
      { path: '/work/start', title: '发起审批' },
      { path: '/work/pending', title: '待处理' },
      { path: '/work/processed', title: '已处理' },
      { path: '/work/started', title: '已发起' },
      { path: '/work/copied', title: '抄送我的' },
      { path: '/work/profile', title: '基本信息' }
    ])
  })

  it('adds the 配置 group with the granted menus when 配置权限 exists', () => {
    const tree = buildSidebarTree({ menus: ONLY_USER_MENUS, viewportWidth: 1200 })
    const configGroup = tree.find(group => group.key === 'config')

    expect(configGroup.items).toEqual([
      {
        path: '/system',
        title: '组织与权限',
        children: [{ path: '/system/sysUser', title: '用户管理' }]
      }
    ])
  })

  it('collapses the 配置 group and shows the desktop hint on mobile', () => {
    const configGroup = buildSidebarTree({ menus: ONLY_USER_MENUS, viewportWidth: 375 })
      .find(group => group.key === 'config')

    expect(configGroup.collapsed).toBe(true)
    expect(configGroup.hint).toBe(DESKTOP_CONFIG_HINT)
  })

  it('keeps the 配置 group expanded without a hint on desktop', () => {
    const configGroup = buildSidebarTree({ menus: ONLY_USER_MENUS, viewportWidth: 1200 })
      .find(group => group.key === 'config')

    expect(configGroup.collapsed).toBe(false)
    expect(configGroup.hint).toBeNull()
  })
})
