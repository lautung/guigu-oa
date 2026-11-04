import { describe, expect, it } from 'vitest'
import { classifySubmissionFailure } from '@/request/submission-outcome'
import { BusinessRequestFailure, RequestFailure } from '@/request/request-failure'

const actions = [
  ['start-approval', [201, 205, 208, 209, 403]],
  ['process-approval', [201, 205, 208, 209, 403]],
  ['save-template', [201, 205, 208, 209, 403]],
  ['assign-roles', [205, 208, 209, 403]],
  ['assign-role-users', [201, 205, 208, 209, 403]],
]

function failure(detail) {
  return new RequestFailure('retryable', '请求失败', detail)
}

function business(code) {
  return failure({ source: 'business', operation: 'request', businessCode: code })
}

describe('提交失败证据', () => {
  it.each(actions)('%s 把写入前的拒绝判为 refused', (action, codes) => {
    for (const code of codes) {
      expect(classifySubmissionFailure(business(code), action)).toBe('refused')
    }
  })

  it('分配角色的 201 仍待确认，其余动作的 201 确定未提交', () => {
    expect(classifySubmissionFailure(business(201), 'assign-roles')).toBe('unconfirmed')
    expect(classifySubmissionFailure(business(201), 'start-approval')).toBe('refused')
    expect(classifySubmissionFailure(business(201), 'process-approval')).toBe('refused')
    expect(classifySubmissionFailure(business(201), 'save-template')).toBe('refused')
  })

  it('205 从 RequestFailureDetail 读取，不依赖会丢掉它的 businessCode', () => {
    const forbidden = new BusinessRequestFailure('forbidden', '没有操作权限', undefined, {
      source: 'business',
      operation: 'request',
      businessCode: 205,
    })
    expect(forbidden.businessCode).toBeUndefined()
    expect(classifySubmissionFailure(forbidden, 'start-approval')).toBe('refused')
    expect(classifySubmissionFailure(forbidden, 'assign-roles')).toBe('refused')
  })

  it.each(['start-approval', 'process-approval', 'save-template', 'assign-roles'])(
    '%s 把 refresh 失败判为 refused',
    (action) => {
      expect(
        classifySubmissionFailure(
          failure({ source: 'timeout', operation: 'refresh', originalBusinessCode: 208 }),
          action,
        ),
      ).toBe('refused')
      expect(
        classifySubmissionFailure(
          failure({
            source: 'business',
            operation: 'refresh',
            businessCode: 201,
            originalBusinessCode: 208,
          }),
          action,
        ),
      ).toBe('refused')
    },
  )

  it.each(['start-approval', 'process-approval', 'save-template', 'assign-roles'])(
    '%s 只把 HTTP 401/403 和业务拒绝当作确定未提交',
    (action) => {
      for (const httpStatus of [401, 403]) {
        expect(
          classifySubmissionFailure(
            failure({ source: 'http', operation: 'request', httpStatus }),
            action,
          ),
        ).toBe('refused')
      }
      for (const detail of [
        { source: 'http', operation: 'request', httpStatus: 500 },
        { source: 'http', operation: 'request', httpStatus: 400 },
        { source: 'timeout', operation: 'request' },
        { source: 'network', operation: 'request' },
        { source: 'unexpected', operation: 'request' },
        { source: 'business', operation: 'request', businessCode: 599 },
      ]) {
        expect(classifySubmissionFailure(failure(detail), action)).toBe('unconfirmed')
      }
    },
  )

  it('非 RequestFailure 的错误一律待确认', () => {
    expect(classifySubmissionFailure(new Error('网络错误'), 'save-template')).toBe('unconfirmed')
    expect(classifySubmissionFailure(undefined, 'assign-roles')).toBe('unconfirmed')
  })
})
