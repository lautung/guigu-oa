/**
 * 配置表单本地必填规则的唯一语义(ADR 0038):字符串按 trim 后判空,镜像服务端
 * `@NotBlank`;数值只在缺省时判空,镜像 `@NotNull`。文案由各 config module 提供,
 * 与服务端 DTO 注解逐字一致——服务端仍是权威,这里只是省一次往返。
 */
export type RuleCallback = (error?: Error) => void

export interface RequiredRule {
  required: true
  validator(rule: unknown, value: unknown, callback: RuleCallback): void
}

export function requiredRule(message: string): RequiredRule {
  return {
    required: true,
    validator(_rule, value, callback) {
      if (value == null || (typeof value === 'string' && !value.trim())) {
        callback(new Error(message))
        return
      }
      callback()
    },
  }
}
