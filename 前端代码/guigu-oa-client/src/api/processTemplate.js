import request from '@/utils/request'

const PROCESS_TEMPLATE_API = '/admin/process/processTemplate'

function unpublishedPayload(form) {
  const data = {
    name: form.name,
    iconUrl: form.iconUrl,
    processTypeId: form.processTypeId,
    formProps: form.formProps,
    formOptions: form.formOptions,
    processDefinitionKey: form.processDefinitionKey,
    processDefinitionPath: form.processDefinitionPath,
    description: form.description,
    status: 0
  }

  if (form.id) {
    data.id = form.id
  }

  return data
}

export function findStartable() {
  return request({
    method: 'get',
    url: '/admin/process/findStartable'
  })
}

export function getPageList(page, limit) {
  return request({
    method: 'get',
    url: `${PROCESS_TEMPLATE_API}/${page}/${limit}`
  })
}

export function getById(id) {
  return request({
    method: 'get',
    url: `${PROCESS_TEMPLATE_API}/get/${id}`
  })
}

export function saveOrUpdate(form) {
  if (form.id) {
    return request({
      method: 'put',
      url: `${PROCESS_TEMPLATE_API}/update`,
      data: unpublishedPayload(form)
    })
  }

  return request({
    method: 'post',
    url: `${PROCESS_TEMPLATE_API}/save`,
    data: unpublishedPayload(form)
  })
}

export function removeById(id) {
  return request({
    method: 'delete',
    url: `${PROCESS_TEMPLATE_API}/remove/${id}`
  })
}

export function publish(id) {
  return request({
    method: 'get',
    url: `${PROCESS_TEMPLATE_API}/publish/${id}`
  })
}

export function uploadProcessDefinitionUrl() {
  const base = import.meta.env.VITE_APP_BASE_API || '/dev-api'
  return `${base}${PROCESS_TEMPLATE_API}/uploadProcessDefinition`
}
