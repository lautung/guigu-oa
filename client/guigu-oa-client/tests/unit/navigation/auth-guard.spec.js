import { describe, expect, it } from 'vitest'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { resolveAuthGuard } from '@/navigation'
import { AUTH_WHITELIST, ROUTES } from '@/navigation/constants'
import { setupNavigation } from '@/router/guard'
import { useUserStore } from '@/store/modules/user'

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

const USER_PAGE = [
  { path: '/system', title: '组织与权限', type: 0, sort: 1 },
  { path: '/system/sysUser', parentPath: '/system', title: '用户管理', type: 1, sort: 1 },
]

const PROCESS_INSTANCE_PAGE = [
  { path: '/system/processMgr', title: '审批管理', type: 0, sort: 1 },
  {
    path: '/system/processMgr/process',
    parentPath: '/system/processMgr',
    title: '审批实例查询',
    type: 1,
    sort: 1,
  },
]

describe('后台页面授予守卫', () => {
  it('allows a granted page', () => {
    expect(resolveAuthGuard({ path: '/system/sysUser', hasToken: true, menus: USER_PAGE })).toEqual(
      { action: 'allow' },
    )
  })

  it('redirects an authenticated user off an ungranted backoffice page to 待处理', () => {
    expect(resolveAuthGuard({ path: '/system/sysUser', hasToken: true, menus: [] })).toEqual({
      action: 'redirect',
      to: ROUTES.PENDING,
    })
  })

  it('allows a directory only when a granted page falls under it', () => {
    expect(
      resolveAuthGuard({
        path: '/system/processMgr',
        hasToken: true,
        menus: PROCESS_INSTANCE_PAGE,
      }),
    ).toEqual({ action: 'allow' })
  })

  it('redirects a directory with no granted page under it', () => {
    expect(
      resolveAuthGuard({ path: '/system/processMgr', hasToken: true, menus: USER_PAGE }),
    ).toEqual({
      action: 'redirect',
      to: ROUTES.PENDING,
    })
  })

  it('covers a detail subpath under a granted page', () => {
    expect(
      resolveAuthGuard({
        path: '/system/processMgr/process/123',
        hasToken: true,
        menus: PROCESS_INSTANCE_PAGE,
      }),
    ).toEqual({ action: 'allow' })
  })

  it('covers the hidden notice editor as a prefix of the granted notice page', () => {
    const noticePage = [
      { path: '/system', title: '组织与权限', type: 0, sort: 1 },
      { path: '/system/notice', parentPath: '/system', title: '通知公告', type: 1, sort: 1 },
    ]

    expect(
      resolveAuthGuard({ path: '/system/notice/edit/7', hasToken: true, menus: noticePage }),
    ).toEqual({ action: 'allow' })
    expect(
      resolveAuthGuard({ path: '/system/notice/edit', hasToken: true, menus: noticePage }),
    ).toEqual({ action: 'allow' })
    expect(
      resolveAuthGuard({ path: '/system/notice/edit/7', hasToken: true, menus: USER_PAGE }),
    ).toEqual({ action: 'redirect', to: ROUTES.PENDING })
  })

  it('allows a hidden page only through its own menus entry', () => {
    const roleWithoutHidden = [
      { path: '/system', title: '组织与权限', type: 0, sort: 1 },
      { path: '/system/sysRole', parentPath: '/system', title: '角色管理', type: 1, sort: 1 },
    ]
    const roleWithHidden = [
      ...roleWithoutHidden,
      {
        path: '/system/assignAuth',
        parentPath: '/system/sysRole',
        title: '菜单授权',
        type: 1,
        hidden: true,
        sort: 2,
      },
    ]

    expect(
      resolveAuthGuard({ path: '/system/assignAuth', hasToken: true, menus: roleWithHidden }),
    ).toEqual({ action: 'allow' })
    expect(
      resolveAuthGuard({ path: '/system/assignAuth', hasToken: true, menus: roleWithoutHidden }),
    ).toEqual({ action: 'redirect', to: ROUTES.PENDING })
  })

  it('redirects an undeclared backoffice path that menus cannot cover', () => {
    expect(
      resolveAuthGuard({ path: '/system/not-a-page', hasToken: true, menus: USER_PAGE }),
    ).toEqual({
      action: 'redirect',
      to: ROUTES.PENDING,
    })
  })

  it('keeps built-in 办事 routes allowed without any menus entry', () => {
    expect(resolveAuthGuard({ path: ROUTES.PENDING, hasToken: true, menus: [] })).toEqual({
      action: 'allow',
    })
    expect(resolveAuthGuard({ path: '/work/pending/9/task-1', hasToken: true })).toEqual({
      action: 'allow',
    })
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

function setupAppNavigation(credentials, menus = []) {
  const router = createTestRouter()
  const pinia = createPinia()
  useUserStore(pinia).menus = menus
  setupNavigation(router, pinia, {
    credentials: () => credentials,
    recognize: async () => {},
  })
  return router
}

describe('导航集成', () => {
  it('lets an authenticated user reach a granted 配置 address', async () => {
    const router = setupAppNavigation({ token: 'token' }, USER_PAGE)

    await router.push('/system/sysUser')

    expect(router.currentRoute.value.path).toBe('/system/sysUser')
  })

  it('falls back to 待处理 when an authenticated user opens an ungranted 配置 address', async () => {
    const router = setupAppNavigation({ token: 'token' })

    await router.push('/system/sysUser')

    expect(router.currentRoute.value.path).toBe(ROUTES.PENDING)
  })

  it('sends an unauthenticated user to login with the return path', async () => {
    const router = setupAppNavigation({ token: '' })

    await router.push('/system/sysUser')

    expect(router.currentRoute.value.path).toBe(ROUTES.LOGIN)
    expect(router.currentRoute.value.query.redirect).toBe('/system/sysUser')
  })
})
