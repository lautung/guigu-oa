export const ROUTES = {
  LOGIN: '/login',
  PENDING: '/work/pending'
}

export const AUTH_WHITELIST = [ROUTES.LOGIN]

export function getDefaultPostLoginRoute() {
  return ROUTES.PENDING
}

export function buildLoginRedirectPath(intendedPath) {
  return intendedPath || getDefaultPostLoginRoute()
}

export function resolveAuthGuard({ path, fullPath, hasToken }) {
  const targetPath = fullPath || path

  if (hasToken) {
    if (path === ROUTES.LOGIN) {
      return { action: 'redirect', to: getDefaultPostLoginRoute() }
    }
    return { action: 'allow' }
  }

  if (AUTH_WHITELIST.includes(path)) {
    return { action: 'allow' }
  }

  return {
    action: 'redirect',
    to: `${ROUTES.LOGIN}?redirect=${encodeURIComponent(targetPath)}`
  }
}
