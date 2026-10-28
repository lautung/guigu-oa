import { describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { flushPromises, mount } from '@vue/test-utils'
import { constantRoutes } from '@/router'
import ClientSurfaceHost from '@/surface/ClientSurfaceHost.vue'
import { createViewportOccupancyHandler, resolveClientSurface } from '@/surface/client-surface'
import { useUserStore } from '@/store/modules/user'

const SAMPLE_CONFIG_MENUS = [
  { path: '/system', title: '组织与权限', type: 0, sort: 1 },
  { path: '/system/sysUser', parentPath: '/system', title: '用户管理', type: 1, sort: 1 },
  { path: '/system/sysRole', parentPath: '/system', title: '角色管理', type: 1, sort: 2 },
  {
    path: '/system/assignAuth',
    parentPath: '/system/sysRole',
    title: '菜单授权',
    type: 1,
    hidden: true,
    sort: 3,
  },
  { path: '/processSet', title: '审批设置', type: 0, sort: 2 },
  {
    path: '/processSet/processType',
    parentPath: '/processSet',
    title: '审批类型',
    type: 1,
    sort: 1,
  },
  {
    path: '/processSet/processTemplate',
    parentPath: '/processSet',
    title: '审批模板',
    type: 1,
    sort: 2,
  },
  {
    path: '/processSet/templateSet',
    parentPath: '/processSet/processTemplate',
    title: '审批模板设置',
    type: 1,
    hidden: true,
    sort: 3,
  },
  { path: '/processMgr', title: '审批查询', type: 0, sort: 3 },
  {
    path: '/processMgr/process',
    parentPath: '/processMgr',
    title: '审批实例查询',
    type: 1,
    sort: 1,
  },
]

const ONLY_USER_MENUS = [
  { path: '/system', title: '组织与权限', type: 0, sort: 1 },
  { path: '/system/sysUser', parentPath: '/system', title: '用户管理', type: 1, sort: 1 },
]

const HOST_ROUTES = [
  { path: '/work/pending', component: { template: '<div />' } },
  { path: '/system/sysUser', component: { template: '<div />' } },
  { path: '/system/sysRole', component: { template: '<div />' } },
]

async function mountSurfaceHost({ initialPath, menus = [] }) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const router = createRouter({
    history: createMemoryHistory(),
    routes: HOST_ROUTES,
  })
  const store = useUserStore(pinia)
  store.token = 'jwt'
  store.menus = menus
  await router.push(initialPath)
  await router.isReady()

  const wrapper = mount(ClientSurfaceHost, {
    global: {
      plugins: [pinia, router],
      stubs: { TopBar: true, AppSidebar: true, RouterView: true },
    },
  })

  return { wrapper, router }
}

describe('统一客户端表面', () => {
  it('login page has no 菜单组 and does not show the surface', () => {
    const surface = resolveClientSurface({
      path: '/login',
      viewportWidth: 1200,
      hasToken: false,
      menus: [],
    })
    const trailingSlash = resolveClientSurface({
      path: '/login/',
      viewportWidth: 1200,
      hasToken: false,
      menus: [],
    })

    expect(surface.showSurface).toBe(false)
    expect(surface.menuGroup).toBeNull()
    expect(surface.density).toBeNull()
    expect(surface.occupancy).toEqual({ action: 'allow' })
    expect(trailingSlash).toEqual(surface)
  })

  it('a tokenless 配置 path keeps occupancy allow without showing the surface', () => {
    const surface = resolveClientSurface({
      path: '/system/sysUser',
      viewportWidth: 1200,
      hasToken: false,
      menus: [],
    })

    expect(surface.showSurface).toBe(false)
    expect(surface.menuGroup).toBe('config')
    expect(surface.occupancy).toEqual({ action: 'allow' })
  })

  it('a 办事 entry uses 办事 density and stays occupyable on mobile', () => {
    const surface = resolveClientSurface({
      path: '/work/pending',
      viewportWidth: 375,
      hasToken: true,
      menus: SAMPLE_CONFIG_MENUS,
    })

    expect(surface.showSurface).toBe(true)
    expect(surface.menuGroup).toBe('work')
    expect(surface.density).toBe('constrained')
    expect(surface.occupancy).toEqual({ action: 'allow' })
  })

  it('a 配置 entry uses 配置 density', () => {
    const surface = resolveClientSurface({
      path: '/system/sysUser',
      viewportWidth: 1200,
      hasToken: true,
      menus: SAMPLE_CONFIG_MENUS,
    })

    expect(surface.showSurface).toBe(true)
    expect(surface.menuGroup).toBe('config')
    expect(surface.density).toBe('wide')
    expect(surface.occupancy).toEqual({ action: 'allow' })
  })

  it('hidden pages follow their parent 菜单组', () => {
    const assignAuth = resolveClientSurface({
      path: '/system/assignAuth',
      viewportWidth: 1200,
      hasToken: true,
      menus: SAMPLE_CONFIG_MENUS,
    })
    const templateSet = resolveClientSurface({
      path: '/processSet/templateSet',
      viewportWidth: 1200,
      hasToken: true,
      menus: SAMPLE_CONFIG_MENUS,
    })

    expect(assignAuth.menuGroup).toBe('config')
    expect(assignAuth.density).toBe('wide')
    expect(templateSet.menuGroup).toBe('config')
    expect(templateSet.density).toBe('wide')
  })

  it('the same 审批详情 takes 菜单组 and 返回位置 from the entry it was opened from', () => {
    const fromWork = resolveClientSurface({
      path: '/work/pending/9/task-1',
      viewportWidth: 1200,
      hasToken: true,
      menus: SAMPLE_CONFIG_MENUS,
    })
    const fromConfig = resolveClientSurface({
      path: '/processMgr/process/9',
      viewportWidth: 1200,
      hasToken: true,
      menus: SAMPLE_CONFIG_MENUS,
    })

    expect(fromWork.menuGroup).toBe('work')
    expect(fromWork.density).toBe('constrained')
    expect(fromWork.returnPath).toBe('/work/pending')
    expect(fromWork.hasWorkListPerspective).toBe(true)

    expect(fromConfig.menuGroup).toBe('config')
    expect(fromConfig.density).toBe('wide')
    expect(fromConfig.returnPath).toBe('/processMgr/process')
    expect(fromConfig.hasWorkListPerspective).toBe(false)
  })

  it('shrinking onto a 配置 page lands on the same 待处理 target as entering', () => {
    const surfaceInput = {
      path: '/system/sysUser',
      hasToken: true,
      menus: SAMPLE_CONFIG_MENUS,
    }
    const entering = resolveClientSurface({
      ...surfaceInput,
      viewportWidth: 375,
    }).occupancy
    const goTo = vi.fn()
    const handleViewportWidth = createViewportOccupancyHandler({
      getSurfaceInput: () => surfaceInput,
      goTo,
    })

    handleViewportWidth(1200)
    expect(goTo).not.toHaveBeenCalled()

    handleViewportWidth(375)
    expect(goTo).toHaveBeenCalledTimes(1)
    expect(goTo).toHaveBeenCalledWith(entering.to)
    expect(goTo).toHaveBeenCalledWith('/work/pending')
  })

  it('shrinking onto 办事 does not leave the current page', () => {
    const goTo = vi.fn()
    createViewportOccupancyHandler({
      getSurfaceInput: () => ({
        path: '/work/pending',
        hasToken: true,
        menus: SAMPLE_CONFIG_MENUS,
      }),
      goTo,
    })(375)

    expect(goTo).not.toHaveBeenCalled()
  })

  it('办事入口 and 配置入口 hang on the same surface host', () => {
    const hosted = constantRoutes.filter((route) => route.children && route.children.length)
    expect(hosted.length).toBeGreaterThan(1)
    hosted.forEach((route) => {
      expect(route.component).toBe(hosted[0].component)
    })
  })
})

describe('表面宿主', () => {
  it('direct entry to an ungranted 配置 page lands on 待处理', async () => {
    const { wrapper, router } = await mountSurfaceHost({
      initialPath: '/system/sysRole',
      menus: ONLY_USER_MENUS,
    })

    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/work/pending')
    wrapper.unmount()
  })

  it('direct entry to a granted 配置 page on mobile lands on 待处理', async () => {
    const originalWidth = window.innerWidth
    window.innerWidth = 375

    const { wrapper, router } = await mountSurfaceHost({
      initialPath: '/system/sysUser',
      menus: SAMPLE_CONFIG_MENUS,
    })

    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/work/pending')
    wrapper.unmount()
    window.innerWidth = originalWidth
  })

  it('a route change into an ungranted 配置 page lands on 待处理', async () => {
    const { wrapper, router } = await mountSurfaceHost({
      initialPath: '/system/sysUser',
      menus: ONLY_USER_MENUS,
    })

    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/system/sysUser')

    await router.push('/system/sysRole')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/work/pending')
    wrapper.unmount()
  })

  it('a route change into a granted 配置 page on desktop stays', async () => {
    const { wrapper, router } = await mountSurfaceHost({
      initialPath: '/work/pending',
      menus: SAMPLE_CONFIG_MENUS,
    })

    await flushPromises()
    await router.push('/system/sysRole')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/system/sysRole')
    wrapper.unmount()
  })
})
