import { configPermission } from '@/config/permission'
import type { MenuGroup } from '@/config/permission'
import { getDefaultPostLoginRoute, ROUTES } from '@/navigation/constants'
import { returnPathOfRoute } from '@/router/paths'
import type { BackofficeMenuEntry } from '@/types/domain'

export const MENU_GROUPS = {
  WORK: 'work',
  CONFIG: 'config',
} as const

export const DENSITIES = {
  CONSTRAINED: 'constrained',
  WIDE: 'wide',
} as const

type Density = (typeof DENSITIES)[keyof typeof DENSITIES]
type Occupancy = { action: 'allow' } | { action: 'redirect'; to: string }

export interface ClientSurfaceInput {
  path?: string
  viewportWidth?: number
  hasToken?: boolean
  menus?: BackofficeMenuEntry[]
}

interface ClientSurfaceResolution {
  showSurface: boolean
  menuGroup: MenuGroup | null
  density: Density | null
  occupancy: Occupancy
  hasWorkListPerspective?: boolean
  returnPath?: string
}

function densityForMenuGroup(menuGroup: MenuGroup | null): Density | null {
  if (menuGroup === MENU_GROUPS.WORK) return DENSITIES.CONSTRAINED
  if (menuGroup === MENU_GROUPS.CONFIG) return DENSITIES.WIDE
  return null
}

const WORK_LIST_ENTRY_PATHS: readonly string[] = [ROUTES.PENDING, ROUTES.PROCESSED, ROUTES.STARTED]

function noSurface(): ClientSurfaceResolution {
  return {
    showSurface: false,
    menuGroup: null,
    density: null,
    occupancy: { action: 'allow' },
  }
}

function isLoginPath(path: string): boolean {
  return path.replace(/\/$/, '') === ROUTES.LOGIN
}

export function resolveClientSurface({
  path = '',
  viewportWidth = 1024,
  hasToken = false,
  menus = [],
}: ClientSurfaceInput = {}): ClientSurfaceResolution {
  if (isLoginPath(path)) {
    return noSurface()
  }

  const permission = configPermission(menus)
  const entry = permission.entryOf(path)
  if (!entry) {
    return noSurface()
  }

  const canOccupy =
    !hasToken || entry.group !== MENU_GROUPS.CONFIG || permission.canOccupy({ path, viewportWidth })

  return {
    showSurface: hasToken,
    menuGroup: entry.group,
    density: densityForMenuGroup(entry.group),
    occupancy: canOccupy
      ? { action: 'allow' }
      : { action: 'redirect', to: getDefaultPostLoginRoute() },
    hasWorkListPerspective: WORK_LIST_ENTRY_PATHS.includes(entry.path),
    returnPath: returnPathOfRoute(path),
  }
}

export function createViewportOccupancyHandler({
  getSurfaceInput,
  goTo,
}: {
  getSurfaceInput: () => ClientSurfaceInput
  goTo: (path: string) => void
}): (viewportWidth: number) => void {
  return function handleViewportWidth(viewportWidth: number): void {
    const input = getSurfaceInput()
    const occupancy = resolveClientSurface({ ...input, viewportWidth }).occupancy
    if (occupancy.action === 'redirect' && occupancy.to !== input.path) {
      goTo(occupancy.to)
    }
  }
}
