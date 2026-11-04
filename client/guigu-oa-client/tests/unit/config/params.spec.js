import { describe, expect, it } from 'vitest'
import { emptyMessageOf } from '../support/form-rules'
import {
  PARAMS_PERMISSIONS,
  VALUE_TYPE_OPTIONS,
  emptyForm,
  formRules,
  listRequest,
  presentForm,
  presentListPage,
  refreshRequest,
  removeRequest,
  valueTypeLabel,
  writeRequest,
} from '@/config/params'

describe('config/params', () => {
  describe('presentListPage', () => {
    it('presents 参数 rows with key, value, type, builtin tag, and time', () => {
      expect(
        presentListPage({
          records: [
            {
              id: 1,
              configKey: 'sys.account.registerUser',
              configValue: 'false',
              configName: '是否开放注册',
              valueType: 'boolean',
              builtin: 1,
              remark: '关闭时注册页不可用',
              createTime: '2026-11-04 10:00:00',
            },
            {
              id: 2,
              configKey: 't252_custom',
              configValue: 'hello',
              configName: '自定义参数',
              valueType: 'string',
              builtin: 0,
              createTime: '2026-11-04 10:01:00',
            },
          ],
          total: 2,
          pages: 1,
        }),
      ).toEqual({
        records: [
          {
            id: 1,
            configKey: 'sys.account.registerUser',
            configValue: 'false',
            configName: '是否开放注册',
            valueType: 'boolean',
            builtin: 1,
            builtinLabel: '内置',
            valueTypeLabel: '布尔',
            remark: '关闭时注册页不可用',
            createTime: '2026-11-04 10:00:00',
          },
          {
            id: 2,
            configKey: 't252_custom',
            configValue: 'hello',
            configName: '自定义参数',
            valueType: 'string',
            builtin: 0,
            builtinLabel: '自定义',
            valueTypeLabel: '字符串',
            remark: undefined,
            createTime: '2026-11-04 10:01:00',
          },
        ],
        total: 2,
        pages: 1,
      })
    })
  })

  describe('参数 API', () => {
    it('pages 参数 with GET /admin/system/config/{page}/{limit}', () => {
      expect(listRequest(1, 10, { configName: '注册' })).toEqual({
        method: 'get',
        url: '/admin/system/config/1/10',
        params: { configName: '注册' },
      })
    })

    it('creates a 参数 with POST /admin/system/config/save and a default string type', () => {
      expect(writeRequest({ configKey: 't252_custom', configName: '自定义参数' })).toEqual({
        method: 'post',
        url: '/admin/system/config/save',
        data: {
          configKey: 't252_custom',
          configName: '自定义参数',
          configValue: '',
          valueType: 'string',
          remark: '',
        },
      })
    })

    it('updates a 参数 with PUT /admin/system/config/update and its id', () => {
      expect(
        writeRequest({
          id: 2,
          configKey: 't252_custom',
          configName: '自定义参数',
          configValue: 'v2',
          valueType: 'number',
          remark: '说明',
          builtin: 0,
        }),
      ).toEqual({
        method: 'put',
        url: '/admin/system/config/update',
        data: {
          id: 2,
          configKey: 't252_custom',
          configName: '自定义参数',
          configValue: 'v2',
          valueType: 'number',
          remark: '说明',
        },
      })
    })

    it('sends only id and value when updating a builtin 参数', () => {
      expect(
        writeRequest({
          id: 1,
          configKey: 'sys.account.registerUser',
          configName: '是否开放注册',
          configValue: 'true',
          valueType: 'boolean',
          builtin: 1,
          remark: '不改',
        }),
      ).toEqual({
        method: 'put',
        url: '/admin/system/config/update',
        data: { id: 1, configValue: 'true' },
      })
    })

    it('deletes a 参数 with DELETE /admin/system/config/remove/{id}', () => {
      expect(removeRequest(2)).toEqual({
        method: 'delete',
        url: '/admin/system/config/remove/2',
      })
    })

    it('refreshes the config cache with POST /admin/system/config/refresh', () => {
      expect(refreshRequest()).toEqual({
        method: 'post',
        url: '/admin/system/config/refresh',
      })
    })
  })

  describe('presentForm', () => {
    it('starts 新建 as a custom string 参数', () => {
      expect(emptyForm()).toEqual({
        id: '',
        configKey: '',
        configValue: '',
        configName: '',
        valueType: 'string',
        builtin: 0,
        remark: '',
      })
    })

    it('loads an existing 参数 into the form including builtin', () => {
      expect(
        presentForm({
          id: 1,
          configKey: 'sys.account.registerUser',
          configValue: 'false',
          configName: '是否开放注册',
          valueType: 'boolean',
          builtin: 1,
          remark: '说明',
        }),
      ).toEqual({
        id: 1,
        configKey: 'sys.account.registerUser',
        configValue: 'false',
        configName: '是否开放注册',
        valueType: 'boolean',
        builtin: 1,
        remark: '说明',
      })
    })
  })

  describe('类型与表单校验', () => {
    it('exposes the three value types and their labels', () => {
      expect(VALUE_TYPE_OPTIONS.map((option) => option.value)).toEqual([
        'string',
        'number',
        'boolean',
      ])
      expect(VALUE_TYPE_OPTIONS.map((option) => option.label)).toEqual(['字符串', '数字', '布尔'])
      expect(valueTypeLabel('boolean')).toBe('布尔')
      expect(valueTypeLabel('unknown')).toBe('')
    })

    it('mirrors the server 必填 messages for key and name', () => {
      const rules = formRules()
      expect(Object.keys(rules).sort()).toEqual(['configKey', 'configName'])
      expect(emptyMessageOf(rules.configKey[0])).toBe('参数键不能为空')
      expect(emptyMessageOf(rules.configName[0])).toBe('参数名称不能为空')
    })

    it('names the three button points', () => {
      expect(PARAMS_PERMISSIONS).toEqual({
        add: 'system:config:add',
        edit: 'system:config:edit',
        remove: 'system:config:remove',
      })
    })
  })
})
