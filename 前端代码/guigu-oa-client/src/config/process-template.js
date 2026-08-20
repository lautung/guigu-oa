const PROCESS_TEMPLATE_API = '/admin/process/processTemplate'

export function canInitiate(record) {
  return !!record && record.status === 1
}

export function canWrite(record) {
  return !!record && !canInitiate(record)
}

function presentListItem(record = {}) {
  const writable = canWrite(record)

  return {
    id: record.id,
    name: record.name,
    iconUrl: record.iconUrl,
    processTypeName: record.processTypeName,
    description: record.description,
    status: record.status,
    statusLabel: canInitiate(record) ? '已发布' : '未发布',
    formProps: record.formProps || '',
    formOptions: record.formOptions || '',
    createTime: record.createTime,
    updateTime: record.updateTime,
    canEdit: writable,
    canDelete: writable,
    canPublish: writable
  }
}

function presentListPage(page = {}) {
  return {
    records: (page.records || []).map(presentListItem),
    total: page.total || 0,
    pages: page.pages || 0
  }
}

export function emptyForm() {
  return {
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
  }
}

function presentForm(record = {}) {
  return {
    id: record.id || '',
    name: record.name || '',
    iconUrl: record.iconUrl || '',
    processTypeId: record.processTypeId || '',
    formProps: record.formProps || '',
    formOptions: record.formOptions || '',
    processDefinitionKey: record.processDefinitionKey || '',
    processDefinitionPath: record.processDefinitionPath || '',
    description: record.description || '',
    status: record.status == null ? 0 : record.status
  }
}

function parseJson(value, fallback) {
  try {
    return value ? JSON.parse(value) : fallback
  } catch (error) {
    return fallback
  }
}

export function applyFormDesign(form, { rule = [], option = {} } = {}) {
  return {
    ...form,
    formProps: JSON.stringify(rule),
    formOptions: JSON.stringify(option)
  }
}

export function presentDesignerState(form = {}) {
  return {
    rule: parseJson(form.formProps, []),
    option: parseJson(form.formOptions, {})
  }
}

export function applyUploadResult(form, data = {}) {
  return {
    ...form,
    processDefinitionPath: data.processDefinitionPath || '',
    processDefinitionKey: data.processDefinitionKey || ''
  }
}

const ZIP_MIME_TYPES = [
  'application/zip',
  'application/x-zip',
  'application/x-zip-compressed'
]
const MAX_ZIP_BYTES = 2048 * 1024

function isZipFile(file = {}) {
  return ZIP_MIME_TYPES.includes(file.type) || /\.zip$/i.test(file.name || '')
}

export function zipUploadError(file = {}) {
  if (!isZipFile(file)) {
    return '只能上传 zip 压缩文件'
  }
  if (file.size > MAX_ZIP_BYTES) {
    return '文件不能超过 2048kb'
  }
  return null
}

export function saveValidationError(form = {}) {
  if (form.id && canInitiate(form)) {
    return '已发布的审批模板不能保存'
  }
  if (!form.processDefinitionPath) {
    return '请上传流程 zip'
  }
  return null
}

function presentStartGroups(types = []) {
  return types
    .map(type => ({
      id: type.id,
      name: type.name,
      templates: (type.processTemplateList || []).map(template => ({
        id: template.id,
        name: template.name,
        iconUrl: template.iconUrl
      }))
    }))
    .filter(group => group.templates.length > 0)
}

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

export async function findPage(page, limit, request) {
  const response = await request({
    method: 'get',
    url: `${PROCESS_TEMPLATE_API}/${page}/${limit}`
  })
  return presentListPage((response && response.data) || {})
}

export async function read(id, request) {
  const response = await request({
    method: 'get',
    url: `${PROCESS_TEMPLATE_API}/get/${id}`
  })
  return presentForm((response && response.data) || {})
}

export async function saveUnpublished(form, request) {
  if (form.id && canInitiate(form)) {
    return Promise.reject(new Error('已发布的审批模板不能保存'))
  }
  const data = unpublishedPayload(form)
  if (form.id) {
    return request({
      method: 'put',
      url: `${PROCESS_TEMPLATE_API}/update`,
      data
    })
  }
  return request({
    method: 'post',
    url: `${PROCESS_TEMPLATE_API}/save`,
    data
  })
}

export async function publish(template, request) {
  if (!canWrite(template)) {
    return Promise.reject(new Error('已发布的审批模板不能再发布'))
  }
  return request({
    method: 'get',
    url: `${PROCESS_TEMPLATE_API}/publish/${template.id}`
  })
}

export async function removeUnpublished(template, request) {
  if (!canWrite(template)) {
    return Promise.reject(new Error('已发布的审批模板不能删除'))
  }
  return request({
    method: 'delete',
    url: `${PROCESS_TEMPLATE_API}/remove/${template.id}`
  })
}

export async function findStartable(request) {
  const response = await request({
    method: 'get',
    url: '/admin/process/findStartable'
  })
  return presentStartGroups((response && response.data) || [])
}

export function uploadProcessDefinitionUrl() {
  const base = import.meta.env.VITE_APP_BASE_API || '/dev-api'
  return `${base}${PROCESS_TEMPLATE_API}/uploadProcessDefinition`
}

export const TEMPLATE_ICONS = [
  { iconUrl: 'https://gw.alicdn.com/tfs/TB1t695CFYqK1RjSZLeXXbXppXa-102-102.png', tag: '请假' },
  { iconUrl: 'https://gw.alicdn.com/tfs/TB1bHOWCSzqK1RjSZFjXXblCFXa-112-112.png', tag: '出差' },
  { iconUrl: 'https://gw.alicdn.com/tfs/TB1cbCYCPTpK1RjSZKPXXa3UpXa-112-112.png', tag: '机票出差' },
  { iconUrl: 'https://gw.alicdn.com/tfs/TB1cbCYCPTpK1RjSZKPXXa3UpXa-112-112.png', tag: '机票改签' },
  { iconUrl: 'https://gw.alicdn.com/tfs/TB1e76lCOLaK1RjSZFxXXamPFXa-112-112.png', tag: '外出' },
  { iconUrl: 'https://gw.alicdn.com/tfs/TB1Yfa0CG6qK1RjSZFmXXX0PFXa-112-112.png', tag: '补卡申请' },
  { iconUrl: 'https://gw.alicdn.com/tfs/TB1Y8PlCNjaK1RjSZKzXXXVwXXa-112-112.png', tag: '加班' },
  { iconUrl: 'https://gw.alicdn.com/tfs/TB11X99CNTpK1RjSZFKXXa2wXXa-102-102.png', tag: '居家隔离' },
  { iconUrl: 'https://gw.alicdn.com/tfs/TB1_YG.COrpK1RjSZFhXXXSdXXa-102-102.png', tag: '请假' },
  { iconUrl: 'https://gw.alicdn.com/tfs/TB13ca1CMDqK1RjSZSyXXaxEVXa-102-102.png', tag: '调岗' },
  { iconUrl: 'https://gw.alicdn.com/tfs/TB1U9iBCSzqK1RjSZPcXXbTepXa-102-102.png', tag: '离职' },
  { iconUrl: 'https://gw.alicdn.com/tfs/TB11pS_CFzqK1RjSZSgXXcpAVXa-102-102.png', tag: '费用申请' },
  { iconUrl: 'https://gw.alicdn.com/tfs/TB1t695CFYqK1RjSZLeXXbXppXa-102-102.png', tag: '用章申请' },
  { iconUrl: 'https://gw.alicdn.com/tfs/TB13f_aCQzoK1RjSZFlXXai4VXa-102-102.png', tag: '携章外出' },
  { iconUrl: 'https://gw.alicdn.com/tfs/TB1_YG.COrpK1RjSZFhXXXSdXXa-102-102.png', tag: '学期内分期' },
  { iconUrl: 'https://gw.alicdn.com/tfs/TB1_YG.COrpK1RjSZFhXXXSdXXa-102-102.png', tag: '特殊学费' },
  { iconUrl: 'https://gw.alicdn.com/tfs/TB1Yfa0CG6qK1RjSZFmXXX0PFXa-112-112.png', tag: '充值卡申领' },
  { iconUrl: 'https://gw.alicdn.com/tfs/TB1e76lCOLaK1RjSZFxXXamPFXa-112-112.png', tag: '礼品申领' },
  { iconUrl: 'https://gw.alicdn.com/tfs/TB1FNG.CMHqK1RjSZFgXXa7JXXa-102-102.png', tag: '邮寄快递申请' },
  { iconUrl: 'https://gw.alicdn.com/imgextra/i3/O1CN01LLn0YV1LhBXs7T2iO_!!6000000001330-2-tps-120-120.png', tag: '合同审批' },
  { iconUrl: 'https://gw.alicdn.com/tfs/TB1e76lCOLaK1RjSZFxXXamPFXa-112-112.png', tag: '合同借阅' },
  { iconUrl: 'https://gw.alicdn.com/tfs/TB1e76lCOLaK1RjSZFxXXamPFXa-112-112.png', tag: '魔点临时开门权限' },
  { iconUrl: 'https://gw.alicdn.com/tfs/TB1bHOWCSzqK1RjSZFjXXblCFXa-112-112.png', tag: '北京科技园车证审批' },
  { iconUrl: 'https://gw.alicdn.com/tfs/TB1e76lCOLaK1RjSZFxXXamPFXa-112-112.png', tag: '魔点访客提前预约审批' }
]
