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

const HOME = { title: '首页', path: '/workbench' }

describe('面包屑', () => {
  it('renders 首页 / 一级 / 页面 for built-in 办事 pages', () => {
    expect(buildBreadcrumb({ path: '/work/pending', title: '待处理' })).toEqual([
      HOME,
      { title: '办事' },
      { title: '待处理' },
    ])
  })

  it('falls detail routes back to the list page trail and keeps the list clickable', () => {
    expect(buildBreadcrumb({ path: '/work/pending/9/task-1', title: '审批详情' })).toEqual([
      HOME,
      { title: '办事' },
      { title: '待处理', path: '/work/pending' },
    ])
    expect(buildBreadcrumb({ path: '/work/start/11', title: '发起审批' })).toEqual([
      HOME,
      { title: '办事' },
      { title: '发起审批', path: '/work/start' },
    ])
  })

  it('renders 首页 / 工作台 for the workbench and notice pages', () => {
    expect(buildBreadcrumb({ path: '/workbench', title: '工作台' })).toEqual([
      { title: '首页' },
      { title: '工作台' },
    ])
    expect(buildBreadcrumb({ path: '/notice', title: '全部公告' })).toEqual([
      HOME,
      { title: '工作台' },
      { title: '全部公告' },
    ])
    expect(buildBreadcrumb({ path: '/notice/8', title: '公告详情' })).toEqual([
      HOME,
      { title: '工作台' },
      { title: '全部公告', path: '/notice' },
    ])
  })

  it('uses the menu parentPath chain for 系统管理 pages', () => {
    expect(
      buildBreadcrumb({
        path: '/system/log/operLog',
        title: '操作日志',
        menus: SYSTEM_MENUS,
      }),
    ).toEqual([HOME, { title: '系统管理' }, { title: '日志管理' }, { title: '操作日志' }])
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
    ).toEqual([HOME, { title: '系统管理' }, { title: '用户管理' }])
  })

  it('falls hidden pages back to their visible parent trail and keeps the parent clickable', () => {
    expect(
      buildBreadcrumb({
        path: '/system/assignAuth',
        title: '菜单授权',
        menus: SYSTEM_MENUS,
      }),
    ).toEqual([HOME, { title: '系统管理' }, { title: '角色管理', path: '/system/sysRole' }])
    expect(
      buildBreadcrumb({
        path: '/system/processSet/templateSet',
        title: '审批模板设置',
        menus: SYSTEM_MENUS,
      }),
    ).toEqual([
      HOME,
      { title: '系统管理' },
      { title: '审批设置' },
      { title: '审批模板', path: '/system/processSet/processTemplate' },
    ])
  })

  it('appends the route title when the menus do not cover the page', () => {
    expect(
      buildBreadcrumb({ path: '/system/sysUser', title: '用户管理', menus: SYSTEM_MENUS }),
    ).toEqual([HOME, { title: '系统管理' }, { title: '用户管理' }])
  })

  it('renders 首页 / 一级 for the empty 监控 and 工具 modules', () => {
    expect(buildBreadcrumb({ path: '/monitor', title: '系统监控' })).toEqual([
      HOME,
      { title: '系统监控' },
    ])
    expect(buildBreadcrumb({ path: '/tool', title: '系统工具' })).toEqual([
      HOME,
      { title: '系统工具' },
    ])
  })

  it('renders only 首页 for unknown paths and keeps the fallback list clickable', () => {
    expect(buildBreadcrumb({ path: '/unknown', title: '' })).toEqual([
      HOME,
      { title: '办事' },
      { title: '待处理', path: '/work/pending' },
    ])
  })
})
