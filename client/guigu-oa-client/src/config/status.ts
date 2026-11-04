/**
 * 启用/停用两档（#247 部门、#248 岗位、#251 字典共用），顺序即下拉顺序。
 * 各页保留自己的别名，避免调用方同时改 import。
 */
export const ENABLED_STATUS_OPTIONS = [
  { value: 1, label: '正常' },
  { value: 0, label: '停用' },
] as const
