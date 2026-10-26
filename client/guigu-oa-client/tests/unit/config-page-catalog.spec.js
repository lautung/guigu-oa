import { KNOWN_CONFIG_PAGES } from '@/config-permission'
import { constantRoutes } from '@/router'
import { createMemoryHistory, createRouter } from 'vue-router'

describe('配置页目录与路由注册', () => {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: constantRoutes,
  })
  const catalogPaths = KNOWN_CONFIG_PAGES.map((page) => page.path)

  it('resolves every catalog path to a registered route', () => {
    const unresolved = catalogPaths.filter((path) => router.resolve(path).matched.length === 0)

    expect(unresolved).toEqual([])
  })

  it('keeps every hidden page parent inside the catalog', () => {
    const hiddenParentPaths = KNOWN_CONFIG_PAGES.map((page) => page.hiddenParentPath).filter(
      Boolean,
    )
    const outsideCatalog = hiddenParentPaths.filter((path) => !catalogPaths.includes(path))

    expect(outsideCatalog).toEqual([])
  })
})
