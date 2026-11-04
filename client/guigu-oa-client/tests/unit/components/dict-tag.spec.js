import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

import ElementPlus from 'element-plus'
import DictTag from '@/components/DictTag.vue'
import { clearDictCache } from '@/dict/use-dict'
import { ensureDomApis, installHttp, ok, setupSession } from '../support/harness'

ensureDomApis()

let http

beforeEach(() => {
  clearDictCache()
  setupSession()
  http = installHttp(() =>
    ok({
      sys_notice_type: [
        { dictLabel: '通知', dictValue: '1', sortValue: 1 },
        { dictLabel: '公告', dictValue: '2', sortValue: 2 },
      ],
    }),
  )
})

afterEach(() => {
  http.restore()
  clearDictCache()
})

function mountTag(props) {
  const { pinia } = setupSession()
  return mount(DictTag, {
    props,
    global: { plugins: [pinia, ElementPlus] },
  })
}

describe('DictTag', () => {
  it('renders the dictionary label when the value hits', async () => {
    const wrapper = mountTag({ dictType: 'sys_notice_type', value: 2, tagType: 'warning' })
    await flushPromises()

    expect(wrapper.find('.el-tag').text()).toBe('公告')
    expect(wrapper.find('.dict-tag-fallback').exists()).toBe(false)
  })

  it('matches numeric record values against string dictionary values', async () => {
    const wrapper = mountTag({ dictType: 'sys_notice_type', value: '1' })
    await flushPromises()

    expect(wrapper.find('.el-tag').text()).toBe('通知')
  })

  it('falls back to the raw value when the dictionary has no match', async () => {
    const wrapper = mountTag({ dictType: 'sys_notice_type', value: 9 })
    await flushPromises()

    expect(wrapper.find('.el-tag').exists()).toBe(false)
    expect(wrapper.find('.dict-tag-fallback').text()).toBe('9')
  })

  it('shows the empty placeholder for null/undefined values', async () => {
    const empty = mountTag({ dictType: 'sys_notice_type', value: null })
    const missing = mountTag({ dictType: 'sys_notice_type' })
    await flushPromises()

    expect(empty.find('.dict-tag-fallback').text()).toBe('—')
    expect(missing.find('.dict-tag-fallback').text()).toBe('—')
    empty.unmount()
    missing.unmount()
  })
})
