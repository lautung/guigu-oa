import { ROUTES } from '@/navigation/access-policy'
import {
  buildApprovalPayload,
  pendingDetailPath,
  postApprovalRoute,
  presentDetail,
  presentListItem,
  presentListPage,
  statusLabel,
  summaryFields
} from '@/work/pending-flow'

const FORM_VALUES = JSON.stringify({
  formShowData: {
    请假天数: '3',
    请假原因: '家中有事',
    图片: '<img src="https://example.com/a.png" />'
  }
})

describe('work/pending-flow', () => {
  describe('statusLabel', () => {
    it('maps the three process statuses to 审批中 / 审批通过 / 审批拒绝', () => {
      expect(statusLabel(1)).toBe('审批中')
      expect(statusLabel(2)).toBe('审批通过')
      expect(statusLabel(-1)).toBe('审批拒绝')
    })
  })

  describe('summaryFields', () => {
    it('keeps formShowData text fields and hides image fields in the list', () => {
      expect(summaryFields({
        请假天数: '3',
        请假原因: '家中有事',
        图片: '<img src="https://example.com/a.png" />'
      })).toEqual([
        { key: '请假天数', value: '3' },
        { key: '请假原因', value: '家中有事' }
      ])
    })
  })

  describe('presentListItem', () => {
    it('presents a pending list row with title, time, status, and list-safe summary fields', () => {
      const item = presentListItem({
        id: 12,
        taskId: 'task-9',
        title: '张三提交的请假申请',
        createTime: '2026-08-18 10:00:00',
        status: 1,
        formValues: FORM_VALUES
      })

      expect(item).toEqual({
        id: 12,
        taskId: 'task-9',
        title: '张三提交的请假申请',
        createTime: '2026-08-18 10:00:00',
        status: 1,
        statusLabel: '审批中',
        statusTagType: 'warning',
        summaryFields: [
          { key: '请假天数', value: '3' },
          { key: '请假原因', value: '家中有事' }
        ],
        detailPath: '/work/pending/12/task-9'
      })
    })
  })

  describe('presentListPage', () => {
    it('maps a paginated findPending payload into presentable rows', () => {
      const page = presentListPage({
        records: [{
          id: 12,
          taskId: 'task-9',
          title: '张三提交的请假申请',
          createTime: '2026-08-18 10:00:00',
          status: 1,
          formValues: FORM_VALUES
        }],
        total: 1,
        pages: 1
      })

      expect(page.total).toBe(1)
      expect(page.records).toHaveLength(1)
      expect(page.records[0].statusLabel).toBe('审批中')
      expect(page.records[0].summaryFields.map(field => field.key)).toEqual([
        '请假天数',
        '请假原因'
      ])
    })
  })

  describe('presentDetail', () => {
    it('presents title, time, status, all form fields, and vertical flow records', () => {
      const detail = presentDetail({
        process: {
          id: 12,
          title: '张三提交的请假申请',
          createTime: '2026-08-18 10:00:00',
          status: 1,
          formValues: FORM_VALUES
        },
        processRecordList: [
          {
            operateUser: '张三',
            description: '发起申请',
            createTime: '2026-08-18 10:00:00'
          },
          {
            operateUser: '李四',
            description: '已通过',
            createTime: '2026-08-18 11:00:00'
          }
        ],
        isApprove: true
      })

      expect(detail.title).toBe('张三提交的请假申请')
      expect(detail.createTime).toBe('2026-08-18 10:00:00')
      expect(detail.statusLabel).toBe('审批中')
      expect(detail.statusTagType).toBe('warning')
      expect(detail.formFields).toEqual([
        { key: '请假天数', value: '3' },
        { key: '请假原因', value: '家中有事' },
        { key: '图片', value: '<img src="https://example.com/a.png" />' }
      ])
      expect(detail.records).toEqual([
        { operateUser: '张三', description: '发起申请', createTime: '2026-08-18 10:00:00' },
        { operateUser: '李四', description: '已通过', createTime: '2026-08-18 11:00:00' }
      ])
      expect(detail.canApprove).toBe(true)
    })
  })

  describe('approve / reject', () => {
    it('builds an approve payload with status only — no rejection reason', () => {
      expect(buildApprovalPayload({
        processId: 12,
        taskId: 'task-9',
        status: 1
      })).toEqual({
        processId: 12,
        taskId: 'task-9',
        status: 1
      })

      expect(buildApprovalPayload({
        processId: 12,
        taskId: 'task-9',
        status: -1,
        reason: '不同意',
        description: '请补充材料'
      })).toEqual({
        processId: 12,
        taskId: 'task-9',
        status: -1
      })
    })

    it('sends the user to the processed list after approve or reject', () => {
      expect(postApprovalRoute()).toBe(ROUTES.PROCESSED)
      expect(postApprovalRoute()).toBe('/work/processed')
    })
  })

  describe('pendingDetailPath', () => {
    it('opens a pending item at the work detail route', () => {
      expect(pendingDetailPath(12, 'task-9')).toBe('/work/pending/12/task-9')
    })
  })
})
