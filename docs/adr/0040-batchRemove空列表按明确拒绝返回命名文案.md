# batchRemove 空列表按明确拒绝返回命名文案

`DELETE /admin/system/sysRole/batchRemove` 的请求体是裸 `List<Long>`。空列表的行为此前由 `SysRoleServiceImpl.removeByIds` 的 `return false` 决定：什么都不删、不回收授予关系、不动缓存，控制器落入 `Result.fail()` 分支，得到 **HTTP 200 + code 201 + 泛化文案「失败」**（#156）。这个形状既不是文档化的幂等无操作（issue 的原始描述把它当成 200 成功），也不是有文案的拒绝——是意外失败形状：信封码碰巧对，但文案不指认分支，也没有任何用例钉住，下一个人可能把它当缺陷改成成功，也可能加 `@NotEmpty` 造出第二个 201 生产者。#152（ADR 0034）与 #155（ADR 0037）先后把它挂为「未做」。

同族语义其实早已选定：单删 `remove/{id}` 对不存在/已删除的 id 也是 `false → 201「失败」`，`doAssign` 对不存在的角色抛 `GuiguException(201, "只能分配已存在的角色")`。「删不到东西 = 201 拒绝」是控制器已有的家族约定，空列表是它的退化情形。

决定：空列表（`[]`、全 null 元素过滤后为空）= **明确拒绝，`GuiguException(201, "删除列表不能为空")`**，即 HTTP 200 + code 201 + 命名文案。过滤非 null 后统一判空，`[]` 与 `[null]` 同文案（`roleIds == null` 只在 service 层防御；HTTP 上裸 `null` 请求体由消息解析先行拒绝，到不了这里）。判定留在 service：请求体没有 DTO 可挂 jsr303 注解（ADR 0033 的静态形状路线到此为止），而领域/状态判断本就归 service；`deleteByIds` 删 0 行（id 全不存在）仍走 `false → Result.fail()`，那是「有列表但删不到行」的另一个 201 成员，不与空列表混同。

## 考虑过的替代方案

- **空列表 = 成功无操作（200）**：issue #156 记录的原判「幂等无操作、不是错误」。但拒绝语义已被 service 的 `return false` 事先选定，改成成功要放宽行为；且与同族不一致——删不到行是 201，空列表反而 200 成功。统一客户端没有批量删除入口，脚本发空删除是调用方 bug，响亮失败更有用。
- **jsr303 `@NotEmpty` + 控制器类 `@Validated`**：裸 List 体要触发约束只能靠方法级校验，但 `@Valid` 在 MVC 参数解析期执行、早于 `@PreAuthorize` 的保证（ADR 0034 顺序契约的根基）不适用于方法校验 advice——它与方法安全 advice 的先后默认序不受本仓库控制，未授予用户 + 空列表可能从 205 变 201。
- **保持 201「失败」泛化文案只补测试**：契约能钉住，但「失败」是全仓兜底文案（`ResultCodeEnum.FAIL`），钉住它等于什么都没钉；testing.md 要求 201 断言钉住的 `$.message` 必须能指认分支。升格为命名文案是一行改动，顺手消掉「意外形状」。

## 后果

- 行为变化只有文案：空列表从 200+201「失败」变为 200+201「删除列表不能为空」，`[null]` 同步并入。信封码、HTTP 状态、删除行为（不删任何行）都不变；客户端与 e2e 不受影响（无批量删除入口）。
- 顺序契约不变：gate 仍是 service 之前的一道门，未授予用户 + 空列表 = 205（`configurationMenuGate`），授予用户才轮到判空文案——与 `doAssign` 的「只能分配已存在的角色」同型。
- 回归锚点：`SysRoleServiceImplTest`（service 层空/null/全 null → 201 文案，删 0 行仍 false）、`FlywayMigrationContractTest`（授予用户 `[]` → 201 文案；未授予 `[]` → 205）、接口套件 `b-role.spec.ts` B26。
