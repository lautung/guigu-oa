import { ROUTES } from '@/navigation/constants'
import type { EntityId } from '@/types/domain'

export function applyPath(templateId: EntityId): string {
  return `${ROUTES.START}/${templateId}`
}
