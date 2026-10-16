import { canInitiate } from '@/config/process-template'
import { ROUTES } from '@/navigation/constants'
import type {
  EntityId,
  FormDataRecord,
  FormDesignerOption,
  FormRule,
  ProcessTemplateRecord,
} from '@/types/domain'

export const START_SUCCESS_MESSAGE = '发起成功'

export function applyPath(templateId: EntityId): string {
  return `${ROUTES.START}/${templateId}`
}

export function postStartSuccessRoute(): string {
  return ROUTES.STARTED
}

function parseJson<T>(value: unknown, fallback: T): T {
  if (!value) {
    return fallback
  }
  if (typeof value === 'object') {
    return value as T
  }
  if (typeof value !== 'string') {
    return fallback
  }
  try {
    return JSON.parse(value) as T
  } catch {
    return fallback
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function presentApplyOption(option: FormDesignerOption = {}): FormDesignerOption {
  const formOptions = isRecord(option.form) ? option.form : {}
  return Object.assign({}, option, {
    submitBtn: true,
    form: Object.assign({}, formOptions, {
      labelPosition: 'top',
    }),
  })
}

export type ApplyTemplate =
  | { ok: false; redirectTo: string; reason?: string }
  | {
      ok: true
      id: EntityId
      processTypeId: EntityId
      name: string
      rule: FormRule[]
      option: FormDesignerOption
    }

export function presentApplyTemplate(record: ProcessTemplateRecord): ApplyTemplate {
  if (!canInitiate(record)) {
    return {
      ok: false,
      redirectTo: ROUTES.START,
      ...(record?.status === 1
        ? { reason: record.availabilityMessage || '暂不可发起，需核实原审批内容' }
        : {}),
    }
  }

  if (record.id == null || record.processTypeId == null) {
    return {
      ok: false,
      redirectTo: ROUTES.START,
    }
  }

  return {
    ok: true,
    id: record.id,
    processTypeId: record.processTypeId,
    name: record.name || '',
    rule: parseJson<FormRule[]>(record.formProps, []),
    option: presentApplyOption(parseJson<FormDesignerOption>(record.formOptions, {})),
  }
}

export interface BuildStartPayloadInput {
  template: { id: EntityId; processTypeId: EntityId }
  formData?: FormDataRecord
  rule?: FormRule[]
  attachmentDraftId?: string
  attachmentIds?: EntityId[]
  ccUserIds?: EntityId[]
}

export function buildStartPayload({
  template,
  formData = {},
  rule = [],
  attachmentDraftId,
  attachmentIds = [],
  ccUserIds = [],
}: BuildStartPayloadInput): {
  processTemplateId: EntityId
  processTypeId: EntityId
  formValues: string
  attachmentDraftId?: string
  attachmentIds?: EntityId[]
  ccUserIds?: EntityId[]
} {
  const formShowData: FormDataRecord = {}
  rule.forEach((item) => {
    if (item.field in formData) {
      formShowData[item.title] = formData[item.field]
    }
  })

  const payload: {
    processTemplateId: EntityId
    processTypeId: EntityId
    formValues: string
    attachmentDraftId?: string
    attachmentIds?: EntityId[]
    ccUserIds?: EntityId[]
  } = {
    processTemplateId: template.id,
    processTypeId: template.processTypeId,
    formValues: JSON.stringify({ formData, formShowData }),
  }
  if (attachmentDraftId) {
    payload.attachmentDraftId = attachmentDraftId
    payload.attachmentIds = attachmentIds
  }
  if (ccUserIds.length > 0) {
    payload.ccUserIds = ccUserIds
  }
  return payload
}
