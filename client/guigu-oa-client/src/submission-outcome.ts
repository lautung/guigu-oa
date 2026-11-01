import { RequestFailure } from '@/request-failure'

export type SubmissionAction =
  'start-approval' | 'process-approval' | 'save-template' | 'assign-roles'

export type SubmissionOutcome = 'refused' | 'unconfirmed'

// 201 is "not written" for every action except assign-roles: doAssign can return
// 201 from its after-commit permission-cache invalidation, after the role rows
// have committed. Evidence comes from RequestFailureDetail.businessCode because
// BusinessRequestFailure.businessCode drops 205.
const REFUSED_BUSINESS_CODES: Record<SubmissionAction, ReadonlySet<number>> = {
  'start-approval': new Set([201, 205, 208, 209, 403]),
  'process-approval': new Set([201, 205, 208, 209, 403]),
  'save-template': new Set([201, 205, 208, 209, 403]),
  'assign-roles': new Set([205, 208, 209, 403]),
}

export function classifySubmissionFailure(
  error: unknown,
  action: SubmissionAction,
): SubmissionOutcome {
  if (!(error instanceof RequestFailure)) return 'unconfirmed'
  const detail = error.detail
  // A refresh failure means the original request was refused with 208 before
  // any business handling, so it cannot have written anything.
  if (detail.operation === 'refresh') return 'refused'
  if (detail.source === 'business' && REFUSED_BUSINESS_CODES[action].has(detail.businessCode ?? 0))
    return 'refused'
  if (detail.source === 'http' && (detail.httpStatus === 401 || detail.httpStatus === 403))
    return 'refused'
  return 'unconfirmed'
}
