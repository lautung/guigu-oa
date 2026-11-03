import { describe, expect, it } from 'vitest'
import { buildBreadcrumb } from '@/shell/breadcrumb'

const SYSTEM_MENUS = [
  { path: '/system', title: '系统管理', type: 0, sort: 1 },
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
  { path: '/system/log', parentPath: '/system', title: '日志管理', type: 0, sort: 2 },
  { path: '/system/log/operLog', parentPath: '/system/log', title: '操作日志', type: 1, sort: 1 },
  { path: '/system/processSet', parentPath: '/system', title: '审批设置', type: 0, sort: 3 },
  {
    path: '/system/processSet/processTemplate',
    parentPath: '/system/processSet',
    title: '审批模板',
    type: 1,
    sort: 1,
  },
  {
    path: '/system/processSet/templateSet',
    parentPath: '/system/processSet/processTemplate',
    title: '审批模板设置',
    type: 1,
    hidden: true,
    sort: 2,
  },
]

describe('面包屑', () => {
  it('renders 首页 / 一级 / 页面 for built-in 办事 pages', () => {
    expect(buildBreadcrumb({ path: '/work/pending', title: '待处理' })).toEqual([
      '首页',
      '办事',
      '待处理',
    ])
  })

  it('falls detail routes back to the list page trail', () => {
    expect(buildBreadcrumb({ path: '/work/pending/9/task-1', title: '审批详情' })).toEqual([
      '首页',
      '办事',
      '待处理',
    ])
    expect(buildBreadcrumb({ path: '/work/start/11', title: '发起审批' })).toEqual([
      '首页',
      '办事',
      '发起审批',
    ])
  })

  it('renders 首页 / 工作台 for the workbench and notice pages', () => {
    expect(buildBreadcrumb({ path: '/workbench', title: '工作台' })).toEqual(['首页', '工作台'])
    expect(buildBreadcrumb({ path: '/notice', title: '全部公告' })).toEqual([
      '首页',
      '工作台',
      '全部公告',
    ])
    expect(buildBreadcrumb({ path: '/notice/8', title: '公告详情' })).toEqual([
      '首页',
      '工作台',
      '全部公告',
    ])
  })

  it('uses the menu parentPath chain for 系统管理 pages', () => {
    expect(
      buildBreadcrumb({
        path: '/system/log/operLog',
        title: '操作日志',
        menus: SYSTEM_MENUS,
      }),
    ).toEqual(['首页', '系统管理', '日志管理', '操作日志'])
  })

  it('keeps the top module name when the menu root is called 组织与权限', () => {
    expect(
      buildBreadcrumb({
        path: '/system/sysUser',
        title: '用户管理',
        menus: [
          { path: '/system', title: '组织与权限', type: 0, sort: 1 },
          { path: '/system/sysUser', parentPath: '/system', title: '用户管理', type: 1, sort: 1 },
        ],
      }),
    ).toEqual(['首页', '系统管理', '用户管理'])
  })

  it('falls hidden pages back to their visible parent trail', () => {
    expect(
      buildBreadcrumb({
        path: '/system/assignAuth',
        title: '菜单授权',
        menus: SYSTEM_MENUS,
      }),
    ).toEqual(['首页', '系统管理', '角色管理'])
    expect(
      buildBreadcrumb({
        path: '/system/processSet/templateSet',
        title: '审批模板设置',
        menus: SYSTEM_MENUS,
      }),
    ).toEqual(['首页', '系统管理', '审批设置', '审批模板'])
  })

  it('appends the route title when the menus do not cover the page', () => {
    expect(
      buildBreadcrumb({ path: '/system/sysUser', title: '用户管理', menus: SYSTEM_MENUS }),
    ).toEqual(['首页', '系统管理', '用户管理'])
  })

  it('renders only 首页 for unknown paths', () => {
    expect(buildBreadcrumb({ path: '/unknown', title: '' })).toEqual(['首页', '办事', '待处理'])
  })
})
