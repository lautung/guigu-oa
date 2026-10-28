import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'

import ListLoadError from '@/components/ListLoadError.vue'
import ListPagination from '@/components/ListPagination.vue'
import { ensureDomApis } from '../support/harness'

ensureDomApis()

const global = { plugins: [ElementPlus] }

describe('ListPagination', () => {
  it('forwards page and size changes and exposes the page size choices', () => {
    const wrapper = mount(ListPagination, {
      props: { page: 2, size: 10, total: 42 },
      global,
    })
    const pagination = wrapper.findComponent({ name: 'ElPagination' })

    expect(pagination.props()).toMatchObject({ currentPage: 2, pageSize: 10, total: 42 })
    expect(pagination.props('pageSizes')).toEqual([5, 10, 20, 30, 40, 50, 100])

    pagination.vm.$emit('current-change', 3)
    pagination.vm.$emit('size-change', 20)

    expect(wrapper.emitted('page-change')).toEqual([[3]])
    expect(wrapper.emitted('size-change')).toEqual([[20]])
  })

  it('drops the page size selector in compact mode', () => {
    const wrapper = mount(ListPagination, {
      props: { page: 1, size: 5, total: 5, compact: true },
      global,
    })
    const pagination = wrapper.findComponent({ name: 'ElPagination' })

    expect(pagination.props('layout')).toBe('prev, pager, next')
    expect(pagination.props('background')).toBe(true)
  })
})

describe('ListLoadError', () => {
  const alertStub = {
    name: 'ElAlert',
    props: ['title'],
    template: '<section><slot /></section>',
  }
  const buttonStub = { name: 'ElButton', template: '<button><slot /></button>' }
  const stubs = { 'el-alert': alertStub, 'el-button': buttonStub }

  it('shows the error and emits retry when loading failed', async () => {
    const wrapper = mount(ListLoadError, {
      props: { status: 'failed', error: '网络不可用' },
      global: { stubs },
    })

    expect(wrapper.findComponent({ name: 'ElAlert' }).props('title')).toBe('网络不可用')
    await wrapper.find('button').trigger('click')
    expect(wrapper.emitted('retry')).toEqual([[]])
  })

  it('falls back to a generic title and stays hidden while loading', () => {
    const failed = mount(ListLoadError, { props: { status: 'failed' }, global: { stubs } })
    expect(failed.findComponent({ name: 'ElAlert' }).props('title')).toBe('加载失败')

    const ready = mount(ListLoadError, { props: { status: 'ready' }, global: { stubs } })
    expect(ready.findComponent({ name: 'ElAlert' }).exists()).toBe(false)
  })
})
