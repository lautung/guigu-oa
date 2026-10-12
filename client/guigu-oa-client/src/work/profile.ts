import type { ApiRequestConfig, Profile } from '@/types/domain'

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
