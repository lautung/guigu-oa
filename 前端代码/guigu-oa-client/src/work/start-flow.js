import { ROUTES } from '@/navigation/access-policy'

export const START_SUCCESS_MESSAGE = '发起成功'

export function applyPath(templateId) {
  return `${ROUTES.START}/${templateId}`
}

export function getProcessTemplateRequest(id) {
  return {
    method: 'get',
    url: `/admin/process/getProcessTemplate/${id}`
  }
}

export function startUpRequest(data) {
  return {
    method: 'post',
    url: '/admin/process/startUp',
    data
  }
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

export function presentApplyTemplate(record) {
  if (!record || record.status !== 1) {
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
    option: parseJson(record.formOptions, {})
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
