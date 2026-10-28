import { describe, expect, it } from 'vitest'
import { WORK_LIST_KINDS } from '@/router/paths'
import {
  parseFormValues,
  presentWorkList,
  statusLabel,
  statusTagType,
  summaryFields,
  workListDefinition,
  workListPerspective,
  workListPerspectiveFromRoute,
  workListRequest,
} from '@/work/work-list'

const FORM_VALUES = JSON.stringify({
  formShowData: {
    请假天数: '3',
    请假原因: '家中有事',
    图片: '<img src="https://example.com/a.png" />',
  },
})

describe('work/work-list', () => {
  describe('statusLabel', () => {
    it('maps the three 审批 statuses to 审批中 / 审批通过 / 审批拒绝', () => {
      expect(statusLabel(1)).toBe('审批中')
      expect(statusLabel(2)).toBe('审批通过')
      expect(statusLabel(-1)).toBe('审批拒绝')
    })

    it('renders an empty label for unknown or missing statuses', () => {
      expect(statusLabel(undefined)).toBe('')
      expect(statusLabel(0)).toBe('')
      expect(statusLabel(7)).toBe('')
    })
  })

  describe('statusTagType', () => {
    it('maps known statuses and falls back to info', () => {
      expect(statusTagType(1)).toBe('warning')
      expect(statusTagType(2)).toBe('success')
      expect(statusTagType(-1)).toBe('danger')
      expect(statusTagType(0)).toBe('info')
      expect(statusTagType(undefined)).toBe('info')
    })
  })

  describe('parseFormValues', () => {
    it('normalizes missing, primitive, and unparseable payloads to an empty form', () => {
      expect(parseFormValues(undefined)).toEqual({ formShowData: {} })
      expect(parseFormValues(null)).toEqual({ formShowData: {} })
      expect(parseFormValues(0)).toEqual({ formShowData: {} })
      expect(parseFormValues(42)).toEqual({ formShowData: {} })
      expect(parseFormValues('not json')).toEqual({ formShowData: {} })
      expect(parseFormValues('null')).toEqual({ formShowData: {} })
      expect(parseFormValues('"text"')).toEqual({ formShowData: {} })
    })

    it('passes through objects and parsed JSON objects', () => {
      const values = { formShowData: { 请假天数: '3' } }
      expect(parseFormValues(values)).toBe(values)
      expect(parseFormValues('{"formShowData":{"请假天数":"3"}}')).toEqual(values)
    })
  })

  describe('summaryFields', () => {
    it('drops hidden system fields and keeps business fields', () => {
      expect(summaryFields()).toEqual([])
      expect(summaryFields({ 请假天数: '3' })).toEqual([{ key: '请假天数', value: '3' }])
    })
  })

  describe('workListRequest', () => {
    it('loads 待处理 with GET /admin/process/findPending/{page}/{limit}', () => {
      expect(workListRequest(WORK_LIST_KINDS.PENDING, 1, 10)).toEqual({
        method: 'get',
        url: '/admin/process/findPending/1/10',
      })
    })

    it('loads 已处理 with GET /admin/process/findProcessed/{page}/{limit}', () => {
      expect(workListRequest(WORK_LIST_KINDS.PROCESSED, 2, 5)).toEqual({
        method: 'get',
        url: '/admin/process/findProcessed/2/5',
      })
    })

    it('loads 已发起 with GET /admin/process/findStarted/{page}/{limit}', () => {
      expect(workListRequest(WORK_LIST_KINDS.STARTED, 1, 10)).toEqual({
        method: 'get',
        url: '/admin/process/findStarted/1/10',
      })
    })

    it('loads 抄送我的 with GET /admin/process/findCarbonCopied/{page}/{limit}', () => {
      expect(workListRequest(WORK_LIST_KINDS.COPIED, 1, 10)).toEqual({
        method: 'get',
        url: '/admin/process/findCarbonCopied/1/10',
      })
    })
  })

  describe('workListDefinition', () => {
    it.each([
      [WORK_LIST_KINDS.PENDING, '待处理', '暂无待处理审批'],
      [WORK_LIST_KINDS.PROCESSED, '已处理', '暂无已处理审批'],
      [WORK_LIST_KINDS.STARTED, '已发起', '暂无已发起审批'],
      [WORK_LIST_KINDS.COPIED, '抄送我的', '暂无抄送审批'],
    ])('provides the %s list title and empty description', (kind, title, emptyDescription) => {
      expect(workListDefinition(kind)).toMatchObject({ title, emptyDescription })
    })
  })

  describe('presentWorkList', () => {
    it('presents a 待处理 page with unified 审批 rows and a pending detail path', () => {
      const page = presentWorkList(WORK_LIST_KINDS.PENDING, {
        records: [
          {
            id: 12,
            taskId: 'task-9',
            title: '张三提交的请假申请',
            name: '张三',
            processTypeName: '请假',
            createTime: '2026-08-18 10:00:00',
            status: 1,
            formValues: FORM_VALUES,
          },
        ],
        total: 1,
        pages: 1,
      })

      expect(page).toEqual({
        kind: WORK_LIST_KINDS.PENDING,
        title: '待处理',
        emptyDescription: '暂无待处理审批',
        total: 1,
        pages: 1,
        records: [
          {
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
              { key: '请假原因', value: '家中有事' },
            ],
            detailPath: '/work/pending/12/task-9',
            kind: WORK_LIST_KINDS.PENDING,
            relationLabel: '等待我处理',
            progressLabel: '',
            rowKey: '12-task-9',
          },
        ],
      })
    })

    it('presents a 已处理 page with the same 审批 skeleton and a processed detail path', () => {
      const page = presentWorkList(WORK_LIST_KINDS.PROCESSED, {
        records: [
          {
            id: 12,
            title: '张三提交的请假申请',
            name: '张三',
            processTypeName: '请假',
            createTime: '2026-08-18 10:00:00',
            status: 2,
            formValues: FORM_VALUES,
          },
        ],
        total: 1,
        pages: 1,
      })

      expect(page.title).toBe('已处理')
      expect(page.emptyDescription).toBe('暂无已处理审批')
      expect(page.records[0]).toMatchObject({
        id: 12,
        title: '张三提交的请假申请',
        name: '张三',
        processTypeName: '请假',
        createTime: '2026-08-18 10:00:00',
        statusLabel: '审批通过',
        statusTagType: 'success',
        summaryFields: [
          { key: '请假天数', value: '3' },
          { key: '请假原因', value: '家中有事' },
        ],
        detailPath: '/work/processed/12',
        kind: WORK_LIST_KINDS.PROCESSED,
        relationLabel: '我已处理',
        rowKey: '12',
      })
    })

    it('presents a read-only 抄送我的 row linking to its full approval detail', () => {
      const page = presentWorkList(WORK_LIST_KINDS.COPIED, {
        records: [
          {
            id: 23,
            title: '小艾发起请假申请',
            name: '小艾',
            processTypeName: '请假',
            status: 1,
            formValues: FORM_VALUES,
          },
        ],
        total: 1,
        pages: 1,
      })

      expect(page).toMatchObject({
        kind: WORK_LIST_KINDS.COPIED,
        title: '抄送我的',
        emptyDescription: '暂无抄送审批',
        records: [
          {
            detailPath: '/work/copied/23',
            relationLabel: '抄送给我',
            kind: WORK_LIST_KINDS.COPIED,
          },
        ],
      })
    })

    it('keeps the same 审批 skeleton for 待处理、已处理和已发起 and only adds relationship extras', () => {
      const record = {
        id: 12,
        title: '张三提交的请假申请',
        name: '张三',
        processTypeName: '请假',
        createTime: '2026-08-18 10:00:00',
        status: 1,
        formValues: FORM_VALUES,
      }

      const pending = presentWorkList(WORK_LIST_KINDS.PENDING, {
        records: [{ ...record, taskId: 'task-9' }],
      }).records[0]
      const processed = presentWorkList(WORK_LIST_KINDS.PROCESSED, {
        records: [{ ...record, status: 2 }],
      }).records[0]
      const started = presentWorkList(WORK_LIST_KINDS.STARTED, {
        records: [{ ...record, description: '等待李四审批' }],
      }).records[0]

      const skeleton = {
        title: '张三提交的请假申请',
        name: '张三',
        processTypeName: '请假',
        createTime: '2026-08-18 10:00:00',
      }
      expect(pending).toMatchObject({
        ...skeleton,
        statusLabel: '审批中',
        relationLabel: '等待我处理',
        progressLabel: '',
      })
      expect(processed).toMatchObject({
        ...skeleton,
        statusLabel: '审批通过',
        relationLabel: '我已处理',
        progressLabel: '',
      })
      expect(started).toMatchObject({
        ...skeleton,
        statusLabel: '审批中',
        relationLabel: '我发起的审批',
        progressLabel: '等待李四审批',
      })
      expect(processed).not.toHaveProperty('processedTime')
    })

    it('presents a 已发起 page with the same 审批 skeleton and a started detail path', () => {
      const page = presentWorkList(WORK_LIST_KINDS.STARTED, {
        records: [
          {
            id: 12,
            title: '我提交的请假申请',
            name: '张三',
            processTypeName: '请假',
            createTime: '2026-08-18 10:00:00',
            status: 1,
            description: '等待李四审批',
            formValues: FORM_VALUES,
          },
        ],
        total: 1,
        pages: 1,
      })

      expect(page.title).toBe('已发起')
      expect(page.emptyDescription).toBe('暂无已发起审批')
      expect(page.records[0]).toMatchObject({
        id: 12,
        title: '我提交的请假申请',
        name: '张三',
        processTypeName: '请假',
        createTime: '2026-08-18 10:00:00',
        statusLabel: '审批中',
        summaryFields: [
          { key: '请假天数', value: '3' },
          { key: '请假原因', value: '家中有事' },
        ],
        detailPath: '/work/started/12',
        kind: WORK_LIST_KINDS.STARTED,
        relationLabel: '我发起的审批',
        progressLabel: '等待李四审批',
        rowKey: '12',
      })
    })

    it('lets the same 审批 appear in 已发起 and 待处理 at once, and stay in 已发起 after it moves to 已处理', () => {
      const record = {
        id: 12,
        title: '我提交的请假申请',
        name: '张三',
        processTypeName: '请假',
        createTime: '2026-08-18 10:00:00',
        status: 1,
        description: '等待我审批',
        formValues: FORM_VALUES,
      }

      const pending = presentWorkList(WORK_LIST_KINDS.PENDING, {
        records: [{ ...record, taskId: 'task-9' }],
      }).records[0]
      const startedWhilePending = presentWorkList(WORK_LIST_KINDS.STARTED, {
        records: [record],
      }).records[0]
      const processed = presentWorkList(WORK_LIST_KINDS.PROCESSED, {
        records: [{ ...record, status: 2, description: '审批完成（通过）' }],
      }).records[0]
      const startedAfterProcessed = presentWorkList(WORK_LIST_KINDS.STARTED, {
        records: [{ ...record, status: 2, description: '审批完成（通过）' }],
      }).records[0]

      expect(pending.id).toBe(12)
      expect(startedWhilePending.id).toBe(12)
      expect(pending.relationLabel).toBe('等待我处理')
      expect(startedWhilePending.relationLabel).toBe('我发起的审批')
      expect(startedWhilePending.progressLabel).toBe('等待我审批')
      expect(processed.id).toBe(12)
      expect(startedAfterProcessed.id).toBe(12)
      expect(processed.relationLabel).toBe('我已处理')
      expect(startedAfterProcessed.relationLabel).toBe('我发起的审批')
      expect(startedAfterProcessed.progressLabel).toBe('审批完成（通过）')
      expect(startedAfterProcessed.detailPath).toBe('/work/started/12')
    })

    it('treats a missing payload as an empty paginated 待处理 page', () => {
      expect(presentWorkList(WORK_LIST_KINDS.PENDING)).toEqual({
        kind: WORK_LIST_KINDS.PENDING,
        title: '待处理',
        emptyDescription: '暂无待处理审批',
        records: [],
        total: 0,
        pages: 0,
      })
    })

    it('uses the 办事列表种类 for empty-state copy on 已处理 and 已发起', () => {
      expect(presentWorkList(WORK_LIST_KINDS.PROCESSED)).toMatchObject({
        records: [],
        total: 0,
        pages: 0,
        emptyDescription: '暂无已处理审批',
      })
      expect(presentWorkList(WORK_LIST_KINDS.STARTED)).toMatchObject({
        records: [],
        total: 0,
        pages: 0,
        emptyDescription: '暂无已发起审批',
      })
    })
  })

  describe('workListPerspective', () => {
    it('keeps 待处理 as an actionable 视角 with a return path to that list', () => {
      expect(workListPerspective(WORK_LIST_KINDS.PENDING)).toEqual({
        kind: WORK_LIST_KINDS.PENDING,
        title: '待处理',
        returnPath: '/work/pending',
        readOnly: false,
        emphasis: '审批动作',
      })
    })

    it('opens 已处理 as a read-only 视角 that returns to that list', () => {
      expect(workListPerspective(WORK_LIST_KINDS.PROCESSED)).toEqual({
        kind: WORK_LIST_KINDS.PROCESSED,
        title: '已处理',
        returnPath: '/work/processed',
        readOnly: true,
        emphasis: '回看',
      })
    })

    it('opens 已发起 as a read-only 视角 that returns to that list and emphasizes 整体进度', () => {
      expect(workListPerspective(WORK_LIST_KINDS.STARTED)).toEqual({
        kind: WORK_LIST_KINDS.STARTED,
        title: '已发起',
        returnPath: '/work/started',
        readOnly: true,
        emphasis: '整体进度',
      })
    })

    it('opens 抄送我的 as a read-only 视角 that returns to its list', () => {
      expect(workListPerspective(WORK_LIST_KINDS.COPIED)).toEqual({
        kind: WORK_LIST_KINDS.COPIED,
        title: '抄送我的',
        returnPath: '/work/copied',
        readOnly: true,
        emphasis: '知情',
      })
    })

    it('reads the current 视角 from the route workListKind', () => {
      expect(
        workListPerspectiveFromRoute({
          meta: { workListKind: WORK_LIST_KINDS.PENDING },
        }),
      ).toEqual({
        kind: WORK_LIST_KINDS.PENDING,
        title: '待处理',
        returnPath: '/work/pending',
        readOnly: false,
        emphasis: '审批动作',
      })

      expect(
        workListPerspectiveFromRoute({
          meta: { workListKind: WORK_LIST_KINDS.PROCESSED },
        }),
      ).toEqual({
        kind: WORK_LIST_KINDS.PROCESSED,
        title: '已处理',
        returnPath: '/work/processed',
        readOnly: true,
        emphasis: '回看',
      })

      expect(
        workListPerspectiveFromRoute({
          meta: { workListKind: WORK_LIST_KINDS.STARTED },
        }),
      ).toEqual({
        kind: WORK_LIST_KINDS.STARTED,
        title: '已发起',
        returnPath: '/work/started',
        readOnly: true,
        emphasis: '整体进度',
      })
    })

    it('treats a config detail route as a read-only 视角 returning to its list', () => {
      expect(workListPerspectiveFromRoute({ path: '/processMgr/process/5', meta: {} })).toEqual({
        kind: null,
        title: '',
        returnPath: '/processMgr/process',
        readOnly: true,
        emphasis: '',
      })
    })
  })
})
