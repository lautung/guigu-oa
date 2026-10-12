import Cookies from 'js-cookie'

const TokenKey = 'guigu_oa_client_token'
const RefreshTokenKey = 'guigu_oa_client_refresh_token'

export function getToken(): string | undefined {
  return Cookies.get(TokenKey)
}

export function setToken(token: string): void {
  Cookies.set(TokenKey, token)
}

export function getRefreshToken(): string | undefined {
  return Cookies.get(RefreshTokenKey)
}

export function setRefreshToken(token: string): void {
  Cookies.set(RefreshTokenKey, token)
}

export function removeRefreshToken(): void {
  return Cookies.remove(RefreshTokenKey)
}

export function removeToken(): void {
  Cookies.remove(TokenKey)
  return removeRefreshToken()
}
