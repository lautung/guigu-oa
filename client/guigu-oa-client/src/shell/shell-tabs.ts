import { PATHS } from '@/router/paths'

/**
 * 标签只存路径与标题（ADR 0051/0059）：不做 keep-alive、不持久化。
 * 路径用 fullPath，查询参数（例如模板编辑的 id）在切回标签时不丢。
 */
export interface ShellTab {
  path: string
  title: string
}

export interface TabCloseOutcome {
  tabs: ShellTab[]
  activePath: string
}

export function isFixedTab(path: string): boolean {
  return (path.split('?')[0].split('#')[0] || '') === PATHS.WORKBENCH
}

export function workbenchTab(): ShellTab {
  return { path: PATHS.WORKBENCH, title: '工作台' }
}

export function initialShellTabs(): ShellTab[] {
  return [workbenchTab()]
}

/** 打开或聚焦：已有同路径标签只更新标题，不重复追加。 */
export function openShellTab(tabs: readonly ShellTab[], tab: ShellTab): ShellTab[] {
  const path = isFixedTab(tab.path) ? PATHS.WORKBENCH : tab.path
  const existing = tabs.findIndex((candidate) => candidate.path === path)
  if (existing < 0) return [...tabs, { ...tab, path }]
  if (tabs[existing].title === tab.title) return [...tabs]
  return tabs.map((candidate, index) =>
    index === existing ? { ...candidate, title: tab.title } : candidate,
  )
}

/** 关闭单个标签；工作台固定不可关。关闭当前时激活右邻，其次左邻，最后工作台。 */
export function closeShellTab(
  tabs: readonly ShellTab[],
  activePath: string,
  path: string,
): TabCloseOutcome {
  const index = tabs.findIndex((candidate) => candidate.path === path)
  if (index < 0 || isFixedTab(path)) return { tabs: [...tabs], activePath }

  const remaining = tabs.filter((_, candidateIndex) => candidateIndex !== index)
  if (path !== activePath) return { tabs: remaining, activePath }

  const next = remaining[index] || remaining[index - 1] || workbenchTab()
  return { tabs: remaining, activePath: next.path }
}

export function closeCurrentTab(tabs: readonly ShellTab[], activePath: string): TabCloseOutcome {
  return closeShellTab(tabs, activePath, activePath)
}

export function closeOtherTabs(tabs: readonly ShellTab[], activePath: string): TabCloseOutcome {
  const remaining = tabs.filter(
    (candidate) => isFixedTab(candidate.path) || candidate.path === activePath,
  )
  const keptActive = remaining.some((candidate) => candidate.path === activePath)
  return {
    tabs: remaining,
    activePath: keptActive ? activePath : PATHS.WORKBENCH,
  }
}

export function closeAllTabs(tabs: readonly ShellTab[]): TabCloseOutcome {
  return {
    tabs: tabs.filter((candidate) => isFixedTab(candidate.path)),
    activePath: PATHS.WORKBENCH,
  }
}
