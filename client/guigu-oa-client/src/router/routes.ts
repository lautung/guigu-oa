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
    // 部门管理（#247）：树表格 + 新增/编辑弹窗；读只判页面授予，写挂三个按钮点。
    path: childPath(PATHS.SYSTEM, PATHS.SYSTEM_SYS_DEPT),
    name: 'SysDept',
    component: () => import('@/views/config/sysDept/index.vue'),
    meta: { title: '部门管理', menuGroup: 'config' },
  },
  {
    path: childPath(PATHS.SYSTEM, PATHS.SYSTEM_SYS_POST),
    name: 'SysPost',
    component: () => import('@/views/config/sysPost/index.vue'),
    meta: { title: '岗位管理', menuGroup: 'config' },
  },
  {
    // 参数设置（#252）：键值 CRUD；内置项只读 key/元数据，写后刷新与手动刷新缓存。
    path: childPath(PATHS.SYSTEM, PATHS.SYSTEM_CONFIG),
    name: 'SysConfig',
    component: () => import('@/views/config/config/index.vue'),
    meta: { title: '参数设置', menuGroup: 'config' },
  },
  {
    // 字典管理（#251）：左类型、右数据两级；写后刷新与手动刷新缓存走各自的 list page。
    path: childPath(PATHS.SYSTEM, PATHS.SYSTEM_DICT),
    name: 'SysDict',
    component: () => import('@/views/config/dict/index.vue'),
    meta: { title: '字典管理', menuGroup: 'config' },
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
        // /monitor 占位保留作直接访问兜底（#239）；监控页面是它的平级子路由。
        // 系统工具已翻真（#246），下面 `/tool` 是跳到接口文档页的目录路由。
        path: PATHS.MONITOR,
        name: 'Monitor',
        component: () => import('@/shell/ModulePlaceholder.vue'),
        meta: { title: '系统监控' },
      },
      {
        // 在线用户（#239，ADR 0060）：监控首个真实页面；授予判定与配置页共用
        // coversPage 路径，未授权回落工作台（ADR 0058）。
        path: PATHS.MONITOR_ONLINE,
        name: 'MonitorOnline',
        component: () => import('@/views/monitor/online/index.vue'),
        meta: { title: '在线用户', menuGroup: 'config' },
      },
      {
        // 服务监控（#240，ADR 0060）：运行时快照面板；授予判定同在线用户。
        path: PATHS.MONITOR_SERVER,
        name: 'MonitorServer',
        component: () => import('@/views/monitor/server/index.vue'),
        meta: { title: '服务监控', menuGroup: 'config' },
      },
      {
        // 数据监控（#240，ADR 0060）：Hikari 实时 + 慢 SQL 内存环；授予判定同在线用户。
        path: PATHS.MONITOR_DATA,
        name: 'MonitorData',
        component: () => import('@/views/monitor/data/index.vue'),
        meta: { title: '数据监控', menuGroup: 'config' },
      },
      {
        // 缓存监控（#241，ADR 0060）：Redis INFO 五段快照面板；授予判定同在线用户。
        path: PATHS.MONITOR_CACHE,
        name: 'MonitorCache',
        component: () => import('@/views/monitor/cache/index.vue'),
        meta: { title: '缓存监控', menuGroup: 'config' },
      },
      {
        // 缓存列表（#241，ADR 0060）：白名单前缀键浏览 + 受限清理；授予判定同在线用户。
        path: PATHS.MONITOR_CACHE_LIST,
        name: 'MonitorCacheList',
        component: () => import('@/views/monitor/cacheList/index.vue'),
        meta: { title: '缓存列表', menuGroup: 'config' },
      },
      {
        // 定时任务（#242，ADR 0060）：表驱动任务面，编辑与手动执行各挂按钮点；授予判定同在线用户。
        path: PATHS.MONITOR_JOB,
        name: 'MonitorJob',
        component: () => import('@/views/monitor/job/index.vue'),
        meta: { title: '定时任务', menuGroup: 'config' },
      },
      {
        // 系统工具（#246，ADR 0061）：本期唯一页面是接口文档。目录只做落点跳转，
        // 页面本身属于 config 组，授予判定与配置页同一条 coversPage 路径。
        path: PATHS.TOOL,
        redirect: PATHS.TOOL_API_DOC,
        children: [
          {
            path: childPath(PATHS.TOOL, PATHS.TOOL_API_DOC),
            name: 'ToolApiDoc',
            component: () => import('@/views/tool/api-doc/index.vue'),
            meta: { title: '接口文档', menuGroup: 'config' },
          },
        ],
      },
      {
        path: PATHS.WORK,
        // 办事模块自身的入口仍是待处理；登录落点改的是 `/` 与守卫回落（#229）。
        redirect: PATHS.WORK_PENDING,
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
        // 个人中心（#254）：登录即有，不进后台菜单、不设页面授予/按钮点。
        path: PATHS.PROFILE,
        name: 'Profile',
        component: () => import('@/views/profile/index.vue'),
        meta: { title: '个人中心' },
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
