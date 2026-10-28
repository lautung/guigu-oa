import type { ApiClient, ApiRequestConfig, Profile } from '@/types/domain'

export function getCurrentUserRequest(): ApiRequestConfig {
  return {
    method: 'get',
    url: '/admin/process/getCurrentUser',
  }
}

export function presentProfile(record: Partial<Profile> = {}): Profile {
  return {
    name: record.name || '',
    phone: record.phone || '',
  }
}

export async function readProfile(request: ApiClient): Promise<Profile> {
  const response = await request<Profile>(getCurrentUserRequest())
  return presentProfile(response.data || {})
}
