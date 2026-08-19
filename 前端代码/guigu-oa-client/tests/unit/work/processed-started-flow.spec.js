import { ROUTES } from '@/navigation/access-policy'
import {
  isReadOnlyDetailRoute,
  presentProcessedListItem,
  presentProcessedListPage,
  presentStartedListItem,
  presentStartedListPage,
  processedDetailPath,
  startedDetailPath
} from '@/work/pending-flow'

const FORM_VALUES = JSON.stringify({
  formShowData: {
    请假天数: '3',
    请假原因: '家中有事',
    图片: '<img src="https://example.com/a.png" />'
  }
})

describe('work/processed-started-flow', () => {
  describe('isReadOnlyDetailRoute', () => {
    it('treats processed, started, and 审批实例详情 routes as read-only', () => {
      expect(isReadOnlyDetailRoute('ProcessedDetail')).toBe(true)
      expect(isReadOnlyDetailRoute('StartedDetail')).toBe(true)
      expect(isReadOnlyDetailRoute('ProcessMgrDetail')).toBe(true)
      expect(isReadOnlyDetailRoute('PendingDetail')).toBe(false)
    })
  })

  describe('processedDetailPath', () => {
    it('opens a processed item at the read-only detail route without a task handle', () => {
      expect(processedDetailPath(12)).toBe('/work/processed/12')
    })
  })

  describe('startedDetailPath', () => {
    it('opens a started item at the read-only detail route without a task handle', () => {
      expect(startedDetailPath(12)).toBe('/work/started/12')
    })
  })

  describe('presentProcessedListItem', () => {
    it('presents a processed list row with a processed detail path', () => {
      const item = presentProcessedListItem({
        id: 12,
        title: '张三提交的请假申请',
        createTime: '2026-08-18 10:00:00',
        status: 2,
        formValues: FORM_VALUES
      })

      expect(item.detailPath).toBe(`${ROUTES.PROCESSED}/12`)
      expect(item.statusLabel).toBe('审批通过')
      expect(item.summaryFields.map(field => field.key)).toEqual(['请假天数', '请假原因'])
    })
  })

  describe('presentStartedListItem', () => {
    it('presents a started list row with a started detail path', () => {
      const item = presentStartedListItem({
        id: 12,
        title: '我提交的请假申请',
        createTime: '2026-08-18 10:00:00',
        status: 1,
        formValues: FORM_VALUES
      })

      expect(item.detailPath).toBe(`${ROUTES.STARTED}/12`)
      expect(item.statusLabel).toBe('审批中')
      expect(item.summaryFields.map(field => field.key)).toEqual(['请假天数', '请假原因'])
    })
  })

  describe('presentProcessedListPage', () => {
    it('maps a paginated findProcessed payload into presentable rows', () => {
      const page = presentProcessedListPage({
        records: [{
          id: 12,
          title: '张三提交的请假申请',
          createTime: '2026-08-18 10:00:00',
          status: 2,
          formValues: FORM_VALUES
        }],
        total: 1,
        pages: 1
      })

      expect(page.total).toBe(1)
      expect(page.records).toHaveLength(1)
      expect(page.records[0].detailPath).toBe('/work/processed/12')
    })
  })

  describe('presentStartedListPage', () => {
    it('maps a paginated findStarted payload into presentable rows', () => {
      const page = presentStartedListPage({
        records: [{
          id: 12,
          title: '我提交的请假申请',
          createTime: '2026-08-18 10:00:00',
          status: 1,
          formValues: FORM_VALUES
        }],
        total: 1,
        pages: 1
      })

      expect(page.total).toBe(1)
      expect(page.records).toHaveLength(1)
      expect(page.records[0].detailPath).toBe('/work/started/12')
    })
  })
})
