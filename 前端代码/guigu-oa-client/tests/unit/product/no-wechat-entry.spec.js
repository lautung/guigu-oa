import { constantRoutes } from '@/router'
import { filterConfigMenus } from '@/navigation/access-policy'

function collectPaths(routes, prefix = '') {
  return routes.reduce((paths, route) => {
    const current = route.path && route.path.startsWith('/')
      ? route.path
      : `${prefix.replace(/\/$/, '')}/${route.path || ''}`
    paths.push(current)
    if (route.children) {
      paths.push(...collectPaths(route.children, current))
    }
    return paths
  }, [])
}

describe('unified client has no wechat entry', () => {
  it('does not register /wechat routes', () => {
    const paths = collectPaths(constantRoutes)
    expect(paths.some(path => /wechat/i.test(path))).toBe(false)
  })

  it('does not surface 公众号菜单 from sys_menu', () => {
    const menus = filterConfigMenus([
      {
        path: '/wechat',
        meta: { title: '公众号菜单' },
        children: [{ path: 'menu', component: 'wechat/menu/list', meta: { title: '菜单列表' } }]
      }
    ])
    expect(menus).toEqual([])
  })
})
