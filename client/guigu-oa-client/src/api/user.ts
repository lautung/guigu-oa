import request from '@/utils/request'
import type { ApiResponse, LoginCredentials, LoginTokens, UserInfo } from '@/types/domain'

export function login(data: LoginCredentials): Promise<ApiResponse<LoginTokens>> {
  return request({
    url: '/admin/system/index/login',
    method: 'post',
    data,
  })
}

export function getInfo(): Promise<ApiResponse<UserInfo>> {
  return request({
    url: '/admin/system/index/info',
    method: 'get',
  })
}
