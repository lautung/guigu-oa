import { canInitiate } from '@/config/process-template'
import { ROUTES } from '@/navigation/access-policy'

export const START_SUCCESS_MESSAGE = '发起成功'

export function applyPath(templateId) {
  return `${ROUTES.START}/${templateId}`
}

export function postStartSuccessRoute() {
  return ROUTES.STARTED
}

function parseJson(value, fallback) {
  if (!value) {
    return fallback
  }
  if (typeof value === 'object') {
    return value
  }
  try {
    return JSON.parse(value)
  } catch (error) {
    return fallback
  }
}

function presentApplyOption(option = {}) {
  return Object.assign({}, option, {
    submitBtn: true,
    form: Object.assign({}, option.form || {}, {
      labelPosition: 'top'
    })
  })
}

export function presentApplyTemplate(record) {
  if (!canInitiate(record)) {
    return {
      ok: false,
      redirectTo: ROUTES.START
    }
  }

  return {
    ok: true,
    id: record.id,
    processTypeId: record.processTypeId,
    name: record.name,
    rule: parseJson(record.formProps, []),
    option: presentApplyOption(parseJson(record.formOptions, {}))
  }
}

export function buildStartPayload({ template, formData = {}, rule = [] }) {
  const formShowData = {}
  rule.forEach(item => {
    if (item.field in formData) {
      formShowData[item.title] = formData[item.field]
    }
  })

  return {
    processTemplateId: template.id,
    processTypeId: template.processTypeId,
    formValues: JSON.stringify({ formData, formShowData })
  }
}
