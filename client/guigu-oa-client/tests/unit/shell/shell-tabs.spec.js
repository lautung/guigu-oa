import { describe, expect, it } from 'vitest'
import {
  closeAllTabs,
  closeCurrentTab,
  closeOtherTabs,
  closeShellTab,
  initialShellTabs,
  isFixedTab,
  openShellTab,
  workbenchTab,
} from '@/shell/shell-tabs'

const tab = (path, title = path) => ({ path, title })

describe('标签状态', () => {
  it('starts with the fixed workbench tab', () => {
    const tabs = initialShellTabs()

    expect(tabs).toEqual([{ path: '/workbench', title: '工作台' }])
    expect(isFixedTab('/workbench')).toBe(true)
    expect(isFixedTab('/workbench?tab=1')).toBe(true)
    expect(isFixedTab('/work/pending')).toBe(false)
  })

  it('opens a tab once and keeps the first order', () => {
    let tabs = initialShellTabs()
    tabs = openShellTab(tabs, tab('/work/pending', '待处理'))
    tabs = openShellTab(tabs, tab('/system/sysUser', '用户管理'))
    tabs = openShellTab(tabs, tab('/work/pending', '待处理'))

    expect(tabs.map((item) => item.path)).toEqual([
      '/workbench',
      '/work/pending',
      '/system/sysUser',
    ])
  })

  it('updates the title in place without reordering', () => {
    const tabs = openShellTab(
      [workbenchTab(), tab('/notice/1', '公告详情')],
      tab('/notice/1', '公告：维护'),
    )

    expect(tabs.map((item) => item.title)).toEqual(['工作台', '公告：维护'])
  })

  it('keeps the query string in the tab path', () => {
    const tabs = openShellTab(
      initialShellTabs(),
      tab('/system/processSet/templateSet?id=7', '审批模板设置'),
    )

    expect(tabs[1].path).toBe('/system/processSet/templateSet?id=7')
  })

  it('closing the active tab activates the right neighbour, then the left, then workbench', () => {
    const tabs = [workbenchTab(), tab('/a', 'A'), tab('/b', 'B'), tab('/c', 'C')]

    const right = closeShellTab(tabs, '/b', '/b')
    expect(right.tabs.map((item) => item.path)).toEqual(['/workbench', '/a', '/c'])
    expect(right.activePath).toBe('/c')

    const left = closeShellTab(right.tabs, '/c', '/c')
    expect(left.tabs.map((item) => item.path)).toEqual(['/workbench', '/a'])
    expect(left.activePath).toBe('/a')

    const workbench = closeShellTab([workbenchTab(), tab('/a', 'A')], '/a', '/a')
    expect(workbench.tabs).toEqual([workbenchTab()])
    expect(workbench.activePath).toBe('/workbench')
  })

  it('closing a background tab does not move the active tab', () => {
    const outcome = closeShellTab([workbenchTab(), tab('/a', 'A'), tab('/b', 'B')], '/b', '/a')

    expect(outcome.tabs.map((item) => item.path)).toEqual(['/workbench', '/b'])
    expect(outcome.activePath).toBe('/b')
  })

  it('never closes the fixed workbench tab', () => {
    const tabs = [workbenchTab(), tab('/a', 'A')]

    expect(closeShellTab(tabs, '/workbench', '/workbench')).toEqual({
      tabs,
      activePath: '/workbench',
    })
  })

  it('closes the current tab through the same semantics', () => {
    const outcome = closeCurrentTab([workbenchTab(), tab('/a', 'A'), tab('/b', 'B')], '/a')

    expect(outcome.tabs.map((item) => item.path)).toEqual(['/workbench', '/b'])
    expect(outcome.activePath).toBe('/b')
  })

  it('closing others keeps workbench and the active tab', () => {
    const outcome = closeOtherTabs(
      [workbenchTab(), tab('/a', 'A'), tab('/b', 'B'), tab('/c', 'C')],
      '/b',
    )

    expect(outcome.tabs.map((item) => item.path)).toEqual(['/workbench', '/b'])
    expect(outcome.activePath).toBe('/b')
  })

  it('closing others on workbench leaves only workbench', () => {
    const outcome = closeOtherTabs([workbenchTab(), tab('/a', 'A')], '/workbench')

    expect(outcome.tabs).toEqual([workbenchTab()])
    expect(outcome.activePath).toBe('/workbench')
  })

  it('closing all returns to workbench', () => {
    const outcome = closeAllTabs([workbenchTab(), tab('/a', 'A'), tab('/b', 'B')])

    expect(outcome.tabs).toEqual([workbenchTab()])
    expect(outcome.activePath).toBe('/workbench')
  })
})
