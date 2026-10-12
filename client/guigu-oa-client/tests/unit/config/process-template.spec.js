import {
  applyUploadResult,
  canInitiate,
  canWrite,
  emptyForm,
  findPage,
  findStartable,
  publish,
  read,
  removeUnpublished,
  saveUnpublished,
  saveValidationError
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
  })

  describe('findPage', () => {
    it('lists 未发布 as writable and 已发布 without write actions', async () => {
      const request = async () => ({
        data: {
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
              updateTime: '2026-08-02 11:00:00'
            },
            {
              id: 2,
              name: '加班申请',
              iconUrl: 'https://example.com/overtime.png',
              processTypeName: '加班',
              description: '加班审批',
              status: 1,
              createTime: '2026-08-03 09:00:00',
              updateTime: '2026-08-03 09:00:00'
            }
          ],
          total: 2,
          pages: 1
        }
      })

      const page = await findPage(1, 10, request)

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
            canPublish: true
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
            canPublish: false
          }
        ],
        total: 2,
        pages: 1
      })
      expect(canWrite(page.records[0])).toBe(true)
      expect(canInitiate(page.records[0])).toBe(false)
      expect(canWrite(page.records[1])).toBe(false)
      expect(canInitiate(page.records[1])).toBe(true)
    })

    it('treats a missing payload as an empty page', async () => {
      const request = async () => ({})

      expect(await findPage(1, 10, request)).toEqual({
        records: [],
        total: 0,
        pages: 0
      })
    })
  })

  describe('read', () => {
    it('loads 未发布 with type, name, form and 流程 zip still writable', async () => {
      const request = async () => ({
        data: {
          id: 1,
          name: '请假申请',
          iconUrl: 'https://example.com/leave.png',
          processTypeId: 3,
          formProps: '[{"type":"input"}]',
          formOptions: '{"submitBtn":false}',
          processDefinitionKey: 'qingjia',
          processDefinitionPath: 'processes/qingjia.zip',
          description: '员工请假',
          status: 0,
          createTime: '2026-08-01 10:00:00'
        }
      })

      const template = await read(1, request)

      expect(template).toEqual({
        id: 1,
        name: '请假申请',
        iconUrl: 'https://example.com/leave.png',
        processTypeId: 3,
        formProps: '[{"type":"input"}]',
        formOptions: '{"submitBtn":false}',
        processDefinitionKey: 'qingjia',
        processDefinitionPath: 'processes/qingjia.zip',
        description: '员工请假',
        status: 0
      })
      expect(canWrite(template)).toBe(true)
      expect(canInitiate(template)).toBe(false)
    })

    it('loads 已发布 as readable but not writable', async () => {
      const request = async () => ({
        data: {
          id: 1,
          name: '请假申请',
          processTypeId: 1,
          status: 1,
          formProps: '[]',
          formOptions: '{}'
        }
      })

      const template = await read(1, request)

      expect(template.name).toBe('请假申请')
      expect(canWrite(template)).toBe(false)
      expect(canInitiate(template)).toBe(true)
    })
  })

  describe('saveUnpublished', () => {
    it('starts 新建 as 未发布', () => {
      expect(emptyForm()).toEqual({
        id: '',
        name: '',
        iconUrl: '',
        processTypeId: '',
        formProps: '',
        formOptions: '',
        processDefinitionKey: '',
        processDefinitionPath: '',
        description: '',
        status: 0
      })
      expect(canInitiate(emptyForm())).toBe(false)
      expect(canWrite(emptyForm())).toBe(true)
    })

    it('new save stays 未发布 even if the caller asks for 已发布', async () => {
      let saved
      const request = async (descriptor) => {
        saved = descriptor.data
        return { message: 'ok' }
      }

      await saveUnpublished({
        name: '病假申请',
        iconUrl: '',
        processTypeId: 1,
        formProps: '[]',
        formOptions: '{}',
        processDefinitionKey: 'bingjia',
        processDefinitionPath: 'processes/bingjia.zip',
        description: '员工病假',
        status: 1
      }, request)

      expect(canInitiate(saved)).toBe(false)
      expect(canWrite(saved)).toBe(true)
      expect(saved.name).toBe('病假申请')
    })

    it('does not save 已发布', async () => {
      let sent = false
      const request = async () => {
        sent = true
      }

      await expect(saveUnpublished({
        id: 1,
        name: '请假申请',
        status: 1,
        processDefinitionPath: 'processes/qingjia.zip'
      }, request)).rejects.toThrow('已发布的审批模板不能保存')
      expect(sent).toBe(false)
    })

    it('captures zip upload path and process definition key', () => {
      const form = applyUploadResult(emptyForm(), {
        processDefinitionPath: 'processes/qingjia.zip',
        processDefinitionKey: 'qingjia'
      })

      expect(form.processDefinitionPath).toBe('processes/qingjia.zip')
      expect(form.processDefinitionKey).toBe('qingjia')
      expect(canInitiate(form)).toBe(false)
    })
  })

  describe('saveValidationError', () => {
    it('requires a 流程 zip before saving as 未发布', () => {
      expect(saveValidationError(emptyForm())).toBe('请上传流程 zip')
    })

    it('accepts a form with processDefinitionPath', () => {
      expect(saveValidationError({
        ...emptyForm(),
        processDefinitionPath: 'processes/qingjia.zip'
      })).toBeNull()
    })

    it('does not save 已发布', () => {
      expect(saveValidationError({
        ...emptyForm(),
        id: 1,
        status: 1,
        processDefinitionPath: 'processes/qingjia.zip'
      })).toBe('已发布的审批模板不能保存')
    })
  })

  describe('publish / removeUnpublished', () => {
    it('does not publish 已发布', async () => {
      let sent = false
      const request = async () => {
        sent = true
      }

      await expect(publish({ id: 1, status: 1 }, request))
        .rejects.toThrow('已发布的审批模板不能再发布')
      expect(sent).toBe(false)
    })

    it('does not delete 已发布', async () => {
      let sent = false
      const request = async () => {
        sent = true
      }

      await expect(removeUnpublished({ id: 1, status: 1 }, request))
        .rejects.toThrow('已发布的审批模板不能删除')
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
              { id: 11, name: '事假', iconUrl: 'https://example.com/leave.png' },
              { id: 1, name: '请假申请', iconUrl: 'https://example.com/leave2.png' }
            ]
          }
        ]
      })

      expect(await findStartable(request)).toEqual([
        {
          id: 1,
          name: '请假',
          templates: [
            { id: 11, name: '事假', iconUrl: 'https://example.com/leave.png' },
            { id: 1, name: '请假申请', iconUrl: 'https://example.com/leave2.png' }
          ]
        }
      ])
    })

    it('omits 审批类型 that have no 已发布 审批模板', async () => {
      const request = async () => ({
        data: [
          { id: 2, name: '加班', processTemplateList: [] }
        ]
      })

      expect(await findStartable(request)).toEqual([])
    })

    it('treats a missing payload as no groups', async () => {
      const request = async () => ({})

      expect(await findStartable(request)).toEqual([])
    })
  })
})
