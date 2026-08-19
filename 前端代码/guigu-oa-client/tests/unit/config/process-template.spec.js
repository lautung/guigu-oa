import {
  applyFormDesign,
  applyUploadResult,
  emptyForm,
  getRequest,
  listRequest,
  presentDesignerState,
  presentForm,
  presentListPage,
  presentStartGroups,
  publishRequest,
  removeRequest,
  saveValidationError,
  uploadProcessDefinitionUrl,
  wizardPrimaryAction,
  WIZARD_STEPS,
  writeRequest,
  zipUploadError
} from '@/config/process-template'

describe('config/process-template', () => {
  describe('presentListPage', () => {
    it('presents a paginated 审批模板 list with type, status, and draft-only actions', () => {
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

  describe('writeRequest', () => {
    const draft = {
      name: '请假申请',
      iconUrl: 'https://example.com/leave.png',
      processTypeId: 3,
      formProps: '[{"type":"input","field":"reason"}]',
      formOptions: '{"submitBtn":false}',
      processDefinitionKey: 'qingjia',
      processDefinitionPath: 'processes/qingjia.zip',
      description: '员工请假'
    }

    it('creates a draft 审批模板 with POST /admin/process/processTemplate/save', () => {
      expect(writeRequest(draft)).toEqual({
        method: 'post',
        url: '/admin/process/processTemplate/save',
        data: {
          name: '请假申请',
          iconUrl: 'https://example.com/leave.png',
          processTypeId: 3,
          formProps: '[{"type":"input","field":"reason"}]',
          formOptions: '{"submitBtn":false}',
          processDefinitionKey: 'qingjia',
          processDefinitionPath: 'processes/qingjia.zip',
          description: '员工请假',
          status: 0
        }
      })
    })

    it('updates an unpublished 审批模板 with PUT /admin/process/processTemplate/update', () => {
      expect(writeRequest({ id: 1, ...draft, status: 0 })).toEqual({
        method: 'put',
        url: '/admin/process/processTemplate/update',
        data: {
          id: 1,
          name: '请假申请',
          iconUrl: 'https://example.com/leave.png',
          processTypeId: 3,
          formProps: '[{"type":"input","field":"reason"}]',
          formOptions: '{"submitBtn":false}',
          processDefinitionKey: 'qingjia',
          processDefinitionPath: 'processes/qingjia.zip',
          description: '员工请假',
          status: 0
        }
      })
    })

    it('treats a blank form id as create, matching 新建向导', () => {
      expect(writeRequest({ id: '', ...draft }).method).toBe('post')
    })
  })

  describe('审批模板 API', () => {
    it('pages 审批模板 with GET /admin/process/processTemplate/{page}/{limit}', () => {
      expect(listRequest(1, 10)).toEqual({
        method: 'get',
        url: '/admin/process/processTemplate/1/10'
      })
    })

    it('loads one 审批模板 with GET /admin/process/processTemplate/get/{id}', () => {
      expect(getRequest(1)).toEqual({
        method: 'get',
        url: '/admin/process/processTemplate/get/1'
      })
    })

    it('deletes an unpublished 审批模板 with DELETE /admin/process/processTemplate/remove/{id}', () => {
      expect(removeRequest(1)).toEqual({
        method: 'delete',
        url: '/admin/process/processTemplate/remove/1'
      })
    })

    it('publishes an unpublished 审批模板 with GET /admin/process/processTemplate/publish/{id}', () => {
      expect(publishRequest(1)).toEqual({
        method: 'get',
        url: '/admin/process/processTemplate/publish/1'
      })
    })

    it('uploads an Activiti zip to /admin/process/processTemplate/uploadProcessDefinition', () => {
      expect(uploadProcessDefinitionUrl()).toBe(
        '/dev-api/admin/process/processTemplate/uploadProcessDefinition'
      )
    })

    it('builds the zip upload URL from Vite VITE_APP_BASE_API', () => {
      vi.stubEnv('VITE_APP_BASE_API', '/custom-api')
      expect(uploadProcessDefinitionUrl()).toBe(
        '/custom-api/admin/process/processTemplate/uploadProcessDefinition'
      )
      vi.unstubAllEnvs()
    })
  })

  describe('form', () => {
    it('starts 新建 with a blank three-step wizard form as 未发布', () => {
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

    it('captures form designer output into formProps and formOptions', () => {
      const form = applyFormDesign(emptyForm(), {
        rule: [{ type: 'input', field: 'reason' }],
        option: { submitBtn: false }
      })

      expect(form.formProps).toBe('[{"type":"input","field":"reason"}]')
      expect(form.formOptions).toBe('{"submitBtn":false}')
    })

    it('captures zip upload path and process definition key', () => {
      const form = applyUploadResult(emptyForm(), {
        processDefinitionPath: 'processes/qingjia.zip',
        processDefinitionKey: 'qingjia'
      })

      expect(form.processDefinitionPath).toBe('processes/qingjia.zip')
      expect(form.processDefinitionKey).toBe('qingjia')
    })

    it('restores designer rule and option from stored 种子 formProps / formOptions without dropping fields', () => {
      expect(presentDesignerState({
        formProps: '[{"type":"input","field":"reason","title":"请假原因","$required":true,"props":{"placeholder":"请输入请假原因"}},{"type":"input","field":"days","title":"请假天数","$required":true,"props":{"type":"number","placeholder":"请输入请假天数"}}]',
        formOptions: '{"submitBtn":false,"form":{"labelPosition":"right","labelWidth":"125px"}}'
      })).toEqual({
        rule: [
          {
            type: 'input',
            field: 'reason',
            title: '请假原因',
            $required: true,
            props: { placeholder: '请输入请假原因' }
          },
          {
            type: 'input',
            field: 'days',
            title: '请假天数',
            $required: true,
            props: { type: 'number', placeholder: '请输入请假天数' }
          }
        ],
        option: {
          submitBtn: false,
          form: { labelPosition: 'right', labelWidth: '125px' }
        }
      })
    })

    it('restores a blank designer when formProps / formOptions are missing or not JSON', () => {
      expect(presentDesignerState(emptyForm())).toEqual({
        rule: [],
        option: {}
      })
      expect(presentDesignerState({
        formProps: '{not-json',
        formOptions: 'also-not-json'
      })).toEqual({
        rule: [],
        option: {}
      })
    })

    it('round-trips designer output through formProps / formOptions for 编辑回填', () => {
      const rule = [{ type: 'input', field: 'reason', title: '请假原因' }]
      const option = { submitBtn: false }
      expect(presentDesignerState(applyFormDesign(emptyForm(), { rule, option }))).toEqual({
        rule,
        option
      })
    })
  })

  describe('wizard', () => {
    it('guides 基本设置 then 表单设置 then zip 流程设置, without an online designer step', () => {
      expect(WIZARD_STEPS).toEqual(['基本设置', '表单设置', '流程设置'])
      expect(WIZARD_STEPS.join(' ')).not.toMatch(/在线|iframe|online/i)
    })

    it('labels the last step as 提交保存', () => {
      expect(wizardPrimaryAction(0)).toBe('下一步')
      expect(wizardPrimaryAction(1)).toBe('下一步')
      expect(wizardPrimaryAction(2)).toBe('提交保存')
    })
  })

  describe('zipUploadError', () => {
    it('accepts a zip under 2048kb', () => {
      expect(zipUploadError({
        name: 'qingjia.zip',
        type: 'application/zip',
        size: 1024 * 1024
      })).toBeNull()
    })

    it('rejects a non-zip file', () => {
      expect(zipUploadError({
        name: 'qingjia.bpmn',
        type: 'application/xml',
        size: 1024
      })).toBe('只能上传 zip 压缩文件')
    })

    it('rejects a zip larger than 2048kb', () => {
      expect(zipUploadError({
        name: 'qingjia.zip',
        type: 'application/x-zip-compressed',
        size: 2048 * 1024 + 1
      })).toBe('文件不能超过 2048kb')
    })
  })

  describe('saveValidationError', () => {
    it('requires an uploaded 流程 zip before 提交保存', () => {
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
    it('shows only 已发布 templates grouped by 审批类型 for 发起审批', () => {
      expect(presentStartGroups([
        {
          id: 1,
          name: '请假',
          processTemplateList: [
            { id: 11, name: '事假', iconUrl: 'https://example.com/leave.png', status: 1 },
            { id: 12, name: '病假草稿', iconUrl: 'https://example.com/draft.png', status: 0 }
          ]
        },
        {
          id: 2,
          name: '加班',
          processTemplateList: [
            { id: 21, name: '加班草稿', iconUrl: 'https://example.com/ot.png', status: 0 }
          ]
        }
      ])).toEqual([
        {
          id: 1,
          name: '请假',
          templates: [
            { id: 11, name: '事假', iconUrl: 'https://example.com/leave.png' }
          ]
        }
      ])
    })

    it('treats a missing payload as no groups', () => {
      expect(presentStartGroups()).toEqual([])
    })
  })
})
