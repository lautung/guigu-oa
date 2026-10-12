import Cookies from 'js-cookie'

const TokenKey = 'guigu_oa_client_token'
const RefreshTokenKey = 'guigu_oa_client_refresh_token'

export function getToken() {
  return Cookies.get(TokenKey)
}

export function setToken(token) {
  return Cookies.set(TokenKey, token)
}

export function getRefreshToken() {
  return Cookies.get(RefreshTokenKey)
}

export function setRefreshToken(token) {
  return Cookies.set(RefreshTokenKey, token)
}

export function removeRefreshToken() {
  return Cookies.remove(RefreshTokenKey)
}

export function removeToken() {
  Cookies.remove(TokenKey)
  return removeRefreshToken()
}
