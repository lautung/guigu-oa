import {
  canInitiate,
  canWrite,
  findStartable,
  listRequest,
  presentListPage,
  publish,
  removeUnpublished,
} from '@/config/process-template'

describe('审批模板', () => {
  describe('canInitiate / canWrite', () => {
    it('未发布 can be written and cannot be initiated', () => {
      const unpublished = { id: 12, status: 0, name: '病假申请' }

      expect(canWrite(unpublished)).toBe(true)
      expect(canInitiate(unpublished)).toBe(false)
    })

    it('已发布 can be initiated and cannot be written', () => {
      const published = { id: 1, status: 1, name: '请假申请' }

      expect(canInitiate(published)).toBe(true)
      expect(canWrite(published)).toBe(false)
    })

    it('preserves 已发布 while hiding a template that is unavailable for initiation', () => {
      const publishedButUnavailable = {
        id: 19,
        status: 1,
        availableForInitiation: false,
        availabilityMessage: '暂不可发起，需核实原审批内容',
      }

      expect(canInitiate(publishedButUnavailable)).toBe(false)
      expect(canWrite(publishedButUnavailable)).toBe(false)
    })
  })

  describe('presentListPage / listRequest', () => {
    it('lists 未发布 as writable and 已发布 without write actions', () => {
      const page = presentListPage({
        records: [
          {
            id: 1,
            name: '请假申请',
            iconUrl: 'https://example.com/leave.png',
            processTypeName: '请假',
            description: '员工请假',
            status: 0,
            formProps: '[{"type":"input"}]',
            formOptions: '{}',
            createTime: '2026-08-01 10:00:00',
            updateTime: '2026-08-02 11:00:00',
          },
          {
            id: 2,
            name: '加班申请',
            iconUrl: 'https://example.com/overtime.png',
            processTypeName: '加班',
            description: '加班审批',
            status: 1,
            createTime: '2026-08-03 09:00:00',
            updateTime: '2026-08-03 09:00:00',
          },
        ],
        total: 2,
        pages: 1,
      })

      expect(page).toEqual({
        records: [
          {
            id: 1,
            name: '请假申请',
            iconUrl: 'https://example.com/leave.png',
            processTypeName: '请假',
            description: '员工请假',
            status: 0,
            statusLabel: '未发布',
            formProps: '[{"type":"input"}]',
            formOptions: '{}',
            createTime: '2026-08-01 10:00:00',
            updateTime: '2026-08-02 11:00:00',
            canEdit: true,
            canDelete: true,
            canPublish: true,
          },
          {
            id: 2,
            name: '加班申请',
            iconUrl: 'https://example.com/overtime.png',
            processTypeName: '加班',
            description: '加班审批',
            status: 1,
            statusLabel: '已发布',
            formProps: '',
            formOptions: '',
            createTime: '2026-08-03 09:00:00',
            updateTime: '2026-08-03 09:00:00',
            canEdit: false,
            canDelete: false,
            canPublish: false,
          },
        ],
        total: 2,
        pages: 1,
      })
      expect(canWrite(page.records[0])).toBe(true)
      expect(canInitiate(page.records[0])).toBe(false)
      expect(canWrite(page.records[1])).toBe(false)
      expect(canInitiate(page.records[1])).toBe(true)
    })

    it('shows the availability explanation without restoring write actions', () => {
      const page = presentListPage({
        records: [
          {
            id: 19,
            name: '旧请假申请',
            status: 1,
            availableForInitiation: false,
            availabilityMessage: '暂不可发起，需核实原审批内容',
          },
        ],
      })

      expect(page.records[0]).toMatchObject({
        statusLabel: '已发布',
        availabilityMessage: '暂不可发起，需核实原审批内容',
        canEdit: false,
        canDelete: false,
        canPublish: false,
      })
    })

    it('treats a missing payload as an empty page', () => {
      expect(presentListPage()).toEqual({
        records: [],
        total: 0,
        pages: 0,
      })
    })

    it('reads a page of 审批模板 through GET /admin/process/processTemplate/:page/:limit', () => {
      expect(listRequest(2, 5)).toEqual({
        method: 'get',
        url: '/admin/process/processTemplate/2/5',
      })
    })
  })

  describe('publish / removeUnpublished', () => {
    it('does not publish 已发布', async () => {
      let sent = false
      const request = async () => {
        sent = true
      }

      await expect(publish({ id: 1, status: 1 }, request)).rejects.toThrow(
        '已发布的审批模板不能再发布',
      )
      expect(sent).toBe(false)
    })

    it('does not delete 已发布', async () => {
      let sent = false
      const request = async () => {
        sent = true
      }

      await expect(removeUnpublished({ id: 1, status: 1 }, request)).rejects.toThrow(
        '已发布的审批模板不能删除',
      )
      expect(sent).toBe(false)
    })
  })

  describe('findStartable', () => {
    it('presents 可发起清单 groups from 审批模板', async () => {
      const request = async () => ({
        data: [
          {
            id: 1,
            name: '请假',
            processTemplateList: [
              { id: 11, name: '事假', iconUrl: 'https://example.com/leave.png', status: 1 },
              { id: 1, name: '请假申请', iconUrl: 'https://example.com/leave2.png', status: 1 },
            ],
          },
        ],
      })

      expect(await findStartable(request)).toEqual([
        {
          id: 1,
          name: '请假',
          templates: [
            { id: 11, name: '事假', iconUrl: 'https://example.com/leave.png' },
            { id: 1, name: '请假申请', iconUrl: 'https://example.com/leave2.png' },
          ],
        },
      ])
    })

    it('omits 审批类型 that have no 已发布 审批模板', async () => {
      const request = async () => ({
        data: [{ id: 2, name: '加班', processTemplateList: [] }],
      })

      expect(await findStartable(request)).toEqual([])
    })

    it('treats a missing payload as no groups', async () => {
      const request = async () => ({})

      expect(await findStartable(request)).toEqual([])
    })

    it('filters published templates that are unavailable for initiation', async () => {
      const request = async () => ({
        data: [
          {
            id: 2,
            name: '请假',
            processTemplateList: [
              { id: 21, name: '正常模板', status: 1 },
              { id: 22, name: '待核实模板', availableForInitiation: false },
            ],
          },
        ],
      })

      expect(await findStartable(request)).toEqual([
        {
          id: 2,
          name: '请假',
          templates: [{ id: 21, name: '正常模板', iconUrl: undefined }],
        },
      ])
    })
  })
})
