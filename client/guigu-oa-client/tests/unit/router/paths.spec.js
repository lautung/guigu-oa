import { describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import { constantRoutes } from '@/router/routes'
import {
  PATHS,
  WORK_LIST_KINDS,
  assignAuthPath,
  noticeDetailPath,
  noticeEditPath,
  processInstanceDetailPath,
  processTemplateListPath,
  returnPathOfRoute,
  startApplyPath,
  sysRolePath,
  templateSetPath,
  workDetailPath,
  workListKindOf,
  workListPath,
} from '@/router/paths'
import { workListPerspectiveFromRoute } from '@/work/work-list'

const router = createRouter({ history: createMemoryHistory(), routes: constantRoutes })
const resolve = (path) => router.resolve(path)

describe('路径 module', () => {
  it.each([
    [WORK_LIST_KINDS.PENDING, 'Pending', 'PendingDetail'],
    [WORK_LIST_KINDS.PROCESSED, 'WorkProcessed', 'ProcessedDetail'],
    [WORK_LIST_KINDS.STARTED, 'WorkStarted', 'StartedDetail'],
    [WORK_LIST_KINDS.COPIED, 'WorkCopied', 'CopiedDetail'],
  ])('列表与详情路径都能解析到对应路由：%s', (kind, listName, detailName) => {
    expect(resolve(workListPath(kind)).name).toBe(listName)
    expect(resolve(workListPath(kind)).meta.workListKind).toBe(kind)
    const detail = resolve(workDetailPath(kind, { id: 12, taskId: 'task-9' }))
    expect(detail.name).toBe(detailName)
    expect(detail.meta.workListKind).toBe(kind)
  })

  it('显式构造器都落在已声明的路由上', () => {
    expect(resolve(startApplyPath(11)).name).toBe('WorkStartApply')
    expect(resolve(processTemplateListPath()).name).toBe('ProcessTemplate')
    expect(resolve(templateSetPath()).name).toBe('TemplateSet')
    expect(resolve(templateSetPath('A')).query).toEqual({ id: 'A' })
    const auth = resolve(assignAuthPath(3, '审批人'))
    expect(auth.name).toBe('AssignAuth')
    expect(auth.query).toEqual({ id: '3', roleName: '审批人' })
    expect(resolve(processInstanceDetailPath(12)).name).toBe('ProcessMgrDetail')
    expect(resolve(sysRolePath()).name).toBe('SysRole')
    expect(resolve(PATHS.NOTICE).name).toBe('NoticeList')
    expect(resolve(noticeDetailPath(19)).name).toBe('NoticeDetail')
    expect(resolve(noticeDetailPath(19)).params.id).toBe('19')
  })

  it('公告是内置读面：不进配置组、不套页面授予', () => {
    expect(resolve(PATHS.NOTICE).meta.menuGroup).toBeUndefined()
    expect(resolve(PATHS.NOTICE).meta.title).toBe('全部公告')
    expect(resolve(noticeDetailPath(1)).meta.menuGroup).toBeUndefined()
    expect(resolve(noticeDetailPath(1)).meta.title).toBe('公告详情')
  })

  it('公告详情返回全部公告', () => {
    expect(returnPathOfRoute(PATHS.NOTICE)).toBe(PATHS.NOTICE)
    expect(returnPathOfRoute(noticeDetailPath(19))).toBe(PATHS.NOTICE)
  })

  it('监控占位、工具目录与接口文档页都返回自身', () => {
    expect(returnPathOfRoute(PATHS.MONITOR)).toBe(PATHS.MONITOR)
    expect(returnPathOfRoute(PATHS.TOOL)).toBe(PATHS.TOOL)
    expect(returnPathOfRoute(PATHS.TOOL_API_DOC)).toBe(PATHS.TOOL_API_DOC)
  })

  it('公告发布管理页的列表与编辑路径落在配置组', () => {
    expect(resolve(PATHS.SYSTEM_NOTICE).name).toBe('SysNotice')
    expect(resolve(PATHS.SYSTEM_NOTICE).meta).toMatchObject({
      title: '通知公告',
      menuGroup: 'config',
    })
    expect(resolve(noticeEditPath()).name).toBe('SysNoticeEdit')
    expect(resolve(noticeEditPath()).params.id).toBeUndefined()
    expect(resolve(noticeEditPath(7)).name).toBe('SysNoticeEdit')
    expect(resolve(noticeEditPath(7)).params.id).toBe('7')
    expect(resolve(noticeEditPath(7)).meta).toMatchObject({
      title: '编辑公告',
      menuGroup: 'config',
    })
    expect(returnPathOfRoute(PATHS.SYSTEM_NOTICE)).toBe(PATHS.SYSTEM_NOTICE)
    expect(returnPathOfRoute(noticeEditPath(7))).toBe(PATHS.SYSTEM_NOTICE)
  })

  it('静态路径都能解析，隐藏页的可见父级来自同一份声明', () => {
    const staticPaths = [
      PATHS.WORKBENCH,
      PATHS.MONITOR,
      PATHS.TOOL,
      PATHS.TOOL_API_DOC,
      PATHS.WORK_START,
      PATHS.WORK_PENDING,
      PATHS.WORK_PROCESSED,
      PATHS.WORK_STARTED,
      PATHS.WORK_COPIED,
      PATHS.PROFILE,
      PATHS.SYSTEM_SYS_USER,
      PATHS.SYSTEM_SYS_ROLE,
      PATHS.SYSTEM_SYS_MENU,
      PATHS.SYSTEM_LOG_OPER,
      PATHS.SYSTEM_LOG_LOGIN,
      PATHS.SYSTEM_ASSIGN_AUTH,
      PATHS.PROCESS_SET_PROCESS_TYPE,
      PATHS.PROCESS_SET_PROCESS_TEMPLATE,
      PATHS.PROCESS_SET_TEMPLATE_SET,
      PATHS.PROCESS_MGR_PROCESS,
    ]
    for (const path of staticPaths) expect(resolve(path).matched.length).toBeGreaterThan(0)
    expect(resolve(PATHS.SYSTEM_ASSIGN_AUTH).meta.hiddenParentPath).toBe(PATHS.SYSTEM_SYS_ROLE)
    expect(resolve(PATHS.PROCESS_SET_TEMPLATE_SET).meta.hiddenParentPath).toBe(
      PATHS.PROCESS_SET_PROCESS_TEMPLATE,
    )
  })

  it('审批页面挂在系统管理下，旧根路径不再注册', () => {
    expect(PATHS.PROCESS_SET).toBe('/system/processSet')
    expect(PATHS.PROCESS_MGR).toBe('/system/processMgr')
    expect(resolve('/system/processSet/processType').name).toBe('ProcessType')
    expect(resolve('/system/processSet/processTemplate').name).toBe('ProcessTemplate')
    expect(resolve('/system/processMgr/process').name).toBe('ProcessMgr')
    expect(resolve('/processSet/processType').matched).toEqual([])
    expect(resolve('/processSet/processTemplate').matched).toEqual([])
    expect(resolve('/processMgr/process').matched).toEqual([])
  })

  it('工作台路由在壳内，返回路径是自身', () => {
    expect(resolve(PATHS.WORKBENCH).name).toBe('Workbench')
    expect(resolve(PATHS.WORKBENCH).meta.title).toBe('工作台')
    expect(returnPathOfRoute(PATHS.WORKBENCH)).toBe(PATHS.WORKBENCH)
  })

  it('返回路径覆盖列表、详情、隐藏页与未知路径', () => {
    expect(returnPathOfRoute(PATHS.WORK_PENDING)).toBe(PATHS.WORK_PENDING)
    expect(returnPathOfRoute('/work/pending/12/task-9')).toBe(PATHS.WORK_PENDING)
    expect(returnPathOfRoute('/work/processed/12')).toBe(PATHS.WORK_PROCESSED)
    expect(returnPathOfRoute('/work/started/12')).toBe(PATHS.WORK_STARTED)
    expect(returnPathOfRoute('/work/copied/23')).toBe(PATHS.WORK_COPIED)
    expect(returnPathOfRoute('/work/start/11')).toBe(PATHS.WORK_START)
    expect(returnPathOfRoute(PATHS.SYSTEM_SYS_USER)).toBe(PATHS.SYSTEM_SYS_USER)
    expect(returnPathOfRoute(PATHS.SYSTEM_LOG_OPER)).toBe(PATHS.SYSTEM_LOG_OPER)
    expect(returnPathOfRoute(PATHS.SYSTEM_LOG_LOGIN)).toBe(PATHS.SYSTEM_LOG_LOGIN)
    expect(returnPathOfRoute('/system/processMgr/process/9')).toBe(PATHS.PROCESS_MGR_PROCESS)
    expect(returnPathOfRoute(PATHS.SYSTEM_ASSIGN_AUTH)).toBe(PATHS.SYSTEM_SYS_ROLE)
    expect(returnPathOfRoute(PATHS.PROCESS_SET_TEMPLATE_SET)).toBe(
      PATHS.PROCESS_SET_PROCESS_TEMPLATE,
    )
    expect(returnPathOfRoute('/unknown')).toBe(PATHS.WORK_PENDING)
  })

  it('详情视角从路由 meta 得到 kind 与返回路径', () => {
    for (const kind of Object.values(WORK_LIST_KINDS)) {
      expect(
        workListPerspectiveFromRoute({ path: workListPath(kind), meta: { workListKind: kind } }),
      ).toMatchObject({ kind, returnPath: workListPath(kind) })
    }
    expect(
      workListPerspectiveFromRoute({ path: '/system/processMgr/process/5', meta: {} }),
    ).toEqual({
      kind: null,
      title: '',
      returnPath: PATHS.PROCESS_MGR_PROCESS,
      readOnly: true,
      emphasis: '',
    })
    expect(workListKindOf({ workListKind: 'pending' })).toBe('pending')
    expect(workListKindOf({ workListKind: 'unknown' })).toBeNull()
    expect(workListKindOf(undefined)).toBeNull()
  })
})
