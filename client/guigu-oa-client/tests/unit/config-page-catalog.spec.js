import { KNOWN_CONFIG_PAGES } from '@/config-permission'
import { constantRoutes } from '@/router'

function collectPaths(routes, prefix = '') {
  return routes.reduce((paths, route) => {
    const current =
      route.path && route.path.startsWith('/')
        ? route.path
        : `${prefix.replace(/\/$/, '')}/${route.path || ''}`
    paths.push(current)
    if (route.children) {
      paths.push(...collectPaths(route.children, current))
    }
    return paths
  }, [])
}

describe('配置页目录与路由注册', () => {
  const registeredPaths = collectPaths(constantRoutes)
  const catalogPaths = KNOWN_CONFIG_PAGES.map((page) => page.path)

  it('resolves every catalog path to a registered route', () => {
    const missing = catalogPaths.filter((path) => !registeredPaths.includes(path))

    expect(missing).toEqual([])
  })

  it('keeps every hidden page parent inside the catalog', () => {
    const hiddenParentPaths = KNOWN_CONFIG_PAGES.map((page) => page.hiddenParentPath).filter(
      Boolean,
    )
    const outsideCatalog = hiddenParentPaths.filter((path) => !catalogPaths.includes(path))

    expect(outsideCatalog).toEqual([])
  })
})
