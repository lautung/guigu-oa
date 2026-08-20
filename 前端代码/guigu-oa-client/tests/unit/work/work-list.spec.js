import {
  WORK_LIST_KINDS,
  loadWorkList,
  presentWorkList,
  workListPerspective,
  workListRequest
} from '@/work/work-list'

const FORM_VALUES = JSON.stringify({
  formShowData: {
    请假天数: '3',
    请假原因: '家中有事',
    图片: '<img src="https://example.com/a.png" />'
  }
})

describe('work/work-list', () => {
  describe('workListRequest', () => {
    it('loads 待处理 with GET /admin/process/findPending/{page}/{limit}', () => {
      expect(workListRequest(WORK_LIST_KINDS.PENDING, 1, 10)).toEqual({
        method: 'get',
        url: '/admin/process/findPending/1/10'
      })
    })

    it('loads 已处理 with GET /admin/process/findProcessed/{page}/{limit}', () => {
      expect(workListRequest(WORK_LIST_KINDS.PROCESSED, 2, 5)).toEqual({
        method: 'get',
        url: '/admin/process/findProcessed/2/5'
      })
    })

    it('loads 已发起 with GET /admin/process/findStarted/{page}/{limit}', () => {
      expect(workListRequest(WORK_LIST_KINDS.STARTED, 1, 10)).toEqual({
        method: 'get',
        url: '/admin/process/findStarted/1/10'
      })
    })
  })

  describe('presentWorkList', () => {
    it('presents a 待处理 page with unified 审批 rows and a pending detail path', () => {
      const page = presentWorkList(WORK_LIST_KINDS.PENDING, {
        records: [{
          id: 12,
          taskId: 'task-9',
          title: '张三提交的请假申请',
          name: '张三',
          processTypeName: '请假',
          createTime: '2026-08-18 10:00:00',
          status: 1,
          formValues: FORM_VALUES
        }],
        total: 1,
        pages: 1
      })

      expect(page).toEqual({
        kind: WORK_LIST_KINDS.PENDING,
        title: '待处理',
        emptyDescription: '暂无待处理审批',
        total: 1,
        pages: 1,
        records: [{
          id: 12,
          taskId: 'task-9',
          title: '张三提交的请假申请',
          name: '张三',
          processTypeName: '请假',
          createTime: '2026-08-18 10:00:00',
          status: 1,
          statusLabel: '审批中',
          statusTagType: 'warning',
          summaryFields: [
            { key: '请假天数', value: '3' },
            { key: '请假原因', value: '家中有事' }
          ],
          detailPath: '/work/pending/12/task-9',
          kind: WORK_LIST_KINDS.PENDING
        }]
      })
    })

    it('presents a 已处理 page with the same 审批 skeleton and a processed detail path', () => {
      const page = presentWorkList(WORK_LIST_KINDS.PROCESSED, {
        records: [{
          id: 12,
          title: '张三提交的请假申请',
          name: '张三',
          processTypeName: '请假',
          createTime: '2026-08-18 10:00:00',
          status: 2,
          formValues: FORM_VALUES
        }],
        total: 1,
        pages: 1
      })

      expect(page.title).toBe('已处理')
      expect(page.emptyDescription).toBe('暂无已处理审批')
      expect(page.records[0]).toMatchObject({
        id: 12,
        title: '张三提交的请假申请',
        name: '张三',
        processTypeName: '请假',
        statusLabel: '审批通过',
        statusTagType: 'success',
        summaryFields: [
          { key: '请假天数', value: '3' },
          { key: '请假原因', value: '家中有事' }
        ],
        detailPath: '/work/processed/12',
        kind: WORK_LIST_KINDS.PROCESSED
      })
    })

    it('presents a 已发起 page with the same 审批 skeleton and a started detail path', () => {
      const page = presentWorkList(WORK_LIST_KINDS.STARTED, {
        records: [{
          id: 12,
          title: '我提交的请假申请',
          name: '张三',
          processTypeName: '请假',
          createTime: '2026-08-18 10:00:00',
          status: 1,
          formValues: FORM_VALUES
        }],
        total: 1,
        pages: 1
      })

      expect(page.title).toBe('已发起')
      expect(page.emptyDescription).toBe('暂无已发起审批')
      expect(page.records[0]).toMatchObject({
        id: 12,
        title: '我提交的请假申请',
        statusLabel: '审批中',
        detailPath: '/work/started/12',
        kind: WORK_LIST_KINDS.STARTED
      })
    })

    it('treats a missing payload as an empty paginated 待处理 page', () => {
      expect(presentWorkList(WORK_LIST_KINDS.PENDING)).toEqual({
        kind: WORK_LIST_KINDS.PENDING,
        title: '待处理',
        emptyDescription: '暂无待处理审批',
        records: [],
        total: 0,
        pages: 0
      })
    })

    it('uses the 办事列表种类 for empty-state copy on 已处理 and 已发起', () => {
      expect(presentWorkList(WORK_LIST_KINDS.PROCESSED)).toMatchObject({
        records: [],
        total: 0,
        pages: 0,
        emptyDescription: '暂无已处理审批'
      })
      expect(presentWorkList(WORK_LIST_KINDS.STARTED)).toMatchObject({
        records: [],
        total: 0,
        pages: 0,
        emptyDescription: '暂无已发起审批'
      })
    })
  })

  describe('loadWorkList', () => {
    it('loads a 待处理 page from kind and pagination through the list request', async () => {
      const request = async (req) => {
        expect(req).toEqual({
          method: 'get',
          url: '/admin/process/findPending/1/10'
        })
        return {
          data: {
            records: [{
              id: 12,
              taskId: 'task-9',
              title: '张三提交的请假申请',
              createTime: '2026-08-18 10:00:00',
              status: 1,
              formValues: FORM_VALUES
            }],
            total: 11,
            pages: 2
          }
        }
      }

      const page = await loadWorkList(WORK_LIST_KINDS.PENDING, 1, 10, request)

      expect(page.title).toBe('待处理')
      expect(page.total).toBe(11)
      expect(page.pages).toBe(2)
      expect(page.records[0].detailPath).toBe('/work/pending/12/task-9')
      expect(page.records[0].statusLabel).toBe('审批中')
    })

    it('dispatches 已处理和已发起 list requests by 办事列表种类', async () => {
      const seen = []
      const request = async (req) => {
        seen.push(req.url)
        return { data: { records: [], total: 0, pages: 0 } }
      }

      const processed = await loadWorkList(WORK_LIST_KINDS.PROCESSED, 2, 5, request)
      const started = await loadWorkList(WORK_LIST_KINDS.STARTED, 1, 10, request)

      expect(seen).toEqual([
        '/admin/process/findProcessed/2/5',
        '/admin/process/findStarted/1/10'
      ])
      expect(processed.emptyDescription).toBe('暂无已处理审批')
      expect(started.emptyDescription).toBe('暂无已发起审批')
    })
  })

  describe('workListPerspective', () => {
    it('keeps 待处理 as an actionable 视角 with a return path to that list', () => {
      expect(workListPerspective(WORK_LIST_KINDS.PENDING)).toEqual({
        kind: WORK_LIST_KINDS.PENDING,
        title: '待处理',
        returnPath: '/work/pending',
        readOnly: false
      })
    })

    it('opens 已处理 as a read-only 视角 that returns to that list', () => {
      expect(workListPerspective(WORK_LIST_KINDS.PROCESSED)).toEqual({
        kind: WORK_LIST_KINDS.PROCESSED,
        title: '已处理',
        returnPath: '/work/processed',
        readOnly: true
      })
    })

    it('opens 已发起 as a read-only 视角 that returns to that list', () => {
      expect(workListPerspective(WORK_LIST_KINDS.STARTED)).toEqual({
        kind: WORK_LIST_KINDS.STARTED,
        title: '已发起',
        returnPath: '/work/started',
        readOnly: true
      })
    })
  })
})
