import { ROUTES } from '@/navigation/access-policy'
import {
  buildApprovalPayload,
  postApprovalRoute,
  presentDetail,
  statusLabel
} from '@/work/pending-flow'
import { WORK_LIST_KINDS, workListPerspective } from '@/work/work-list'

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

    it('keeps 待处理详情 actionable and 已处理详情 read-only using the list 视角', () => {
      const payload = {
        process: {
          id: 12,
          title: '张三提交的请假申请',
          createTime: '2026-08-18 10:00:00',
          status: 1,
          formValues: FORM_VALUES
        },
        processRecordList: [],
        isApprove: true
      }

      const pendingDetail = presentDetail(payload, workListPerspective(WORK_LIST_KINDS.PENDING))
      expect(pendingDetail.canApprove).toBe(true)
      expect(pendingDetail.perspective).toEqual({
        kind: WORK_LIST_KINDS.PENDING,
        title: '待处理',
        returnPath: '/work/pending',
        readOnly: false
      })

      const processedDetail = presentDetail(payload, workListPerspective(WORK_LIST_KINDS.PROCESSED))
      expect(processedDetail.canApprove).toBe(false)
      expect(processedDetail.title).toBe('张三提交的请假申请')
      expect(processedDetail.perspective).toEqual({
        kind: WORK_LIST_KINDS.PROCESSED,
        title: '已处理',
        returnPath: '/work/processed',
        readOnly: true
      })
    })

    it('forces read-only detail when opened from processed or started routes', () => {
      const payload = {
        process: {
          id: 12,
          title: '张三提交的请假申请',
          createTime: '2026-08-18 10:00:00',
          status: 1,
          formValues: FORM_VALUES
        },
        processRecordList: [],
        isApprove: true
      }

      expect(presentDetail(payload, { readOnly: true }).canApprove).toBe(false)
    })

    it('keeps form and flow records read-only when the old engine instance is gone', () => {
      const detail = presentDetail({
        process: {
          id: 12,
          title: '张三提交的请假申请',
          createTime: '2026-08-18 10:00:00',
          status: 2,
          processInstanceId: 'pre-cutover-instance-id',
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
        isApprove: false
      })

      expect(detail.canApprove).toBe(false)
      expect(detail.statusLabel).toBe('审批通过')
      expect(detail.formFields.map(field => field.key)).toEqual(['请假天数', '请假原因', '图片'])
      expect(detail.records).toHaveLength(2)
      expect(detail.records[0].description).toBe('发起申请')
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
})
