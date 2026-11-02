import { describe, expect, it } from 'vitest'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { resolveAuthGuard } from '@/navigation'
import { AUTH_WHITELIST, ROUTES } from '@/navigation/constants'
import { setupNavigation } from '@/router/guard'

describe('认证守卫', () => {
  it('redirects unauthenticated users to login with the return path', () => {
    expect(
      resolveAuthGuard({
        path: ROUTES.PENDING,
        fullPath: '/work/pending?page=2',
        hasToken: false,
      }),
    ).toEqual({
      action: 'redirect',
      to: `${ROUTES.LOGIN}?redirect=${encodeURIComponent('/work/pending?page=2')}`,
    })
  })

  it('allows the login page without a token', () => {
    expect(resolveAuthGuard({ path: ROUTES.LOGIN, hasToken: false })).toEqual({
      action: 'allow',
    })
  })

  it('redirects authenticated users away from login to 待处理', () => {
    expect(resolveAuthGuard({ path: ROUTES.LOGIN, hasToken: true })).toEqual({
      action: 'redirect',
      to: ROUTES.PENDING,
    })
  })

  it('only whitelists the login page', () => {
    expect(AUTH_WHITELIST).toEqual([ROUTES.LOGIN])
  })
})

function createTestRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: ROUTES.LOGIN, component: { render: () => null } },
      { path: ROUTES.PENDING, component: { render: () => null } },
      { path: '/system/sysUser', component: { render: () => null } },
    ],
  })
}

function setupAppNavigation(credentials) {
  const router = createTestRouter()
  setupNavigation(router, createPinia(), {
    credentials: () => credentials,
    recognize: async () => {},
  })
  return router
}

describe('导航集成', () => {
  it('keeps only the 认证守卫: an authenticated user reaches a 配置 address', async () => {
    const router = setupAppNavigation({ token: 'token' })

    await router.push('/system/sysUser')

    expect(router.currentRoute.value.path).toBe('/system/sysUser')
  })

  it('sends an unauthenticated user to login with the return path', async () => {
    const router = setupAppNavigation({ token: '' })

    await router.push('/system/sysUser')

    expect(router.currentRoute.value.path).toBe(ROUTES.LOGIN)
    expect(router.currentRoute.value.query.redirect).toBe('/system/sysUser')
  })
})
