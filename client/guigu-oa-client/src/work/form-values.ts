import type { FormDataRecord, WorkSummaryField } from '@/types/domain'

export function formFieldText(value: unknown): string {
  if (value === null || value === undefined) return ''
  if (Array.isArray(value)) {
    return value
      .map(formFieldText)
      .filter((part) => part !== '')
      .join('、')
  }
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

export function formFields(formShowData: FormDataRecord = {}): WorkSummaryField[] {
  return Object.keys(formShowData).map((key) => ({
    key,
    value: formFieldText(formShowData[key]),
  }))
}
