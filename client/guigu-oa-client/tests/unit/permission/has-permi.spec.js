import { defineComponent } from 'vue'
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { hasPermission } from '@/permission'
import { mountOptions, setupSession } from '../support/harness'

const GuardedButton = defineComponent({
  props: { permission: { type: String, required: true } },
  template:
    '<div><button v-hasPermi="permission" class="guarded">删除</button><button>取消</button></div>',
})

function mountGuarded(permissions, permission = 'system:user:add') {
  const { pinia, user } = setupSession({ permissions })
  const wrapper = mount(GuardedButton, {
    props: { permission },
    global: mountOptions(pinia).global,
  })
  return { wrapper, user }
}

describe('v-hasPermi 指令', () => {
  it('removes the guarded DOM node when the permission is absent', () => {
    const { wrapper } = mountGuarded([])

    expect(wrapper.find('.guarded').exists()).toBe(false)
    expect(wrapper.findAll('button')).toHaveLength(1)
  })

  it('keeps the guarded DOM node when the permission is granted', () => {
    const { wrapper } = mountGuarded(['system:user:add'])

    expect(wrapper.find('.guarded').exists()).toBe(true)
    expect(wrapper.findAll('button')).toHaveLength(2)
  })

  it('removes the guarded DOM node when permissions are withdrawn on update', async () => {
    const { wrapper, user } = mountGuarded(['system:user:add'])
    expect(wrapper.find('.guarded').exists()).toBe(true)

    user.permissions = []
    wrapper.vm.$forceUpdate()
    await wrapper.vm.$nextTick()

    expect(wrapper.find('.guarded').exists()).toBe(false)
  })
})

describe('hasPermission 脚本判定', () => {
  it('answers from the current user permissions', () => {
    const { user } = setupSession({ permissions: ['system:user:add'] })

    expect(hasPermission('system:user:add')).toBe(true)
    expect(hasPermission('system:user:delete')).toBe(false)

    user.permissions = []
    expect(hasPermission('system:user:add')).toBe(false)
  })

  it('treats an empty permission name as not granted', () => {
    setupSession({ permissions: ['system:user:add'] })

    expect(hasPermission('')).toBe(false)
  })
})
