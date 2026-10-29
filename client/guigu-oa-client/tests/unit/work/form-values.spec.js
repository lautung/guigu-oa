import { describe, expect, it } from 'vitest'
import { formFields, formFieldText } from '@/work/form-values'

describe('work/form-values', () => {
  describe('formFieldText', () => {
    it('turns every stored value into display text', () => {
      expect(formFieldText('<img src=x onerror="window.__secXss=1">')).toBe(
        '<img src=x onerror="window.__secXss=1">',
      )
      expect(formFieldText(3)).toBe('3')
      expect(formFieldText(false)).toBe('false')
      expect(formFieldText(undefined)).toBe('')
      expect(formFieldText(null)).toBe('')
      expect(formFieldText(['2026-01-01', '2026-01-02'])).toBe('2026-01-01、2026-01-02')
      expect(formFieldText(['a.png', '', null])).toBe('a.png')
      expect(formFieldText({ unit: '天' })).toBe('{"unit":"天"}')
    })
  })

  describe('formFields', () => {
    it('keeps the stored key order and renders every value as text', () => {
      expect(
        formFields({ 请假原因: '<b>出差</b>', 同行人: ['张三', '李四'], 请假天数: 2 }),
      ).toEqual([
        { key: '请假原因', value: '<b>出差</b>' },
        { key: '同行人', value: '张三、李四' },
        { key: '请假天数', value: '2' },
      ])
    })

    it('renders an absent form as no fields', () => {
      expect(formFields()).toEqual([])
    })
  })
})
