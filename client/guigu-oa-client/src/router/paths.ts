import type { EntityId, ProcessInstanceRecord } from '@/types/domain'

/**
 * 路径原子。路由表、列表、详情、面包屑都从这里取；字符串只在这里出现一次。
 */
export const PATHS = {
  LOGIN: '/login',
  WORKBENCH: '/workbench',
  MONITOR: '/monitor',
  MONITOR_ONLINE: '/monitor/online',
  MONITOR_SERVER: '/monitor/server',
  MONITOR_DATA: '/monitor/data',
  MONITOR_CACHE: '/monitor/cache',
  MONITOR_CACHE_LIST: '/monitor/cacheList',
  MONITOR_JOB: '/monitor/job',
  TOOL: '/tool',
  TOOL_API_DOC: '/tool/api-doc',
  WORK: '/work',
  WORK_START: '/work/start',
  WORK_START_APPLY: '/work/start/:processTemplateId',
  WORK_PENDING: '/work/pending',
  WORK_PENDING_DETAIL: '/work/pending/:id/:taskId',
  WORK_PROCESSED: '/work/processed',
  WORK_PROCESSED_DETAIL: '/work/processed/:id',
  WORK_STARTED: '/work/started',
  WORK_STARTED_DETAIL: '/work/started/:id',
  WORK_COPIED: '/work/copied',
  WORK_COPIED_DETAIL: '/work/copied/:id',
  /** 个人中心（#254）：登录即有，不进后台菜单、不设页面授予。 */
  PROFILE: '/profile',
  SYSTEM: '/system',
  SYSTEM_SYS_USER: '/system/sysUser',
  SYSTEM_SYS_ROLE: '/system/sysRole',
  SYSTEM_SYS_MENU: '/system/sysMenu',
  SYSTEM_SYS_DEPT: '/system/sysDept',
  SYSTEM_SYS_POST: '/system/sysPost',
  SYSTEM_CONFIG: '/system/config',
  SYSTEM_DICT: '/system/dict',
  SYSTEM_LOG_OPER: '/system/log/operLog',
  SYSTEM_LOG_LOGIN: '/system/log/loginLog',
  SYSTEM_ASSIGN_AUTH: '/system/assignAuth',
  PROCESS_SET: '/system/processSet',
  PROCESS_SET_PROCESS_TYPE: '/system/processSet/processType',
  PROCESS_SET_PROCESS_TEMPLATE: '/system/processSet/processTemplate',
  PROCESS_SET_TEMPLATE_SET: '/system/processSet/templateSet',
  PROCESS_MGR: '/system/processMgr',
  PROCESS_MGR_PROCESS: '/system/processMgr/process',
  PROCESS_MGR_PROCESS_DETAIL: '/system/processMgr/process/:id',
  NOTICE: '/notice',
  NOTICE_DETAIL: '/notice/:id',
  SYSTEM_NOTICE: '/system/notice',
  SYSTEM_NOTICE_EDIT: '/system/notice/edit/:id?',
} as const

/** 办事列表的静态路径，保留既有 ROUTES 形状供导航使用。 */
export const ROUTES = {
  LOGIN: PATHS.LOGIN,
  START: PATHS.WORK_START,
  PENDING: PATHS.WORK_PENDING,
  PROCESSED: PATHS.WORK_PROCESSED,
  STARTED: PATHS.WORK_STARTED,
  COPIED: PATHS.WORK_COPIED,
} as const

export const WORK_LIST_KINDS = {
  PENDING: 'pending',
  PROCESSED: 'processed',
  STARTED: 'started',
  COPIED: 'copied',
} as const

export type WorkListKind = (typeof WORK_LIST_KINDS)[keyof typeof WORK_LIST_KINDS]

const WORK_LIST_PATHS: Record<WorkListKind, string> = {
  [WORK_LIST_KINDS.PENDING]: PATHS.WORK_PENDING,
  [WORK_LIST_KINDS.PROCESSED]: PATHS.WORK_PROCESSED,
  [WORK_LIST_KINDS.STARTED]: PATHS.WORK_STARTED,
  [WORK_LIST_KINDS.COPIED]: PATHS.WORK_COPIED,
}

export function workListPath(kind: WorkListKind): string {
  return WORK_LIST_PATHS[kind]
}

export function workDetailPath(
  kind: WorkListKind,
  record: Pick<ProcessInstanceRecord, 'id' | 'taskId'>,
): string {
  const list = workListPath(kind)
  return kind === WORK_LIST_KINDS.PENDING
    ? `${list}/${record.id}/${record.taskId}`
    : `${list}/${record.id}`
}

export function workListKindOf(meta: unknown): WorkListKind | null {
  if (!meta || typeof meta !== 'object') return null
  const kind = (meta as { workListKind?: unknown }).workListKind
  return typeof kind === 'string' && Object.hasOwn(WORK_LIST_PATHS, kind)
    ? (kind as WorkListKind)
    : null
}

export function startApplyPath(templateId: EntityId): string {
  return `${PATHS.WORK_START}/${templateId}`
}

export function processTemplateListPath(): string {
  return PATHS.PROCESS_SET_PROCESS_TEMPLATE
}

export function templateSetPath(id?: EntityId): string {
  return id == null ? PATHS.PROCESS_SET_TEMPLATE_SET : `${PATHS.PROCESS_SET_TEMPLATE_SET}?id=${id}`
}

export function assignAuthPath(id: EntityId, roleName = ''): string {
  return `${PATHS.SYSTEM_ASSIGN_AUTH}?id=${id}&roleName=${encodeURIComponent(roleName)}`
}

export function processInstanceDetailPath(id: EntityId | undefined): string {
  return `${PATHS.PROCESS_MGR_PROCESS}/${id}`
}

export function sysRolePath(): string {
  return PATHS.SYSTEM_SYS_ROLE
}

/** 公告详情是内置读面（ADR 0055），路径只在这里拼一次。 */
export function noticeDetailPath(id: EntityId): string {
  return `${PATHS.NOTICE}/${id}`
}

/** 发布管理页的编辑子路由（#220）：新建不带 id，编辑带 id。 */
export function noticeEditPath(id?: EntityId): string {
  const base = PATHS.SYSTEM_NOTICE_EDIT.replace('/:id?', '')
  return id == null || id === '' ? base : `${base}/${id}`
}

const RETURN_PATH_BY_PATH: Record<string, string> = {
  [PATHS.LOGIN]: PATHS.LOGIN,
  [PATHS.WORKBENCH]: PATHS.WORKBENCH,
  [PATHS.MONITOR]: PATHS.MONITOR,
  [PATHS.MONITOR_ONLINE]: PATHS.MONITOR_ONLINE,
  [PATHS.MONITOR_SERVER]: PATHS.MONITOR_SERVER,
  [PATHS.MONITOR_DATA]: PATHS.MONITOR_DATA,
  [PATHS.MONITOR_CACHE]: PATHS.MONITOR_CACHE,
  [PATHS.MONITOR_CACHE_LIST]: PATHS.MONITOR_CACHE_LIST,
  [PATHS.MONITOR_JOB]: PATHS.MONITOR_JOB,
  [PATHS.TOOL]: PATHS.TOOL,
  [PATHS.TOOL_API_DOC]: PATHS.TOOL_API_DOC,
  [PATHS.WORK_START]: PATHS.WORK_START,
  [PATHS.WORK_PENDING]: PATHS.WORK_PENDING,
  [PATHS.WORK_PROCESSED]: PATHS.WORK_PROCESSED,
  [PATHS.WORK_STARTED]: PATHS.WORK_STARTED,
  [PATHS.WORK_COPIED]: PATHS.WORK_COPIED,
  [PATHS.PROFILE]: PATHS.PROFILE,
  [PATHS.SYSTEM_SYS_USER]: PATHS.SYSTEM_SYS_USER,
  [PATHS.SYSTEM_SYS_ROLE]: PATHS.SYSTEM_SYS_ROLE,
  [PATHS.SYSTEM_SYS_MENU]: PATHS.SYSTEM_SYS_MENU,
  [PATHS.SYSTEM_SYS_DEPT]: PATHS.SYSTEM_SYS_DEPT,
  [PATHS.SYSTEM_SYS_POST]: PATHS.SYSTEM_SYS_POST,
  [PATHS.SYSTEM_CONFIG]: PATHS.SYSTEM_CONFIG,
  [PATHS.SYSTEM_DICT]: PATHS.SYSTEM_DICT,
  [PATHS.SYSTEM_LOG_OPER]: PATHS.SYSTEM_LOG_OPER,
  [PATHS.SYSTEM_LOG_LOGIN]: PATHS.SYSTEM_LOG_LOGIN,
  [PATHS.SYSTEM_ASSIGN_AUTH]: PATHS.SYSTEM_SYS_ROLE,
  [PATHS.PROCESS_SET_PROCESS_TYPE]: PATHS.PROCESS_SET_PROCESS_TYPE,
  [PATHS.PROCESS_SET_PROCESS_TEMPLATE]: PATHS.PROCESS_SET_PROCESS_TEMPLATE,
  [PATHS.PROCESS_SET_TEMPLATE_SET]: PATHS.PROCESS_SET_PROCESS_TEMPLATE,
  [PATHS.PROCESS_MGR_PROCESS]: PATHS.PROCESS_MGR_PROCESS,
  [PATHS.NOTICE]: PATHS.NOTICE,
  [PATHS.SYSTEM_NOTICE]: PATHS.SYSTEM_NOTICE,
}

const RETURN_PATH_PREFIXES: readonly { prefix: string; returnPath: string }[] = [
  { prefix: `${PATHS.WORK_START}/`, returnPath: PATHS.WORK_START },
  { prefix: `${PATHS.WORK_PENDING}/`, returnPath: PATHS.WORK_PENDING },
  { prefix: `${PATHS.WORK_PROCESSED}/`, returnPath: PATHS.WORK_PROCESSED },
  { prefix: `${PATHS.WORK_STARTED}/`, returnPath: PATHS.WORK_STARTED },
  { prefix: `${PATHS.WORK_COPIED}/`, returnPath: PATHS.WORK_COPIED },
  { prefix: `${PATHS.PROCESS_MGR_PROCESS}/`, returnPath: PATHS.PROCESS_MGR_PROCESS },
  { prefix: `${PATHS.NOTICE}/`, returnPath: PATHS.NOTICE },
  { prefix: `${PATHS.SYSTEM_NOTICE}/`, returnPath: PATHS.SYSTEM_NOTICE },
]

function normalize(path: string | undefined): string {
  const value = (path || '').split('?')[0].split('#')[0]
  const withLeadingSlash = value.startsWith('/') ? value : `/${value}`
  return withLeadingSlash.replace(/\/+$/, '') || '/'
}

/**
 * 页面返回哪里：列表与静态页回自身，详情回它所属的列表，隐藏页回可见父级，未知路径回待处理。
 */
export function returnPathOfRoute(path: string | undefined): string {
  const normalized = normalize(path)
  const exact = RETURN_PATH_BY_PATH[normalized]
  if (exact) return exact
  const match = RETURN_PATH_PREFIXES.filter((entry) => normalized.startsWith(entry.prefix)).sort(
    (left, right) => right.prefix.length - left.prefix.length,
  )[0]
  return match ? match.returnPath : PATHS.WORK_PENDING
}
