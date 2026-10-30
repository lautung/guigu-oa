/**
 * 断言必填规则空值文案的小工具:以空串跑一遍规则的 validator,返回错误消息。
 * 各 config module 的 formRules 文案镜像服务端 DTO(ADR 0038),用例逐字比对时复用。
 */
export function emptyMessageOf(rule) {
  let error
  rule.validator({}, '', (result) => {
    error = result
  })
  return error && error.message
}
