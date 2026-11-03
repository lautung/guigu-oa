import { getDefaultPostLoginRoute } from '@/navigation/constants'
import { PATHS, WORK_LIST_KINDS } from './paths'

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

const AppShell = () => import('@/shell/AppShell.vue')

/** 子路由路径从 paths 的绝对路径派生，避免第二份路径字符串。 */
function childPath(parent: string, absolute: string): string {
  return absolute.startsWith(`${parent}/`) ? absolute.slice(parent.length + 1) : absolute
}

const workChildren: AppRoute[] = [
  {
    path: childPath(PATHS.WORK, PATHS.WORK_START),
    name: 'WorkStart',
    component: () => import('@/views/work/start/index.vue'),
    meta: { title: '发起审批', menuGroup: 'work' },
  },
  {
    path: childPath(PATHS.WORK, PATHS.WORK_START_APPLY),
    name: 'WorkStartApply',
    component: () => import('@/views/work/start/apply.vue'),
    meta: { title: '发起审批', menuGroup: 'work' },
  },
  {
    path: childPath(PATHS.WORK, PATHS.WORK_PENDING),
    name: 'Pending',
    component: () => import('@/views/work/WorkListPage.vue'),
    meta: { title: '待处理', menuGroup: 'work', workListKind: WORK_LIST_KINDS.PENDING },
  },
  {
    path: childPath(PATHS.WORK, PATHS.WORK_PENDING_DETAIL),
    name: 'PendingDetail',
    component: () => import('@/views/work/pending/detail.vue'),
    meta: { title: '审批详情', menuGroup: 'work', workListKind: WORK_LIST_KINDS.PENDING },
  },
  {
    path: childPath(PATHS.WORK, PATHS.WORK_PROCESSED),
    name: 'WorkProcessed',
    component: () => import('@/views/work/WorkListPage.vue'),
    meta: { title: '已处理', menuGroup: 'work', workListKind: WORK_LIST_KINDS.PROCESSED },
  },
  {
    path: childPath(PATHS.WORK, PATHS.WORK_PROCESSED_DETAIL),
    name: 'ProcessedDetail',
    component: () => import('@/views/work/pending/detail.vue'),
    meta: { title: '审批详情', menuGroup: 'work', workListKind: WORK_LIST_KINDS.PROCESSED },
  },
  {
    path: childPath(PATHS.WORK, PATHS.WORK_STARTED),
    name: 'WorkStarted',
    component: () => import('@/views/work/WorkListPage.vue'),
    meta: { title: '已发起', menuGroup: 'work', workListKind: WORK_LIST_KINDS.STARTED },
  },
  {
    path: childPath(PATHS.WORK, PATHS.WORK_STARTED_DETAIL),
    name: 'StartedDetail',
    component: () => import('@/views/work/pending/detail.vue'),
    meta: { title: '审批详情', menuGroup: 'work', workListKind: WORK_LIST_KINDS.STARTED },
  },
  {
    path: childPath(PATHS.WORK, PATHS.WORK_COPIED),
    name: 'WorkCopied',
    component: () => import('@/views/work/WorkListPage.vue'),
    meta: { title: '抄送我的', menuGroup: 'work', workListKind: WORK_LIST_KINDS.COPIED },
  },
  {
    path: childPath(PATHS.WORK, PATHS.WORK_COPIED_DETAIL),
    name: 'CopiedDetail',
    component: () => import('@/views/work/pending/detail.vue'),
    meta: { title: '审批详情', menuGroup: 'work', workListKind: WORK_LIST_KINDS.COPIED },
  },
  {
    path: childPath(PATHS.WORK, PATHS.WORK_PROFILE),
    name: 'WorkProfile',
    component: () => import('@/views/work/profile/index.vue'),
    meta: { title: '基本信息', menuGroup: 'work' },
  },
]

const configChildren: AppRoute[] = [
  {
    path: childPath(PATHS.SYSTEM, PATHS.SYSTEM_SYS_USER),
    name: 'SysUser',
    component: () => import('@/views/config/sysUser/index.vue'),
    meta: { title: '用户管理', menuGroup: 'config' },
  },
  {
    path: childPath(PATHS.SYSTEM, PATHS.SYSTEM_SYS_ROLE),
    name: 'SysRole',
    component: () => import('@/views/config/sysRole/index.vue'),
    meta: { title: '角色管理', menuGroup: 'config' },
  },
  {
    path: childPath(PATHS.SYSTEM, PATHS.SYSTEM_SYS_MENU),
    name: 'SysMenu',
    component: () => import('@/views/config/sysMenu/index.vue'),
    meta: { title: '菜单管理', menuGroup: 'config' },
  },
  {
    path: childPath(PATHS.SYSTEM, PATHS.SYSTEM_LOG_OPER),
    name: 'SysOperLog',
    component: () => import('@/views/config/operLog/index.vue'),
    meta: { title: '操作日志', menuGroup: 'config' },
  },
  {
    path: childPath(PATHS.SYSTEM, PATHS.SYSTEM_LOG_LOGIN),
    name: 'SysLoginLog',
    component: () => import('@/views/config/loginLog/index.vue'),
    meta: { title: '登录日志', menuGroup: 'config' },
  },
  {
    path: childPath(PATHS.SYSTEM, PATHS.SYSTEM_NOTICE),
    name: 'SysNotice',
    component: () => import('@/views/config/notice/index.vue'),
    meta: { title: '通知公告', menuGroup: 'config' },
  },
  {
    // 隐藏子路由（#220）：作为已授予 `/system/notice` 的前缀被 #224 守卫覆盖，
    // 不单独出现在菜单或页面授予里。
    path: childPath(PATHS.SYSTEM, PATHS.SYSTEM_NOTICE_EDIT),
    name: 'SysNoticeEdit',
    component: () => import('@/views/config/notice/edit.vue'),
    meta: { title: '编辑公告', menuGroup: 'config' },
    hidden: true,
  },
  {
    path: childPath(PATHS.SYSTEM, PATHS.SYSTEM_ASSIGN_AUTH),
    name: 'AssignAuth',
    component: () => import('@/views/config/sysRole/assignAuth.vue'),
    meta: { title: '菜单授权', menuGroup: 'config', hiddenParentPath: PATHS.SYSTEM_SYS_ROLE },
    hidden: true,
  },
]

const processSetChildren: AppRoute[] = [
  {
    path: childPath(PATHS.PROCESS_SET, PATHS.PROCESS_SET_PROCESS_TYPE),
    name: 'ProcessType',
    component: () => import('@/views/config/processType/index.vue'),
    meta: { title: '审批类型', menuGroup: 'config' },
  },
  {
    path: childPath(PATHS.PROCESS_SET, PATHS.PROCESS_SET_PROCESS_TEMPLATE),
    name: 'ProcessTemplate',
    component: () => import('@/views/config/processTemplate/index.vue'),
    meta: { title: '审批模板', menuGroup: 'config' },
  },
  {
    path: childPath(PATHS.PROCESS_SET, PATHS.PROCESS_SET_TEMPLATE_SET),
    name: 'TemplateSet',
    component: () => import('@/views/config/processTemplate/templateSet.vue'),
    meta: {
      title: '审批模板设置',
      menuGroup: 'config',
      hiddenParentPath: PATHS.PROCESS_SET_PROCESS_TEMPLATE,
    },
    hidden: true,
  },
]

const noticeChildren: AppRoute[] = [
  {
    path: '',
    name: 'NoticeList',
    component: () => import('@/views/notice/index.vue'),
    meta: { title: '全部公告' },
  },
  {
    path: ':id',
    name: 'NoticeDetail',
    component: () => import('@/views/notice/detail.vue'),
    meta: { title: '公告详情' },
  },
]

export const constantRoutes: AppRoute[] = [
  {
    path: PATHS.LOGIN,
    component: () => import('@/views/login/index.vue'),
    hidden: true,
  },
  {
    // 一个外壳、一个 router-view（ADR 0059）：认证路由全部挂在这里，/login 在外。
    path: '/',
    component: AppShell,
    redirect: getDefaultPostLoginRoute(),
    children: [
      {
        path: PATHS.WORKBENCH,
        name: 'Workbench',
        component: () => import('@/views/workbench/index.vue'),
        meta: { title: '工作台' },
      },
      {
        path: PATHS.WORK,
        redirect: getDefaultPostLoginRoute(),
        meta: { menuGroup: 'work' },
        children: workChildren,
      },
      {
        // 公告读面是内置路由（ADR 0055）：登录即有，不设 menuGroup，因此配置页授予
        // 不把它当后台页面（#224 的守卫只过滤 config 组）。
        path: PATHS.NOTICE,
        children: noticeChildren,
      },
      {
        path: PATHS.SYSTEM,
        redirect: PATHS.SYSTEM_SYS_USER,
        meta: { menuGroup: 'config' },
        children: configChildren,
      },
      {
        path: PATHS.PROCESS_SET,
        redirect: PATHS.PROCESS_SET_PROCESS_TYPE,
        meta: { menuGroup: 'config' },
        children: processSetChildren,
      },
      {
        path: PATHS.PROCESS_MGR,
        redirect: PATHS.PROCESS_MGR_PROCESS,
        meta: { menuGroup: 'config' },
        children: [
          {
            path: childPath(PATHS.PROCESS_MGR, PATHS.PROCESS_MGR_PROCESS),
            name: 'ProcessMgr',
            component: () => import('@/views/config/processInstance/index.vue'),
            meta: { title: '审批实例查询', menuGroup: 'config' },
          },
          {
            path: childPath(PATHS.PROCESS_MGR, PATHS.PROCESS_MGR_PROCESS_DETAIL),
            name: 'ProcessMgrDetail',
            component: () => import('@/views/work/pending/detail.vue'),
            meta: { title: '审批详情', menuGroup: 'config' },
            hidden: true,
          },
        ],
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
