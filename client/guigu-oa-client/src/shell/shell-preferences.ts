export type ShellTheme = 'light' | 'dark'
export type ShellDensity = 'small' | 'default' | 'large'

export const SHELL_THEME_KEY = 'guigu_oa_shell_theme'
export const SHELL_DENSITY_KEY = 'guigu_oa_shell_density'

export const DEFAULT_SHELL_THEME: ShellTheme = 'light'
export const DEFAULT_SHELL_DENSITY: ShellDensity = 'small'

const THEMES: readonly ShellTheme[] = ['light', 'dark']
const DENSITIES: readonly ShellDensity[] = ['small', 'default', 'large']

type ShellStorage = Pick<Storage, 'getItem' | 'setItem'>

function safeStorage(): ShellStorage | null {
  try {
    return typeof localStorage === 'undefined' ? null : localStorage
  } catch {
    return null
  }
}

function isTheme(value: unknown): value is ShellTheme {
  return typeof value === 'string' && THEMES.includes(value as ShellTheme)
}

function isDensity(value: unknown): value is ShellDensity {
  return typeof value === 'string' && DENSITIES.includes(value as ShellDensity)
}

export interface ShellPreferences {
  theme: ShellTheme
  density: ShellDensity
}

export function readShellPreferences(
  storage: ShellStorage | null = safeStorage(),
): ShellPreferences {
  let theme: ShellTheme = DEFAULT_SHELL_THEME
  let density: ShellDensity = DEFAULT_SHELL_DENSITY

  try {
    const storedTheme = storage?.getItem(SHELL_THEME_KEY)
    const storedDensity = storage?.getItem(SHELL_DENSITY_KEY)
    if (isTheme(storedTheme)) theme = storedTheme
    if (isDensity(storedDensity)) density = storedDensity
  } catch {
    // 读取失败（隐私模式等）时用默认值，不影响进入外壳。
  }

  return { theme, density }
}

export function writeShellTheme(
  theme: ShellTheme,
  storage: ShellStorage | null = safeStorage(),
): void {
  try {
    storage?.setItem(SHELL_THEME_KEY, theme)
  } catch {
    // 写失败只影响下次启动的默认值。
  }
}

export function writeShellDensity(
  density: ShellDensity,
  storage: ShellStorage | null = safeStorage(),
): void {
  try {
    storage?.setItem(SHELL_DENSITY_KEY, density)
  } catch {
    // 写失败只影响下次启动的默认值。
  }
}

export function applyShellTheme(
  theme: ShellTheme,
  root: HTMLElement | null = typeof document === 'undefined' ? null : document.documentElement,
): void {
  if (!root) return
  root.classList.toggle('dark', theme === 'dark')
}
