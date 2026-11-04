import { createI18n as createVueI18n } from 'vue-i18n'
import elementEn from 'element-plus/es/locale/lang/en'
import elementZhCn from 'element-plus/es/locale/lang/zh-cn'

import en from './en'
import zhCN from './zh-CN'

export const LOCALE_STORAGE_KEY = 'guigu_oa_client_locale'
export const DEFAULT_LOCALE = 'zh-CN'
export const SUPPORTED_LOCALES = ['zh-CN', 'en'] as const

export type AppLocale = (typeof SUPPORTED_LOCALES)[number]

/** 语言下拉的固定选项：两种 locale 下都写成自身语言（中文/English），标签走 i18n key。 */
export const LOCALE_OPTIONS: readonly { value: AppLocale; labelKey: string }[] = [
  { value: 'zh-CN', labelKey: 'shell.locale.zhCN' },
  { value: 'en', labelKey: 'shell.locale.en' },
]

type LocaleStorage = Pick<Storage, 'getItem' | 'setItem'>

function safeStorage(): LocaleStorage | null {
  try {
    return typeof localStorage === 'undefined' ? null : localStorage
  } catch {
    return null
  }
}

export function isLocale(value: unknown): value is AppLocale {
  return typeof value === 'string' && (SUPPORTED_LOCALES as readonly string[]).includes(value)
}

/** 持久化的 locale 读取：只认支持的值，其余（缺失、损坏、旧值）一律回默认。 */
export function readLocale(storage: LocaleStorage | null = safeStorage()): AppLocale {
  try {
    const stored = storage?.getItem(LOCALE_STORAGE_KEY)
    return isLocale(stored) ? stored : DEFAULT_LOCALE
  } catch {
    return DEFAULT_LOCALE
  }
}

/** 建一个 vue-i18n 实例；默认取持久化值，显式传值供测试与启动兜底。 */
export function createI18n(locale: AppLocale = readLocale()) {
  return createVueI18n({
    legacy: false,
    locale,
    fallbackLocale: DEFAULT_LOCALE,
    messages: { 'zh-CN': zhCN, en },
  })
}

/** 应用唯一的 locale owner；组件通过 `translate` 读取并随 locale 变化重渲染。 */
export const i18n = createI18n()

export function currentLocale(): AppLocale {
  return i18n.global.locale.value as AppLocale
}

export function setLocale(locale: AppLocale, storage: LocaleStorage | null = safeStorage()): void {
  i18n.global.locale.value = locale
  try {
    storage?.setItem(LOCALE_STORAGE_KEY, locale)
  } catch {
    // 写失败只影响下次启动的默认值。
  }
}

export function translate(key: string, named?: Record<string, unknown>): string {
  return named ? i18n.global.t(key, named) : i18n.global.t(key)
}

export type ElementLocale = typeof elementZhCn

const ELEMENT_LOCALES: Record<AppLocale, ElementLocale> = {
  'zh-CN': elementZhCn,
  en: elementEn,
}

/** ElConfigProvider 的 locale 投影：外壳按当前 locale 切换 Element Plus 内建文案。 */
export function elementLocaleFor(locale: AppLocale = currentLocale()): ElementLocale {
  return ELEMENT_LOCALES[locale]
}
