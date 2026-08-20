import {
  applyUploadResult,
  emptyForm,
  presentForm,
  presentListPage,
  presentStartGroups,
  saveValidationError
} from '@/config/process-template'

describe('config/process-template', () => {
  describe('presentListPage', () => {
    it('presents a paginated 审批模板 list with type, 未发布/已发布, and unpublished-only actions', () => {
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
    })

    it('treats a missing payload as an empty page', () => {
      expect(presentListPage()).toEqual({
        records: [],
        total: 0,
        pages: 0
      })
    })
  })

  describe('form', () => {
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
    })

    it('loads 编辑 from a getById payload', () => {
      expect(presentForm({
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
      })).toEqual({
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
    })

    it('captures zip upload path and process definition key', () => {
      const form = applyUploadResult(emptyForm(), {
        processDefinitionPath: 'processes/qingjia.zip',
        processDefinitionKey: 'qingjia'
      })

      expect(form.processDefinitionPath).toBe('processes/qingjia.zip')
      expect(form.processDefinitionKey).toBe('qingjia')
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
  })

  describe('presentStartGroups', () => {
    it('presents 可发起清单 groups from 审批模板', () => {
      expect(presentStartGroups([
        {
          id: 1,
          name: '请假',
          processTemplateList: [
            { id: 11, name: '事假', iconUrl: 'https://example.com/leave.png' },
            { id: 1, name: '请假申请', iconUrl: 'https://example.com/leave2.png' }
          ]
        }
      ])).toEqual([
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

    it('treats a missing payload as no groups', () => {
      expect(presentStartGroups()).toEqual([])
    })
  })
})
