import { describe, expect, it } from 'vitest'
import {
  SHELL_MODULE_DEFINITIONS,
  activeModuleKey,
  buildShellNav,
  isShellLeafActive,
  lastTabPathForModule,
  moduleTitleOf,
} from '@/shell/shell-nav'

const SYSTEM_MENUS = [
  { path: '/system', title: '系统管理', type: 0, sort: 1 },
  { path: '/system/sysUser', parentPath: '/system', title: '用户管理', type: 1, sort: 1 },
  { path: '/system/log', parentPath: '/system', title: '日志管理', type: 0, sort: 2 },
  { path: '/system/log/operLog', parentPath: '/system/log', title: '操作日志', type: 1, sort: 1 },
  { path: '/system/log/loginLog', parentPath: '/system/log', title: '登录日志', type: 1, sort: 2 },
  { path: '/system/processSet', parentPath: '/system', title: '审批设置', type: 0, sort: 3 },
  {
    path: '/system/processSet/processType',
    parentPath: '/system/processSet',
    title: '审批类型',
    type: 1,
    sort: 1,
  },
  { path: '/system/processMgr', parentPath: '/system', title: '审批管理', type: 0, sort: 4 },
  {
    path: '/system/processMgr/process',
    parentPath: '/system/processMgr',
    title: '审批实例查询',
    type: 1,
    sort: 1,
  },
]

/** 真实菜单形态缺失目录行时的授予形状：页面直接挂在 /system 下。 */
const FLAT_SYSTEM_MENUS = [
  { path: '/system/log/operLog', title: '操作日志', type: 1, sort: 2 },
  { path: '/system/processSet/processTemplate', title: '审批模板', type: 1, sort: 3 },
  { path: '/system/sysUser', title: '用户管理', type: 1, sort: 1 },
]

describe('shell 一级导航', () => {
  it('defines the five top modules', () => {
    expect(SHELL_MODULE_DEFINITIONS.map((module) => module.title)).toEqual([
      '工作台',
      '办事',
      '系统管理',
      '系统监控',
      '系统工具',
    ])
  })

  it('always returns the five modules, even without any grant or page', () => {
    const modules = buildShellNav([])

    expect(modules.map((module) => module.key)).toEqual([
      'workbench',
      'work',
      'system',
      'monitor',
      'tool',
    ])
    const system = modules.find((module) => module.key === 'system')
    expect(system.items).toEqual([])
    expect(system.groups).toEqual([])
    expect(system.firstPath).toBe('')
    expect(system.emptyHint).toBe('暂无可用页面')
    expect(modules.find((module) => module.key === 'monitor').firstPath).toBe('/monitor')
    expect(modules.find((module) => module.key === 'tool').firstPath).toBe('/tool')
  })

  it('always exposes 工作台 entries and the six 办事 built-ins', () => {
    const modules = buildShellNav([])

    expect(modules[0].items.map((item) => item.title)).toEqual(['工作台', '全部公告'])
    expect(modules[1].items.map((item) => item.title)).toEqual([
      '发起审批',
      '待处理',
      '已处理',
      '已发起',
      '抄送我的',
      '基本信息',
    ])
    expect(modules[1].firstPath).toBe('/work/start')
  })

  it('splits direct 系统管理 pages from the three path-prefix groups', () => {
    const system = buildShellNav(SYSTEM_MENUS).find((module) => module.key === 'system')

    expect(system.items.map((item) => item.title)).toEqual(['用户管理'])
    expect(system.groups.map((group) => group.title)).toEqual(['日志管理', '审批设置', '审批管理'])
    expect(system.groups[0].items.map((item) => item.title)).toEqual(['操作日志', '登录日志'])
    expect(system.groups[1].items.map((item) => item.title)).toEqual(['审批类型'])
    expect(system.groups[2].items.map((item) => item.title)).toEqual(['审批实例查询'])
    expect(system.firstPath).toBe('/system/sysUser')
    expect(system.emptyHint).toBe('')
  })

  it('groups by path prefix even when menus omit the directory rows', () => {
    const system = buildShellNav(FLAT_SYSTEM_MENUS).find((module) => module.key === 'system')

    expect(system.items.map((item) => item.title)).toEqual(['用户管理'])
    expect(system.groups.map((group) => group.title)).toEqual(['日志管理', '审批设置'])
    expect(system.groups[0].items.map((item) => item.path)).toEqual(['/system/log/operLog'])
    expect(system.groups[1].items.map((item) => item.path)).toEqual([
      '/system/processSet/processTemplate',
    ])
  })

  it('keeps hidden pages out of the sidebar and falls 系统管理 to its granted pages', () => {
    const modules = buildShellNav([
      ...SYSTEM_MENUS,
      {
        path: '/system/assignAuth',
        parentPath: '/system/sysRole',
        title: '菜单授权',
        type: 1,
        hidden: true,
        sort: 9,
      },
    ])
    const system = modules.find((module) => module.key === 'system')
    const titles = [...system.items, ...system.groups.flatMap((group) => group.items)].map(
      (item) => item.title,
    )

    expect(titles).not.toContain('菜单授权')
    expect(titles).toContain('用户管理')
  })

  it('uses the top module title even when the menu root is called 组织与权限', () => {
    const modules = buildShellNav([
      { path: '/system', title: '组织与权限', type: 0, sort: 1 },
      { path: '/system/sysUser', parentPath: '/system', title: '用户管理', type: 1, sort: 1 },
    ])
    const system = modules.find((module) => module.key === 'system')

    expect(system.title).toBe('系统管理')
    expect(moduleTitleOf('system')).toBe('系统管理')
  })

  it('derives the active module from the path', () => {
    expect(activeModuleKey('/workbench')).toBe('workbench')
    expect(activeModuleKey('/notice')).toBe('workbench')
    expect(activeModuleKey('/notice/9')).toBe('workbench')
    expect(activeModuleKey('/work/pending')).toBe('work')
    expect(activeModuleKey('/work/pending/9/task-1')).toBe('work')
    expect(activeModuleKey('/system/sysUser')).toBe('system')
    expect(activeModuleKey('/system/processSet/templateSet')).toBe('system')
    expect(activeModuleKey('/monitor')).toBe('monitor')
    expect(activeModuleKey('/tool')).toBe('tool')
    expect(activeModuleKey('/unknown')).toBeNull()
    expect(activeModuleKey('/work/processed?page=2')).toBe('work')
  })

  it('finds the most recent tab for a module', () => {
    const tabs = [
      { path: '/workbench' },
      { path: '/work/pending' },
      { path: '/system/sysUser' },
      { path: '/work/processed' },
    ]

    expect(lastTabPathForModule(tabs, 'work')).toBe('/work/processed')
    expect(lastTabPathForModule(tabs, 'system')).toBe('/system/sysUser')
    expect(lastTabPathForModule(tabs, 'tool')).toBeNull()
  })

  it('activates a list item for its detail and hidden child routes', () => {
    expect(isShellLeafActive('/work/pending', '/work/pending')).toBe(true)
    expect(isShellLeafActive('/work/pending/9/task-1', '/work/pending')).toBe(true)
    expect(
      isShellLeafActive('/system/processSet/templateSet', '/system/processSet/processTemplate'),
    ).toBe(true)
    expect(isShellLeafActive('/system/assignAuth', '/system/sysRole')).toBe(true)
    expect(isShellLeafActive('/work/pending', '/work/processed')).toBe(false)
    expect(isShellLeafActive('/work/started', '/work/start')).toBe(false)
  })
})
