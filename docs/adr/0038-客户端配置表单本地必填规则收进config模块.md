# 客户端配置表单本地必填规则收进config模块

ADR 0034 落地时把「客户端配置表单的本地必填规则」记为未做项,把握是「服务端是权威,客户端规则是重复一份」。后果是四个配置表单(#157)提交前没有任何本地提示:新建用户密码为空要等一次 201 往返才拿到「密码不能为空」,三个对话框的 `el-form` 连 `ref` 与 `validate()` 都没有,templateSet 的名称输入框不标必填(只有 `canSave` 的 JS 门)。

决定:**补本地必填规则,规则收进 config module**,文案逐字镜像服务端 DTO 注解(ADR 0034 的必填口径、ADR 0033 的信封文案),服务端仍是权威——本地规则只省一次往返、让表单直接标红。

- `src/config/form-rules.ts` 是必填语义的唯一 owner:`requiredRule(message)` 返回 `{ required: true, validator }`,`required: true` 让 Element Plus 画红星;validator 里**字符串按 trim 后判空**(镜像 `@NotBlank`,空白串也算空),**数值只在缺省时判空**(镜像 `@NotNull`,模板的 `processTypeId` 传 0 不误杀)。
- 各 config module 导出 `formRules()`:`sys-user`(username/password/name)、`sys-role`(roleName/roleCode)、`process-type`(name)、`template-editor`(name/processTypeId),键之外的字段(phone、description、iconUrl)选填,与 save DTO 一致。module 不 import Element Plus,规则是可单测的纯数据。
- 三个对话框页(`sysUser`/`sysRole`/`processType` 的 index.vue)把 `:model`/`:rules`/`prop` 接上,`save()` 先 `validate()`、不过不发请求;对话框加 `destroy-on-close`,重开不带上一轮的红字。
- `templateSet.vue` 只加标记与内联提示(`:model`/`:rules` + 两个 `prop`),拦截门仍在 template-editor 的 `canSave`——提交按钮本来就禁用,本地规则负责让必填可见、触碰后标红,不再加一道重复的门。

## 考虑过的替代方案

- **规则直接写在视图里**(如登录页的内联 `loginRules`):登录页是历史遗留,配置表单已有 config module 作为规则的家,写回视图会让文案无法单测、四个页面各抄一份。
- **用 Element Plus 内建 required 规则(`{ required: true, message, whitespace: true }`)**:判空语义外包给 async-validator,单测只能断言规则形状;自定义 validator 让「trim 判空」与「数值缺省」都有行为级单测,语义与 `@NotBlank`/`@NotNull` 的对应关系一眼可见。
- **顺手把 sysMenu 的对话框也补上**:sysMenu 服务端必填是 parentId/name/type/path(ADR 0034),但客户端表单按 type 分支渲染,规则要跟类型联动,#157 圈定的是四个表单;sysMenu 留作后续。

## 后果

- 四个表单空必填在提交前就标红,文案与服务端一致;负例路径的失败顺序不变(客户端本地拦截只是多一层,服务端 201 仍兜底)。
- vitest 必须把 `element-plus` inline 进测试管线(`vite.config.js` 的 `test.server.deps.inline`):外置时 EP 内部 import 的 async-validator 与测试侧形成两份实例,`el-form.validate()` 在单测里静默失真——空必填也判 VALID、错误消息不上屏,而生产(rollup 单实例)没有这个问题。inline 后单测里的校验行为与真实浏览器一致(template-edit e2e 复跑 17 通过)。
- Element Plus 的错误上屏走 100ms `refDebounced`,视图测试断言错误 DOM 前需真等过 debounce。
- `src/config/form-rules.ts` 成为唯一必填语义点;后续给 sysMenu 或其他表单补规则时,只写 `formRules()` 并镜像服务端文案,不重复实现判空。
- 回归锚点:`tests/unit/config/form-rules.spec.js`、四个 module spec 的 `formRules` 用例、`config-lists`/`sys-user-and-auth` 的本地拦截用例、`template-set-states` 的接线用例。
