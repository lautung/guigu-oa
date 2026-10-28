import { describe, expect, it } from 'vitest'
import { emptyQuery, listRequest, presentListPage, presentQuery } from '@/config/process-instance'

const FORM_VALUES = JSON.stringify({
  formShowData: {
    请假天数: '3',
    请假原因: '家中有事',
  },
})

describe('config/process-instance', () => {
  describe('presentListPage', () => {
    it('presents a paginated 审批实例 list with spec status labels', () => {
      expect(
        presentListPage({
          records: [
            {
              id: 12,
              processCode: '1690000000000',
              title: '张三发起请假申请',
              name: '张三',
              processTypeName: '请假',
              processTemplateName: '事假',
              description: '等待李四审批',
              status: 1,
              createTime: '2026-08-01 10:00:00',
              formValues: FORM_VALUES,
            },
          ],
          total: 1,
          pages: 1,
        }),
      ).toEqual({
        records: [
          {
            id: 12,
            processCode: '1690000000000',
            title: '张三发起请假申请',
            name: '张三',
            processTypeName: '请假',
            processTemplateName: '事假',
            description: '等待李四审批',
            status: 1,
            statusLabel: '审批中',
            createTime: '2026-08-01 10:00:00',
            detailPath: '/processMgr/process/12',
          },
        ],
        total: 1,
        pages: 1,
      })
    })
  })

  describe('query', () => {
    it('starts with an empty keyword/status/time filter', () => {
      expect(emptyQuery()).toEqual({
        keyword: '',
        status: '',
        createTimeBegin: '',
        createTimeEnd: '',
      })
    })

    it('omits blank filters from the request params', () => {
      expect(
        presentQuery({
          keyword: '张三',
          status: '',
          createTimeBegin: '2026-08-01 00:00:00',
          createTimeEnd: '',
        }),
      ).toEqual({
        keyword: '张三',
        createTimeBegin: '2026-08-01 00:00:00',
      })
    })

    it('pages 审批实例 with GET /admin/process/{page}/{limit}', () => {
      expect(listRequest(1, 10, { keyword: '张三', status: 1 })).toEqual({
        method: 'get',
        url: '/admin/process/1/10',
        params: { keyword: '张三', status: 1 },
      })
    })
  })
})
