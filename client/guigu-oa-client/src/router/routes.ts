import { getDefaultPostLoginRoute, ROUTES } from '@/navigation/constants'
import { WORK_LIST_KINDS } from '@/work/work-list'

export type RouteMenuGroup = 'work' | 'config'

export interface RouteGroupEntry {
  path: string
  group: RouteMenuGroup
  hiddenParentPath?: string
}

interface AppRouteMeta {
  title?: string
  menuGroup?: RouteMenuGroup
  hiddenParentPath?: string
  workListKind?: string
}

interface AppRoute {
  path: string
  name?: string
  component?: unknown
  redirect?: unknown
  hidden?: boolean
  meta?: AppRouteMeta
  children?: AppRoute[]
}

const ClientSurfaceHost = () => import('@/surface/ClientSurfaceHost.vue')

const workChildren: AppRoute[] = [
  {
    path: 'start',
    name: 'WorkStart',
    component: () => import('@/views/work/start/index.vue'),
    meta: { title: '发起审批', menuGroup: 'work' },
  },
  {
    path: 'start/:processTemplateId',
    name: 'WorkStartApply',
    component: () => import('@/views/work/start/apply.vue'),
    meta: { title: '发起审批', menuGroup: 'work' },
  },
  {
    path: 'pending',
    name: 'Pending',
    component: () => import('@/views/work/WorkListPage.vue'),
    meta: { title: '待处理', menuGroup: 'work', workListKind: WORK_LIST_KINDS.PENDING },
  },
  {
    path: 'pending/:id/:taskId',
    name: 'PendingDetail',
    component: () => import('@/views/work/pending/detail.vue'),
    meta: { title: '审批详情', menuGroup: 'work', workListKind: WORK_LIST_KINDS.PENDING },
  },
  {
    path: 'processed',
    name: 'WorkProcessed',
    component: () => import('@/views/work/WorkListPage.vue'),
    meta: { title: '已处理', menuGroup: 'work', workListKind: WORK_LIST_KINDS.PROCESSED },
  },
  {
    path: 'processed/:id',
    name: 'ProcessedDetail',
    component: () => import('@/views/work/pending/detail.vue'),
    meta: { title: '审批详情', menuGroup: 'work', workListKind: WORK_LIST_KINDS.PROCESSED },
  },
  {
    path: 'started',
    name: 'WorkStarted',
    component: () => import('@/views/work/WorkListPage.vue'),
    meta: { title: '已发起', menuGroup: 'work', workListKind: WORK_LIST_KINDS.STARTED },
  },
  {
    path: 'started/:id',
    name: 'StartedDetail',
    component: () => import('@/views/work/pending/detail.vue'),
    meta: { title: '审批详情', menuGroup: 'work', workListKind: WORK_LIST_KINDS.STARTED },
  },
  {
    path: 'copied',
    name: 'WorkCopied',
    component: () => import('@/views/work/WorkListPage.vue'),
    meta: { title: '抄送我的', menuGroup: 'work', workListKind: WORK_LIST_KINDS.COPIED },
  },
  {
    path: 'copied/:id',
    name: 'CopiedDetail',
    component: () => import('@/views/work/pending/detail.vue'),
    meta: { title: '审批详情', menuGroup: 'work', workListKind: WORK_LIST_KINDS.COPIED },
  },
  {
    path: 'profile',
    name: 'WorkProfile',
    component: () => import('@/views/work/profile/index.vue'),
    meta: { title: '基本信息', menuGroup: 'work' },
  },
]

const configChildren: AppRoute[] = [
  {
    path: 'sysUser',
    name: 'SysUser',
    component: () => import('@/views/config/sysUser/index.vue'),
    meta: { title: '用户管理', menuGroup: 'config' },
  },
  {
    path: 'sysRole',
    name: 'SysRole',
    component: () => import('@/views/config/sysRole/index.vue'),
    meta: { title: '角色管理', menuGroup: 'config' },
  },
  {
    path: 'sysMenu',
    name: 'SysMenu',
    component: () => import('@/views/config/sysMenu/index.vue'),
    meta: { title: '菜单管理', menuGroup: 'config' },
  },
  {
    path: 'assignAuth',
    name: 'AssignAuth',
    component: () => import('@/views/config/sysRole/assignAuth.vue'),
    meta: { title: '菜单授权', menuGroup: 'config', hiddenParentPath: '/system/sysRole' },
    hidden: true,
  },
]

const processSetChildren: AppRoute[] = [
  {
    path: 'processType',
    name: 'ProcessType',
    component: () => import('@/views/config/processType/index.vue'),
    meta: { title: '审批类型', menuGroup: 'config' },
  },
  {
    path: 'processTemplate',
    name: 'ProcessTemplate',
    component: () => import('@/views/config/processTemplate/index.vue'),
    meta: { title: '审批模板', menuGroup: 'config' },
  },
  {
    path: 'templateSet',
    name: 'TemplateSet',
    component: () => import('@/views/config/processTemplate/templateSet.vue'),
    meta: {
      title: '审批模板设置',
      menuGroup: 'config',
      hiddenParentPath: '/processSet/processTemplate',
    },
    hidden: true,
  },
]

export const constantRoutes: AppRoute[] = [
  {
    path: ROUTES.LOGIN,
    component: () => import('@/views/login/index.vue'),
    hidden: true,
  },
  {
    path: '/',
    redirect: getDefaultPostLoginRoute(),
  },
  {
    path: '/work',
    component: ClientSurfaceHost,
    redirect: getDefaultPostLoginRoute(),
    meta: { menuGroup: 'work' },
    children: workChildren,
  },
  {
    path: '/system',
    component: ClientSurfaceHost,
    redirect: '/system/sysUser',
    meta: { menuGroup: 'config' },
    children: configChildren,
  },
  {
    path: '/processSet',
    component: ClientSurfaceHost,
    redirect: '/processSet/processType',
    meta: { menuGroup: 'config' },
    children: processSetChildren,
  },
  {
    path: '/processMgr',
    component: ClientSurfaceHost,
    redirect: '/processMgr/process',
    meta: { menuGroup: 'config' },
    children: [
      {
        path: 'process',
        name: 'ProcessMgr',
        component: () => import('@/views/config/processInstance/index.vue'),
        meta: { title: '审批实例查询', menuGroup: 'config' },
      },
      {
        path: 'process/:id',
        name: 'ProcessMgrDetail',
        component: () => import('@/views/work/pending/detail.vue'),
        meta: { title: '审批详情', menuGroup: 'config' },
        hidden: true,
      },
    ],
  },
]

function normalizePath(path: string): string {
  const value = `/${path}`.replace(/\/{2,}/g, '/')
  return value.length > 1 && value.endsWith('/') ? value.slice(0, -1) : value
}

function routePath(parentPath: string, path: string): string {
  if (path.startsWith('/')) return normalizePath(path)
  return normalizePath(`${parentPath}/${path}`)
}

function flattenRoutes(routes: AppRoute[], parentPath = ''): RouteGroupEntry[] {
  return routes.flatMap((route) => {
    const path = routePath(parentPath, route.path)
    const meta = route.meta || {}
    const entry: RouteGroupEntry[] = meta.menuGroup
      ? [
          {
            path,
            group: meta.menuGroup,
            ...(meta.hiddenParentPath ? { hiddenParentPath: meta.hiddenParentPath } : {}),
          },
        ]
      : []
    const children = route.children ? flattenRoutes(route.children, path) : []
    return [...entry, ...children]
  })
}

/** 客户端路由自己声明组归属；配置授予只决定这些路由里哪些能被占用。 */
export const ROUTE_GROUP_ENTRIES: readonly RouteGroupEntry[] = flattenRoutes(constantRoutes)
