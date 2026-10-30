import { describe, expect, it } from 'vitest'
import { requiredRule } from '@/config/form-rules'

function verdictOf(rule, value) {
  let error
  rule.validator({}, value, (result) => {
    error = result
  })
  return error
}

describe('config/form-rules', () => {
  it('marks the rule as required so Element Plus renders the asterisk', () => {
    expect(requiredRule('用户名不能为空')).toMatchObject({ required: true })
  })

  it('rejects empty, whitespace-only, and missing strings with the given message', () => {
    const rule = requiredRule('用户名不能为空')
    for (const value of ['', '   ', null, undefined]) {
      const error = verdictOf(rule, value)
      expect(error).toBeInstanceOf(Error)
      expect(error.message).toBe('用户名不能为空')
    }
  })

  it('accepts non-empty strings without trimming the value itself', () => {
    const rule = requiredRule('用户名不能为空')
    expect(verdictOf(rule, '张三')).toBeUndefined()
    expect(verdictOf(rule, ' 张三 ')).toBeUndefined()
  })

  it('treats numbers as empty only when missing, mirroring @NotNull', () => {
    const rule = requiredRule('审批类型不能为空')
    expect(verdictOf(rule, 0)).toBeUndefined()
    expect(verdictOf(rule, 2)).toBeUndefined()
    expect(verdictOf(rule, '')).toBeInstanceOf(Error)
    expect(verdictOf(rule, null)).toBeInstanceOf(Error)
  })
})
