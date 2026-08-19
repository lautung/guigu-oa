import { ROUTES } from '@/navigation/access-policy'
import {
  START_SUCCESS_MESSAGE,
  applyPath,
  buildStartPayload,
  getProcessTemplateRequest,
  presentApplyTemplate,
  postStartSuccessRoute,
  startUpRequest
} from '@/work/start-flow'

describe('work/start-flow', () => {
  describe('applyPath', () => {
    it('opens a published template at the 发起审批 form route', () => {
      expect(applyPath(11)).toBe('/work/start/11')
    })
  })

  describe('presentApplyTemplate', () => {
    it('renders the dynamic form from a published template definition', () => {
      expect(presentApplyTemplate({
        id: 11,
        processTypeId: 1,
        name: '事假',
        status: 1,
        formProps: JSON.stringify([{ field: 'days', title: '请假天数' }]),
        formOptions: JSON.stringify({ submitBtn: true })
      })).toEqual({
        ok: true,
        id: 11,
        processTypeId: 1,
        name: '事假',
        rule: [{ field: 'days', title: '请假天数' }],
        option: { submitBtn: true }
      })
    })

    it('sends unpublished or missing templates back to 发起审批', () => {
      expect(presentApplyTemplate({
        id: 12,
        status: 0,
        formProps: '[]',
        formOptions: '{}'
      })).toEqual({
        ok: false,
        redirectTo: ROUTES.START
      })

      expect(presentApplyTemplate()).toEqual({
        ok: false,
        redirectTo: ROUTES.START
      })
    })
  })

  describe('buildStartPayload', () => {
    it('submits formData plus formShowData titles for startUp', () => {
      expect(buildStartPayload({
        template: { id: 11, processTypeId: 1 },
        formData: { days: 3 },
        rule: [{ field: 'days', title: '请假天数' }]
      })).toEqual({
        processTemplateId: 11,
        processTypeId: 1,
        formValues: JSON.stringify({
          formData: { days: 3 },
          formShowData: { 请假天数: 3 }
        })
      })
    })
  })

  describe('start APIs', () => {
    it('loads a template with GET /admin/process/getProcessTemplate/{id}', () => {
      expect(getProcessTemplateRequest(11)).toEqual({
        method: 'get',
        url: '/admin/process/getProcessTemplate/11'
      })
    })

    it('starts an 审批 with POST /admin/process/startUp', () => {
      const data = {
        processTemplateId: 11,
        processTypeId: 1,
        formValues: '{"formData":{},"formShowData":{}}'
      }
      expect(startUpRequest(data)).toEqual({
        method: 'post',
        url: '/admin/process/startUp',
        data
      })
    })
  })

  describe('success feedback', () => {
    it('tells the user 发起成功 and continues to 已发起', () => {
      expect(START_SUCCESS_MESSAGE).toBe('发起成功')
      expect(postStartSuccessRoute()).toBe(ROUTES.STARTED)
    })
  })
})
