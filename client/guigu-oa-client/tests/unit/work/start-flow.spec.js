import { ROUTES } from '@/navigation/constants'
import {
  START_SUCCESS_MESSAGE,
  applyPath,
  buildStartPayload,
  presentApplyTemplate,
  postStartSuccessRoute
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
        option: {
          submitBtn: true,
          form: { labelPosition: 'top' }
        }
      })
    })

    it('replays 种子 formProps / formOptions without dropping fields and enables submit on phone', () => {
      const presented = presentApplyTemplate({
        id: 1,
        processTypeId: 1,
        name: '请假申请',
        status: 1,
        formProps: '[{"type":"input","field":"reason","title":"请假原因","$required":true,"props":{"placeholder":"请输入请假原因"}},{"type":"input","field":"days","title":"请假天数","$required":true,"props":{"type":"number","placeholder":"请输入请假天数"}}]',
        formOptions: '{"submitBtn":false,"form":{"labelPosition":"right","labelWidth":"125px"}}'
      })

      expect(presented.ok).toBe(true)
      expect(presented.name).toBe('请假申请')
      expect(presented.rule).toEqual([
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
      ])
      expect(presented.option).toEqual({
        submitBtn: true,
        form: {
          labelPosition: 'top',
          labelWidth: '125px'
        }
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

  describe('success feedback', () => {
    it('tells the user 发起成功 and continues to 已发起', () => {
      expect(START_SUCCESS_MESSAGE).toBe('发起成功')
      expect(postStartSuccessRoute()).toBe(ROUTES.STARTED)
    })
  })
})
