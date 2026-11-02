# `/info` 返回 roles、permissions 与 menus，客户端静态路由只做过滤

ADR 0052 写 `/info` 返回 `{roles, permissions, menus}`，并说「客户端据此注册路由」。但客户端是静态路由表（ADR 0020 保留），没有 `addRoute`；当前 `/info` 的 `roles` 是硬编码字符串 `"[admin]"`，`buttons` 从未被客户端消费，客户端类型里也没有 roles/permissions。本条钉死响应合同与消费方式，取代 ADR 0052 的「注册路由」一句。

决定：

- **端点不变**：仍是 `GET /admin/system/index/info`，只改响应体；硬切 `configMenus` 与 `buttons`，不保留兼容别名——自用产品，服务端与客户端同一波上线。
- **形状**：

```json
{
  "name": "管理员",
  "avatar": "",
  "roles": ["super_admin"],
  "permissions": ["system:user:add", "system:role:assignAuth"],
  "menus": [
    { "path": "/system", "parentPath": null, "title": "系统管理",
      "type": 0, "hidden": false, "sort": 1, "icon": "el-icon-setting" },
    { "path": "/system/sysUser", "parentPath": "/system", "title": "用户管理",
      "type": 1, "hidden": false, "sort": 1, "icon": null }
  ]
}
```

- `roles` = 角色 key 数组，仅供展示，不参与任何判定；`permissions` = 按钮点标识数组（客户端转 Set）；`menus` 只含被授予的 `type 0/1` 及其祖先，路径一律绝对，隐藏页照发并带 `hidden:true`，按钮点不进菜单。
- `parentPath` 为空即一级；工作台与办事是客户端内置，不出现在 `menus`。
- `icon` 可空；列已在 `sys_menu`，外壳可能要用于导航，先纳入合同，后加不如现在加。
- **客户端静态路由拥有组件**：路由表继续声明路径、组件与 `meta`（ADR 0020），`menus` 只决定导航可见与守卫是否放行；不引入 `addRoute`，不把 DB 的 `component` 字符串当 import 路径。未授予页面直接打开时，客户端回落到默认落点，服务端仍以 205 兜底——安全边界在服务端方法级 gate。
- **按钮指令**：`v-hasPermi`（无权限移除 DOM，与若依一致）+ `hasPermission(perm)` 供脚本条件使用；服务端 gate 是唯一强制点。
- 数据范围不进 `/info`（ADR 0053/0057），客户端不做行级过滤。

考虑过的替代方案：

- **`menus` 带 `component`、客户端 `addRoute` 动态注册**（否决）：DB 字符串变成前端 chunk 路径，错一个字母就是运行时空白页；静态路由与 ADR 0020 的路径所有权一致。
- **保留 `configMenus`/`buttons` 别名过渡**（否决）：自用产品没有外部消费者，别名只会让两套形状长期并存。
- **`roles` 返回角色名或硬编码字符串**（否决）：角色 key 是稳定标识；硬编码 `"[admin]"` 已经证明没人敢用。
- **把数据范围塞进 `/info` 让前端过滤**（否决）：与服务端参数化查询冲突，前端过滤不构成任何保护。

后果：

- `IndexController` 与 `ConfigMenuVo` 换成新的响应 DTO；客户端 `UserInfo`/store 同步，权限波次先做最小管道，外壳的多标签与顶栏一级导航消费同一份 `menus`。
- 合同测试断言字段集合、绝对路径、隐藏页标记、按钮不进菜单、roles 为数组。
- ADR 0052 的「客户端据此注册路由」由本条取代；其余三层权限语义不变。

Related: ADR 0009/0018（列表读收口）；ADR 0020（路径所有权）；ADR 0044（会话核验）；ADR 0052/0053/0056/0057；issue #206/#209/#210。
