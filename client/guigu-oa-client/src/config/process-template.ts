import type {
  ApiClient,
  ApiRequestConfig,
  ApiResponse,
  FormDesignerOption,
  FormRule,
  PageResult,
  ProcessTemplateListItem,
  ProcessTemplateRecord,
  ProcessTemplateStartGroup,
  ProcessTypeRecord,
  PresentedProcessTemplate,
} from '@/types/domain'

const PROCESS_TEMPLATE_API = '/admin/process/processTemplate'

export function canInitiate(record: ProcessTemplateRecord | null | undefined): boolean {
  return !!record && record.status === 1 && record.availableForInitiation !== false
}

export function canWrite(record: ProcessTemplateRecord | null | undefined): boolean {
  return !!record && record.status !== 1
}

export function presentListRecord(record: ProcessTemplateRecord = {}): ProcessTemplateListItem {
  const writable = canWrite(record)

  return {
    id: record.id || '',
    name: record.name,
    iconUrl: record.iconUrl,
    processTypeId: record.processTypeId,
    processTypeName: record.processTypeName,
    formProps: record.formProps || '',
    formOptions: record.formOptions || '',
    processDefinitionKey: record.processDefinitionKey,
    processDefinitionPath: record.processDefinitionPath,
    description: record.description,
    status: record.status,
    statusLabel: record.status === 1 ? '已发布' : '未发布',
    createTime: record.createTime,
    updateTime: record.updateTime,
    canEdit: writable,
    canDelete: writable,
    canPublish: writable,
    ...(record.availableForInitiation == null
      ? {}
      : { availableForInitiation: record.availableForInitiation }),
    ...(record.availabilityMessage ? { availabilityMessage: record.availabilityMessage } : {}),
  }
}

export function presentListPage(page: PageResult<ProcessTemplateRecord> = {}) {
  return {
    records: (page.records || []).map(presentListRecord),
    total: page.total || 0,
    pages: page.pages || 0,
  }
}

export function listRequest(page: number, limit: number): ApiRequestConfig {
  return {
    method: 'get',
    url: `${PROCESS_TEMPLATE_API}/${page}/${limit}`,
  }
}

export function emptyForm(): PresentedProcessTemplate {
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
    status: 0,
  }
}

function parseJson<T>(value: string | undefined, fallback: T): T {
  try {
    return value ? (JSON.parse(value) as T) : fallback
  } catch {
    return fallback
  }
}

export function presentDesignerState(form: ProcessTemplateRecord = {}): {
  rule: FormRule[]
  option: FormDesignerOption
} {
  return {
    rule: parseJson(form.formProps, []),
    option: parseJson(form.formOptions, {}),
  }
}

const ZIP_MIME_TYPES = ['application/zip', 'application/x-zip', 'application/x-zip-compressed']
const MAX_ZIP_BYTES = 2048 * 1024

interface UploadFileCandidate {
  type?: string
  name?: string
  size?: number
}

function isZipFile(file: UploadFileCandidate = {}): boolean {
  return ZIP_MIME_TYPES.includes(file.type || '') || /\.zip$/i.test(file.name || '')
}

export function zipUploadError(file: UploadFileCandidate = {}): string | null {
  if (!isZipFile(file)) {
    return '只能上传 zip 压缩文件'
  }
  if ((file.size || 0) > MAX_ZIP_BYTES) {
    return '文件不能超过 2048kb'
  }
  return null
}

function presentStartGroups(types: ProcessTypeRecord[] = []): ProcessTemplateStartGroup[] {
  return types
    .map((type) => ({
      id: type.id,
      name: type.name,
      templates: (type.processTemplateList || []).filter(canInitiate).map((template) => ({
        id: template.id,
        name: template.name,
        iconUrl: template.iconUrl,
      })),
    }))
    .filter((group) => group.templates.length > 0)
}

export async function publish(
  template: ProcessTemplateRecord,
  request: ApiClient,
): Promise<ApiResponse<unknown>> {
  if (!canWrite(template)) {
    return Promise.reject(new Error('已发布的审批模板不能再发布'))
  }
  return request({
    method: 'post',
    url: `${PROCESS_TEMPLATE_API}/publish/${template.id}`,
  })
}

export async function removeUnpublished(
  template: ProcessTemplateRecord,
  request: ApiClient,
): Promise<ApiResponse<unknown>> {
  if (!canWrite(template)) {
    return Promise.reject(new Error('已发布的审批模板不能删除'))
  }
  return request({
    method: 'delete',
    url: `${PROCESS_TEMPLATE_API}/remove/${template.id}`,
  })
}

export async function findStartable(request: ApiClient): Promise<ProcessTemplateStartGroup[]> {
  const response = await request<ProcessTypeRecord[]>({
    method: 'get',
    url: '/admin/process/findStartable',
  })
  return presentStartGroups(response.data || [])
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
  { iconUrl: 'https://gw.alicdn.com/tfs/TB1U9iBCSzqK1RjSZPcXXbTepXa-112-112.png', tag: '离职' },
  { iconUrl: 'https://gw.alicdn.com/tfs/TB11pS_CFzqK1RjSZSgXXcpAVXa-102-102.png', tag: '费用申请' },
  { iconUrl: 'https://gw.alicdn.com/tfs/TB1t695CFYqK1RjSZLeXXbXppXa-102-102.png', tag: '用章申请' },
  { iconUrl: 'https://gw.alicdn.com/tfs/TB13f_aCQzoK1RjSZFlXXai4VXa-112-112.png', tag: '携章外出' },
  {
    iconUrl: 'https://gw.alicdn.com/tfs/TB1_YG.COrpK1RjSZFhXXXSdXXa-102-102.png',
    tag: '学期内分期',
  },
  { iconUrl: 'https://gw.alicdn.com/tfs/TB1_YG.COrpK1RjSZFhXXXSdXXa-102-102.png', tag: '特殊学费' },
  {
    iconUrl: 'https://gw.alicdn.com/tfs/TB1Yfa0CG6qK1RjSZFmXXX0PFXa-112-112.png',
    tag: '充值卡申领',
  },
  { iconUrl: 'https://gw.alicdn.com/tfs/TB1e76lCOLaK1RjSZFxXXamPFXa-112-112.png', tag: '礼品申领' },
  {
    iconUrl: 'https://gw.alicdn.com/tfs/TB1FNG.CMHqK1RjSZFgXXa7JXXa-102-102.png',
    tag: '邮寄快递申请',
  },
  {
    iconUrl:
      'https://gw.alicdn.com/imgextra/i3/O1CN01LLn0YV1LhBXs7T2iO_!!6000000001330-2-tps-120-120.png',
    tag: '合同审批',
  },
  { iconUrl: 'https://gw.alicdn.com/tfs/TB1e76lCOLaK1RjSZFxXXamPFXa-112-112.png', tag: '合同借阅' },
  {
    iconUrl: 'https://gw.alicdn.com/tfs/TB1e76lCOLaK1RjSZFxXXamPFXa-112-112.png',
    tag: '魔点临时开门权限',
  },
  {
    iconUrl: 'https://gw.alicdn.com/tfs/TB1bHOWCSzqK1RjSZFjXXblCFXa-112-112.png',
    tag: '北京科技园车证审批',
  },
  {
    iconUrl: 'https://gw.alicdn.com/tfs/TB1e76lCOLaK1RjSZFxXXamPFXa-112-112.png',
    tag: '魔点访客提前预约审批',
  },
] as const
