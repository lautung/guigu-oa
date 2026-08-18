import {
  AUTH_WHITELIST,
  ROUTES,
  buildLoginRedirectPath,
  getDefaultPostLoginRoute,
  resolveAuthGuard
} from '@/navigation/access-policy'

describe('navigation/access-policy', () => {
  describe('getDefaultPostLoginRoute', () => {
    it('returns pending as the default post-login landing', () => {
      expect(getDefaultPostLoginRoute()).toBe(ROUTES.PENDING)
    })
  })

  describe('buildLoginRedirectPath', () => {
    it('uses intended path when provided', () => {
      expect(buildLoginRedirectPath('/work/pending')).toBe('/work/pending')
    })

    it('falls back to pending when no intended path', () => {
      expect(buildLoginRedirectPath()).toBe(ROUTES.PENDING)
      expect(buildLoginRedirectPath('')).toBe(ROUTES.PENDING)
    })
  })

  describe('resolveAuthGuard', () => {
    it('redirects unauthenticated users to login with return path', () => {
      expect(resolveAuthGuard({ path: ROUTES.PENDING, hasToken: false })).toEqual({
        action: 'redirect',
        to: `${ROUTES.LOGIN}?redirect=${encodeURIComponent(ROUTES.PENDING)}`
      })
    })

    it('allows login page without token', () => {
      expect(resolveAuthGuard({ path: ROUTES.LOGIN, hasToken: false })).toEqual({
        action: 'allow'
      })
    })

    it('redirects authenticated users away from login to pending', () => {
      expect(resolveAuthGuard({ path: ROUTES.LOGIN, hasToken: true })).toEqual({
        action: 'redirect',
        to: ROUTES.PENDING
      })
    })

    it('allows authenticated access to protected routes', () => {
      expect(resolveAuthGuard({ path: ROUTES.PENDING, hasToken: true })).toEqual({
        action: 'allow'
      })
    })

    it('only whitelists login route', () => {
      expect(AUTH_WHITELIST).toEqual([ROUTES.LOGIN])
    })
  })
})
