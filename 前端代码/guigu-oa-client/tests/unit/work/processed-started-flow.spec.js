import {
  isReadOnlyDetailRoute
} from '@/work/pending-flow'

describe('work/processed-started-flow', () => {
  describe('isReadOnlyDetailRoute', () => {
    it('treats processed, started, and 审批实例详情 routes as read-only', () => {
      expect(isReadOnlyDetailRoute('ProcessedDetail')).toBe(true)
      expect(isReadOnlyDetailRoute('StartedDetail')).toBe(true)
      expect(isReadOnlyDetailRoute('ProcessMgrDetail')).toBe(true)
      expect(isReadOnlyDetailRoute('PendingDetail')).toBe(false)
    })
  })
})
