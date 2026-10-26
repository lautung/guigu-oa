import { applyPath } from '@/work/start-flow'

describe('发起模板入口', () => {
  it('opens a template at its 发起审批 form route', () => {
    expect(applyPath(11)).toBe('/work/start/11')
  })
})
