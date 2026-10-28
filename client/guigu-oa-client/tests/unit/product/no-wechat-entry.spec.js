import { describe, expect, it } from 'vitest'
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

describe('unified client has no wechat entry', () => {
  it('does not register /wechat routes', () => {
    const paths = collectPaths(constantRoutes)
    expect(paths.some((path) => /wechat/i.test(path))).toBe(false)
  })
})
