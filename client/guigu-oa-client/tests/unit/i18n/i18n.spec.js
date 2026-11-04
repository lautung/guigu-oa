import { afterEach, describe, expect, it } from 'vitest'

import {
  DEFAULT_LOCALE,
  LOCALE_STORAGE_KEY,
  SUPPORTED_LOCALES,
  createI18n,
  currentLocale,
  elementLocaleFor,
  readLocale,
  setLocale,
  translate,
} from '@/i18n'

describe('i18n locale 状态', () => {
  afterEach(() => {
    setLocale(DEFAULT_LOCALE)
    localStorage.clear()
  })

  it('defaults to zh-CN when nothing is stored', () => {
    localStorage.clear()

    expect(DEFAULT_LOCALE).toBe('zh-CN')
    expect(SUPPORTED_LOCALES).toEqual(['zh-CN', 'en'])
    expect(readLocale()).toBe('zh-CN')
    expect(currentLocale()).toBe('zh-CN')
  })

  it('ignores unsupported stored values', () => {
    localStorage.setItem(LOCALE_STORAGE_KEY, 'fr')

    expect(readLocale()).toBe('zh-CN')
  })

  it('persists the selected locale and translates from the language pack', () => {
    setLocale('en')

    expect(localStorage.getItem(LOCALE_STORAGE_KEY)).toBe('en')
    expect(currentLocale()).toBe('en')
    expect(translate('shell.modules.workbench')).toBe('Workbench')
    expect(translate('shell.breadcrumb.home')).toBe('Home')
    expect(translate('login.submit')).toBe('Sign in')
  })

  it('creates a fresh instance from the persisted locale', () => {
    localStorage.setItem(LOCALE_STORAGE_KEY, 'en')

    const instance = createI18n()

    expect(instance.global.locale.value).toBe('en')
    expect(instance.global.t('login.username')).toBe('Username')
  })

  it('maps the Element Plus locale for each supported locale', () => {
    expect(elementLocaleFor('zh-CN').name).toBe('zh-cn')
    expect(elementLocaleFor('zh-CN').el.pagination.total).toBe('共 {total} 条')
    expect(elementLocaleFor('en').name).toBe('en')
    expect(elementLocaleFor('en').el.pagination.total).toBe('Total {total}')
  })
})
