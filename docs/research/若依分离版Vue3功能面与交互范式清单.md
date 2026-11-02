# 若依分离版（RuoYi-Vue3）功能面与交互范式清单

> 调研票：[#207](https://github.com/lautung/guigu-oa/issues/207)。本文只描述若依分离版的**实际功能面与交互范式**，作为 #206 蓝图的**事实输入**。
>
> 不做的事：不判断本项目该不该引入某一能力（属蓝图票决策），不写实现方案，不搬运代码。文中代码路径仅用于溯源。
>
> 复杂度为**粗评**，口径：**低**＝单表 CRUD / 静态页；**中**＝多表、树、缓存、异步或专门页面；**高**＝跨端协议、调度器、生成器或需要独立子系统的能力。

## 0. 调研对象与取证

| 项 | 值 | 来源 |
| --- | --- | --- |
| 前端 | RuoYi-Vue3 `v3.9.2`（Vue 3.5.26 + Element Plus 2.13.1 + Pinia 3.0.4 + Vue Router 4.6.4 + Vite 6.4.1） | [package.json](https://github.com/yangzongzhuan/RuoYi-Vue3/blob/master/package.json) |
| 后端 | RuoYi-Vue `v3.9.2`（Spring Boot 4.1.0、Java 17、MyBatis、Quartz、Druid） | [pom.xml](https://github.com/yangzongzhuan/RuoYi-Vue/blob/master/pom.xml) |
| 菜单种子 | `sql/ry_20260417.sql`：85 条 `sys_menu`（5 目录 / 19 菜单 / 61 按钮） | [sql/ry_20260417.sql](https://github.com/yangzongzhuan/RuoYi-Vue/blob/master/sql/ry_20260417.sql) |
| 在线演示 | <https://vue.ruoyi.vip>，`admin / admin123`，算术图形验证码 | 本次实测 |
| 官方文档 | <http://doc.ruoyi.vip/ruoyi-vue/>（后台手册、前端手册） | 本次抓取 |

实测说明：演示站与仓库同版本；演示库额外挂了一条「AI对话」菜单，仓库种子数据中**没有**这一项，下文按仓库种子为准并单独标注。

## 1. 菜单结构与模块清单

### 1.1 顶层结构

侧边栏为「目录 → 菜单 → 按钮」三级；仅目录/菜单进侧栏，按钮（F 类型）只作权限点。

| 顶层目录 | 菜单 | 路由 | 组件 | 列表权限标识 |
| --- | --- | --- | --- | --- |
| 系统管理 | 用户管理 | `/system/user` | `system/user/index` | `system:user:list` |
| | 角色管理 | `/system/role` | `system/role/index` | `system:role:list` |
| | 菜单管理 | `/system/menu` | `system/menu/index` | `system:menu:list` |
| | 部门管理 | `/system/dept` | `system/dept/index` | `system:dept:list` |
| | 岗位管理 | `/system/post` | `system/post/index` | `system:post:list` |
| | 字典管理 | `/system/dict` | `system/dict/index` | `system:dict:list` |
| | 参数设置 | `/system/config` | `system/config/index` | `system:config:list` |
| | 通知公告 | `/system/notice` | `system/notice/index` | `system:notice:list` |
| | 日志管理（目录） | `/system/log` | — | — |
| | ↳ 操作日志 | `/monitor/operlog` | `monitor/operlog/index` | `monitor:operlog:list` |
| | ↳ 登录日志 | `/monitor/logininfor` | `monitor/logininfor/index` | `monitor:logininfor:list` |
| 系统监控 | 在线用户 | `/monitor/online` | `monitor/online/index` | `monitor:online:list` |
| | 定时任务 | `/monitor/job` | `monitor/job/index` | `monitor:job:list` |
| | 数据监控 | `/monitor/druid` | `monitor/druid/index` | `monitor:druid:list` |
| | 服务监控 | `/monitor/server` | `monitor/server/index` | `monitor:server:list` |
| | 缓存监控 | `/monitor/cache` | `monitor/cache/index` | `monitor:cache:list` |
| | 缓存列表 | `/monitor/cacheList` | `monitor/cache/list` | `monitor:cache:list` |
| 系统工具 | 表单构建 | `/tool/build` | `tool/build/index` | `tool:build:list` |
| | 代码生成 | `/tool/gen` | `tool/gen/index` | `tool:gen:list` |
| | 系统接口 | `/tool/swagger` | `tool/swagger/index` | `tool:swagger:list` |
| 其他 | 若依官网 | 外链 `http://ruoyi.vip` | — | — |

来源：SQL 种子 `sys_menu` 插入段（id 1–4、100–117、500–501、1000–1060）；[RuoYi-Vue3 views 目录](https://github.com/yangzongzhuan/RuoYi-Vue3/tree/master/src/views)。

### 1.2 系统管理

#### 用户管理（`system/user/index.vue`）
- **页面**：左侧「组织机构」部门树（可搜索、可刷新、可拖宽、默认全展开）＋右侧列表。查询：用户名称、手机号码、状态、创建时间区间。
- **核心动作**：新增、修改、删除（批量）、导入（Excel 模板下载 + 是否更新已存在数据）、导出；行内：修改、删除、重置密码、分配角色；状态用 `el-switch` 即时切换。
- **表单**：用户昵称、归属部门（树选择）、手机号码、用户名称、用户邮箱、用户性别、状态、岗位（多选）、角色（多选）、备注。
- **复杂度**：高。涉及部门树、角色/岗位多选、导入导出、密码重置、状态联动、个人信息页复用。

#### 角色管理（`system/role/index.vue` / `authUser.vue`）
- **页面**：查询：角色名称、权限字符、状态、创建时间；列表含状态开关。
- **核心动作**：新增、修改、删除（批量）、导出；行内：修改、删除、数据权限、分配用户。
- **表单**：角色名称、权限字符、角色顺序、状态、菜单权限树（展开/折叠、全选/全不选、父子联动）、备注。
- **数据权限弹窗**：权限范围下拉（5 档）＋部门树（可勾选，仅「自定数据权限」时显示树）。
- **分配用户页**：已分配用户列表（查询、单条取消授权、批量取消授权、添加用户）。
- **复杂度**：高。菜单树 + 部门树两套勾选语义，且直接决定服务端 SQL。

#### 菜单管理（`system/menu/index.vue`）
- **页面**：树表（`el-table` row-key 展开）；查询：菜单名称、状态。
- **核心动作**：新增、修改、删除；无导出。
- **表单**：上级菜单、菜单类型（目录 M / 菜单 C / 按钮 F，切换时字段动态显隐）、菜单图标（图标选择器）、显示排序、路由地址、组件路径、权限字符、路由参数、是否外链、是否缓存、显示状态、菜单状态。
- **复杂度**：中高。M/C/F 三态与路由生成规则耦合，改错直接影响侧栏与权限。

#### 部门管理（`system/dept/index.vue`）
- **页面**：树表；查询：部门名称、状态。
- **核心动作**：新增（支持在某行下新增子部门）、修改、删除；无导出。
- **表单**：上级部门（树选择）、部门名称、显示排序、负责人、联系电话、邮箱、部门状态。
- **数据**：`sys_dept.ancestors` 存祖级路径（如 `0,100,101`），删除前服务层有子部门 / 有用户校验。
- **复杂度**：中。树维护 + 数据权限边界。

#### 岗位管理（`system/post/index.vue`）
- **页面**：普通列表；查询：岗位编码、岗位名称、状态。
- **核心动作**：新增、修改、删除（批量）、导出。
- **表单**：岗位编码、岗位名称、岗位顺序、状态、备注。
- **复杂度**：低。纯字典式 CRUD，不承载权限。

#### 字典管理（`system/dict/index.vue` / `data.vue`）
- **页面**：两级。字典类型列表 →「字典数据」子页（`/system/dict-data/index/:dictId`）；数据页含返回按钮。
- **核心动作**：类型：新增、修改、删除、导出、刷新缓存；数据：新增、修改、删除、导出。
- **表单**：类型（字典名称、字典类型、状态、备注）；数据（数据标签、键值、排序、样式属性、回显样式、默认、状态、备注）。
- **渲染**：`DictTag` 组件按字典数据把值渲染成 `el-tag`（支持 `elTagType` 五色）。
- **复杂度**：中。两表 + 前端字典缓存 + 标签渲染。

#### 参数设置（`system/config/index.vue`）
- **页面**：列表；查询：参数名称、参数键名、系统内置、创建时间。
- **核心动作**：新增、修改、删除（批量）、导出、刷新缓存；内置参数有保护语义。
- **表单**：参数名称、参数键名、参数键值、系统内置、备注。
- **复杂度**：低中。CRUD + 缓存刷新 + 内置项保护。

#### 通知公告（`system/notice/index.vue`）
- **页面**：列表；查询：公告标题、操作人员、类型；行内「阅读用户」。
- **核心动作**：新增、修改、删除（批量）；内容用富文本编辑器（Quill）；类型（通知 / 公告）、状态（正常 / 关闭）。
- **阅读用户**：弹窗列出已读用户；`sys_notice_read` 对 `(user_id, notice_id)` 唯一。
- **顶栏联动**：`HeaderNotice` 铃铛 + 未读角标；挂载时拉取一次顶部公告，悬停弹出列表，点击即标记已读，支持全部已读，无轮询。
- **复杂度**：中。富文本 + 已读记录 + 顶栏组件。

#### 操作日志（`monitor/operlog/index.vue`）
- **页面**：查询：操作地址、系统模块、操作人员、类型、状态、时间区间；支持列排序（操作人员/时间/耗时）。
- **核心动作**：查看详情（请求参数、返回结果、异常信息）、删除（批量）、清空、导出。
- **写入**：服务端 `@Log(title, businessType)` 注解 + AOP 切面，记录模块、方法、请求方式、操作人、部门、URL、IP、地点、参数、结果、状态、耗时。
- **复杂度**：中。写入侧是框架能力，查询侧是标准列表 + 详情。

#### 登录日志（`monitor/logininfor/index.vue`）
- **页面**：查询：登录地址、用户名称、状态、时间区间。
- **核心动作**：删除（批量）、清空、解锁（清除登录失败锁定）、导出。
- **复杂度**：低中。登录事件写入 + 账户解锁。

### 1.3 系统监控

| 页面 | 页面形态与核心动作 | 复杂度 |
| --- | --- | --- |
| 在线用户 | 列表：会话编号、登录名称、所属部门、主机、登录地点、操作系统、浏览器、登录时间；单条「强退」（删 Redis 会话 token） | 中 |
| 定时任务 | 列表：任务名称、任务组、调用目标字符串、cron、状态；新增/修改/删除/导出、状态开关、执行一次、调度日志子页；表单含 cron 表达式 + 图形化 cron 生成器（秒/分/时/日/月/周/年）、执行策略（立即执行/执行一次/放弃执行）、是否并发 | 高 |
| 数据监控 | Druid 控制台以 iframe 嵌入：数据源、SQL 监控、SQL 防火墙、URI 监控、Session 监控 | 低（集成）/中（若自研面板） |
| 服务监控 | 卡片式只读：CPU（核心数/用户使用率/系统使用率/空闲率）、内存、JVM（总/已用/剩余/使用率）、服务器信息、Java 虚拟机信息、磁盘状态 | 中 |
| 缓存监控 | Redis 基本信息（版本/模式/端口/客户端/运行时间/内存/CPU/AOF/RDB/Key 数）、命令统计（ECharts 柱图）、内存信息（ECharts 饼图） | 中 |
| 缓存列表 | 左「缓存名称」列表 → 右「缓存键名」列表 → 查看缓存内容；支持刷新与清理 | 中 |

来源：[monitor/online](https://github.com/yangzongzhuan/RuoYi-Vue3/blob/master/src/views/monitor/online/index.vue)、[monitor/job](https://github.com/yangzongzhuan/RuoYi-Vue3/blob/master/src/views/monitor/job/index.vue)、[monitor/server](https://github.com/yangzongzhuan/RuoYi-Vue3/blob/master/src/views/monitor/server/index.vue)、[monitor/cache](https://github.com/yangzongzhuan/RuoYi-Vue3/blob/master/src/views/monitor/cache/index.vue)、[monitor/cache/list](https://github.com/yangzongzhuan/RuoYi-Vue3/blob/master/src/views/monitor/cache/list.vue)。

### 1.4 系统工具

| 页面 | 页面形态与核心动作 | 复杂度 |
| --- | --- | --- |
| 表单构建 | 三栏拖拽器：左「输入型/选择型/布局型」组件面板，中画布，右属性面板；生成 Vue 单文件，分「页面 / 弹窗」两种形态，支持预览、复制、下载、清空。组件含单行/多行/密码/计数器、下拉/级联/单选/多选/开关/滑块/时间/日期/评分/颜色/上传 | 高 |
| 代码生成 | 表列表（表名/描述/实体/创建时间/更新时间）；生成、导入表、创建表、修改生成配置、预览（按文件名分 Tab）、同步、下载、删除；字段级配置（Java 类型、插入/编辑/列表/查询、查询方式、必填、显示类型、字典类型） | 高 |
| 系统接口 | Swagger/OpenAPI 页面 iframe 嵌入 | 低 |

来源：[tool/build](https://github.com/yangzongzhuan/RuoYi-Vue3/blob/master/src/views/tool/build/index.vue)、[tool/gen](https://github.com/yangzongzhuan/RuoYi-Vue3/blob/master/src/views/tool/gen/index.vue)、[tool/swagger](https://github.com/yangzongzhuan/RuoYi-Vue3/blob/master/src/views/tool/swagger/index.vue)、[generator/config.js](https://github.com/yangzongzhuan/RuoYi-Vue3/blob/master/src/utils/generator/config.js)。

### 1.5 无侧栏菜单的页面

| 页面 | 路由 | 说明 | 复杂度 |
| --- | --- | --- | --- |
| 首页 | `/index` | **静态介绍页**：框架简介、技术选型、版本号、更新日志、联系方式、捐赠；不是指标看板 | 低 |
| 个人中心 | `/user/profile/:activeTab?` | 头像（裁剪上传）+ Tab：基本资料 / 修改密码 | 低中 |
| 登录 | `/login` | 账号、密码、图形验证码（算术题）、记住密码、版权脚注 | 低 |
| 注册 | `/register` | 可选的公开注册页（账号/昵称/密码/确认/验证码） | 低 |
| 锁屏 | `/lock` | 顶栏菜单手动锁定；状态存 localStorage，输入当前账号密码解锁 | 低 |
| 401 / 404 | `/401`、`/:pathMatch(.*)*` | 错误页 | 低 |
| 重定向 | `/redirect/:path(.*)` | 标签页「刷新」用的中转路由 | 低 |

来源：[router/index.js](https://github.com/yangzongzhuan/RuoYi-Vue3/blob/master/src/router/index.js)、[views/index.vue](https://github.com/yangzongzhuan/RuoYi-Vue3/blob/master/src/views/index.vue)、[views/login.vue](https://github.com/yangzongzhuan/RuoYi-Vue3/blob/master/src/views/login.vue)。

### 1.6 模块复杂度总表

| 模块 | 页面数 | 粗评 | 主要重量来自 |
| --- | --- | --- | --- |
| 系统管理 | 9（含 2 个日志子页） | 高 | 用户/角色/菜单/部门四者互相引用 |
| 系统监控 | 6 | 高 | Quartz 调度、Redis 与会话、服务指标 |
| 系统工具 | 3 | 高 | 拖拽搭建器、代码生成器 |
| 其他 | 7 | 低-中 | 登录/锁屏/个人中心/静态首页 |

## 2. 权限模型

### 2.1 三层授权链

```
用户 sys_user ──< sys_user_role >── 角色 sys_role ──< sys_role_menu >── 菜单 sys_menu
                                     │
                                     └──< sys_role_dept >── 部门 sys_dept（仅「自定数据权限」）
用户 sys_user ──< sys_user_post >── 岗位 sys_post（仅标识职务，不参与授权）
```

- 一个用户可有多角色；权限集合为各角色并集。
- 用户**没有**直接的菜单/按钮授权，必须经角色。
- 角色有 `role_key`（权限字符，如 `admin`、`common`）与 `data_scope`（数据范围）。
- 超级管理员是硬编码特例：`user_id == 1` 或 `role_key == admin`，拥有 `*:*:*` 全部权限且数据权限不过滤。

来源：[sys_user_role / sys_role_menu / sys_role_dept / sys_user_post DDL](https://github.com/yangzongzhuan/RuoYi-Vue/blob/master/sql/ry_20260417.sql)、[SysPermissionService](https://github.com/yangzongzhuan/RuoYi-Vue/blob/master/ruoyi-framework/src/main/java/com/ruoyi/framework/web/service/SysPermissionService.java)。

### 2.2 菜单类型与权限标识

`sys_menu.menu_type` 三值：

| 类型 | 含义 | 是否进侧栏/路由 | 典型字段 |
| --- | --- | --- | --- |
| `M` | 目录 | 是（作为可展开分组） | path、icon、order_num |
| `C` | 菜单 | 是（真实页面） | path、component、perms、is_cache |
| `F` | 按钮 | 否 | 仅 perms |

权限标识统一为 `模块:业务:动作`，例如 `system:user:add`、`monitor:job:changeStatus`、`tool:gen:code`。种子数据共 **61 个按钮权限点**，覆盖导出、导入、重置密码、强退、解锁、清空、状态修改等。

菜单表还带：`visible`（显示/隐藏）、`status`（正常/停用）、`is_frame`（是否外链）、`is_cache`（是否 keep-alive）、`query`（路由默认参数）、`route_name`。

### 2.3 前端权限执行

| 机制 | 行为 | 来源 |
| --- | --- | --- |
| 动态路由 | 登录后 `getInfo()` 取 `roles/permissions`，`getRouters()` 取后端菜单树，转换为路由并 `router.addRoute`；无权限的菜单根本不注册 | [permission.js](https://github.com/yangzongzhuan/RuoYi-Vue3/blob/master/src/permission.js)、[store/modules/permission.js](https://github.com/yangzongzhuan/RuoYi-Vue3/blob/master/src/store/modules/permission.js) |
| `v-hasPermi="['a:b:c']"` | 无权限时**把 DOM 移除**（不是隐藏） | [directive/permission/hasPermi.js](https://github.com/yangzongzhuan/RuoYi-Vue3/blob/master/src/directive/permission/hasPermi.js) |
| `v-hasRole="['admin']"` | 同上，按角色判断；`admin` 恒通过 | [directive/permission/hasRole.js](https://github.com/yangzongzhuan/RuoYi-Vue3/blob/master/src/directive/permission/hasRole.js) |
| `checkPermi` / `checkRole` | 供 `v-if` 等无法用指令的场景 | [plugins/auth.js](https://github.com/yangzongzhuan/RuoYi-Vue3/blob/master/src/plugins/auth.js) |
| 路由级 `permissions` / `roles` | `dynamicRoutes`（如分配角色、字典数据、调度日志、修改生成配置）按权限过滤后再注册 | [router/index.js](https://github.com/yangzongzhuan/RuoYi-Vue3/blob/master/src/router/index.js) |
| keep-alive | 标签页缓存依赖 `meta.noCache` 与组件 `name` 一致 | [AppMain.vue](https://github.com/yangzongzhuan/RuoYi-Vue3/blob/master/src/layout/components/AppMain.vue) |

### 2.4 后端权限执行

- 接口级：`@PreAuthorize("@ss.hasPermi('system:user:list')")`；`PermissionService` 提供 `hasPermi`、`lacksPermi`、`hasAnyPermi`、`hasRole`、`lacksRole`、`hasAnyRoles`。
- 公开接口：`@Anonymous`。
- 权限来源：登录时把角色与权限集装入 `LoginUser`；`*:*:*` 表示全权限。
- 后端始终是最终裁决；前端指令只做体验层裁剪。

来源：[PermissionService](https://github.com/yangzongzhuan/RuoYi-Vue/blob/master/ruoyi-framework/src/main/java/com/ruoyi/framework/web/service/PermissionService.java)、[后台手册·权限注解](http://doc.ruoyi.vip/ruoyi-vue/document/htsc.html#权限注解)。

### 2.5 数据权限（数据范围）

`sys_role.data_scope` 五档（演示站实测下拉一致，第二档文案为「自定数据权限」）：

| 值 | 含义 | 过滤规则（部门别名/用户别名） |
| --- | --- | --- |
| `1` | 全部数据权限 | 不加条件 |
| `2` | 自定数据权限 | `dept_id IN (SELECT dept_id FROM sys_role_dept WHERE role_id = ?)` |
| `3` | 本部门数据权限 | `dept_id = 当前用户 dept_id` |
| `4` | 本部门及以下数据权限 | `dept_id IN (SELECT dept_id FROM sys_dept WHERE dept_id = ? OR find_in_set(?, ancestors))` |
| `5` | 仅本人数据权限 | `user_id = 当前用户`；未配 userAlias 时退化为 `dept_id = 0`（查不到） |

实现方式：
- 注解 `@DataScope(userAlias, deptAlias, userField, deptField, permission)` 标在 Service 方法上；
- `DataScopeAspect` 在方法执行前把拼好的 SQL 片段写入入参实体的 `params.dataScope`；
- Mapper XML 里以 `${params.dataScope}` 拼到 `where` 后；
- 多角色按 `OR` 合并；角色与接口权限字符不匹配则跳过；全部不匹配时返回空集（`dept_id = 0`）；
- 超级管理员完全不过滤。

`permission()` 字段让「数据权限」也能按权限字符收敛：只有角色拥有该接口权限时才把它的数据范围并入。

来源：[Constants.Dept](https://github.com/yangzongzhuan/RuoYi-Vue/blob/master/ruoyi-common/src/main/java/com/ruoyi/common/constant/Constants.java)、[DataScopeAspect](https://github.com/yangzongzhuan/RuoYi-Vue/blob/master/ruoyi-framework/src/main/java/com/ruoyi/framework/aspectj/DataScopeAspect.java)、[DataScope 注解](https://github.com/yangzongzhuan/RuoYi-Vue/blob/master/ruoyi-common/src/main/java/com/ruoyi/common/annotation/DataScope.java)。

### 2.6 权限相关的界面动作

| 界面 | 动作 |
| --- | --- |
| 角色管理 → 菜单权限 | 树勾选；「展开/折叠」「全选/全不选」「父子联动」三个开关；父子联动状态存 `menu_check_strictly` |
| 角色管理 → 数据权限 | 权限范围下拉＋部门树勾选；父子联动状态存 `dept_check_strictly`；`sys_role_dept` 落库 |
| 角色管理 → 分配用户 | 已授权用户列表；单条/批量取消授权；从用户列表添加 |
| 用户管理 → 分配角色 | 用户维度的角色多选页（`/system/user-auth/role/:userId`） |
| 用户管理 → 重置密码 | 弹窗输入新密码 |
| 用户管理 / 角色管理 | 状态 `el-switch` 即时启停 |

### 2.7 边界与特例

- `v-hasPermi` 是**删除节点**而非 `display:none`，因此不能用于需要保留布局占位的场景。
- 动态路由在刷新后需重新拉取；未注册的路径落 404。
- 数据权限只覆盖标注了 `@DataScope` 的查询；不是全局强制。
- 部门/菜单的「父子联动」是**授权时的勾选语义**，与数据权限的部门树是两套独立状态。
- 岗位不参与任何授权判断。

## 3. 组织模型

### 3.1 实体

| 实体 | 关键字段 | 语义 |
| --- | --- | --- |
| 部门 `sys_dept` | `dept_id`、`parent_id`、`ancestors`、`dept_name`、`order_num`、`leader`、`phone`、`email`、`status`、`del_flag` | 树；`ancestors` 保存祖级路径，支撑「本部门及以下」数据权限 |
| 用户 `sys_user` | `user_id`、`dept_id`、`user_name`、`nick_name`、`user_type`、`email`、`phonenumber`、`sex`、`avatar`、`password`、`status`、`login_ip`、`login_date`、`pwd_update_date` | 单部门归属；账号状态与删除标志分离 |
| 岗位 `sys_post` | `post_id`、`post_code`、`post_name`、`post_sort`、`status` | 职务字典，不参与权限 |
| 角色 `sys_role` | `role_id`、`role_name`、`role_key`、`role_sort`、`data_scope`、`menu_check_strictly`、`dept_check_strictly`、`status` | 授权与数据范围的载体 |

来源：[ry_20260417.sql](https://github.com/yangzongzhuan/RuoYi-Vue/blob/master/sql/ry_20260417.sql) 第 5–133 行。

### 3.2 关系

| 关系 | 表 | 基数 |
| --- | --- | --- |
| 用户 ↔ 角色 | `sys_user_role(user_id, role_id)` | 多对多 |
| 用户 ↔ 岗位 | `sys_user_post(user_id, post_id)` | 多对多 |
| 角色 ↔ 菜单 | `sys_role_menu(role_id, menu_id)` | 多对多（含按钮） |
| 角色 ↔ 部门 | `sys_role_dept(role_id, dept_id)` | 多对多（仅自定数据权限） |
| 部门 ↔ 用户 | `sys_user.dept_id` | 一对多（用户单部门） |
| 部门 ↔ 部门 | `sys_dept.parent_id` + `ancestors` | 树 |

### 3.3 语义要点

- **部门 = 组织边界 + 数据权限边界**：用户归属一个部门；部门树同时服务「用户筛选」与「数据范围」。
- **岗位 = 职务标签**：`post_code/post_name` 用于展示和筛选，不产生权限；种子数据给了 ceo/项目经理/人力资源/普通员工。
- **角色 = 权限包**：可跨部门授予；同一用户多角色时权限并集、数据范围并集（OR）。
- **用户管理页**把这三者拼在一起：左树按部门过滤、表格显示部门、表单里选部门/岗位/角色。
- 部门删除有保护：服务层提供 `hasChildByDeptId` 与 `checkDeptExistUser` 校验。

## 4. UI 范式

### 4.1 布局

- 经典「左侧栏 + 顶栏 + 多标签 + 内容区」。可配置项见 [settings.js](https://github.com/yangzongzhuan/RuoYi-Vue3/blob/master/src/settings.js)：
  - `navType`：1 纯左侧 / 2 混合（左侧 + 顶部）/ 3 纯顶部；
  - `sideTheme`：深色 / 浅色侧栏；
  - `tagsView`、`tagsViewPersist`、`tagsIcon`、`tagsViewStyle`（card / chrome）；
  - `fixedHeader`、`sidebarLogo`、`dynamicTitle`、`footerVisible`、`footerContent`。
- 侧栏可折叠；窄屏（<992px）自动切换移动态抽屉。
- 深色模式与主题色可在顶栏/设置抽屉切换，主题色写入 CSS 变量。

### 4.2 多标签页（TagsView）

- 每个访问过的路由生成一个标签；`meta.affix` 固定（首页不可关闭）。
- 右键菜单 / 右侧下拉：刷新页面、关闭当前、关闭其他、关闭左侧、关闭右侧、全部关闭、全屏。
- 标签可持久化（`tagsViewPersist`）；支持图标显示（`tagsIcon`）。
- 刷新通过 `/redirect` 中转，以重建组件；缓存由 keep-alive + `cachedViews` 维护。

来源：[TagsView/index.vue](https://github.com/yangzongzhuan/RuoYi-Vue3/blob/master/src/layout/components/TagsView/index.vue)、[store/modules/tagsView.js](https://github.com/yangzongzhuan/RuoYi-Vue3/blob/master/src/store/modules/tagsView.js)。

### 4.3 面包屑与标题

- 面包屑由当前 `matched` 路由生成，首页为根；`meta.breadcrumb=false` 可隐藏；`redirect: 'noRedirect'` 时不可点击。
- 页面标题随路由 `meta.title` 变化（`dynamicTitle` 控制是否加系统名）。

### 4.4 顶栏工具簇

从左到右：折叠按钮、面包屑/顶部菜单、**菜单搜索**（fuse.js 模糊搜索，跳转）、源码链接、文档链接、全屏、暗色模式、**布局大小**（全局 Element Plus size）、**消息通知**（铃铛 + 未读角标 + 悬停弹层）、头像下拉（个人中心 / 布局设置 / 锁定屏幕 / 退出登录）。

来源：[Navbar.vue](https://github.com/yangzongzhuan/RuoYi-Vue3/blob/master/src/layout/components/Navbar.vue)、[HeaderSearch](https://github.com/yangzongzhuan/RuoYi-Vue3/blob/master/src/components/HeaderSearch/index.vue)、[HeaderNotice](https://github.com/yangzongzhuan/RuoYi-Vue3/blob/master/src/layout/components/HeaderNotice/index.vue)。

### 4.5 列表页范式（全站统一，最值得参照）

标准结构，自上而下：

1. **查询区**：`el-form inline`，字段 2–4 个，`搜索` / `重置`；回车触发；可整体显隐。
2. **动作行**：`新增`（primary）、`修改`（success，选中 1 条可用）、`删除`（danger，选中 ≥1 可用）、`导入`/`导出`（可选）；右侧 `RightToolbar`（搜索显隐、刷新、列设置）。
3. **表格**：`el-table` + 多选列 + 状态 `el-switch` + 行内操作（文字/图标按钮；角色页把「数据权限/分配用户」收进「更多」下拉）。
4. **分页**：`Pagination` 组件（总条数、每页条数、上下页、跳页）。
5. **弹窗表单**：`el-dialog`（600–680px，`append-to-body`），`el-form` + 校验规则，取消/确定。
6. **删除确认**、成功/失败 `ElMessage`、加载 `v-loading`。

变体：
- **左树 + 右列表**：用户管理（部门树）。
- **树表**：菜单、部门（展开行，无分页）。
- **主从两级**：字典（类型 → 数据）、缓存（名称 → 键名）。
- **主子页**：定时任务 → 调度日志；角色 → 分配用户。

### 4.6 表单与树交互

- 部门选择用 `el-tree-select`（`check-strictly` 可按需设置）。
- 角色/岗位用 `el-select multiple`。
- 菜单/部门/权限勾选用 `el-tree` + 「展开/折叠、全选/全不选、父子联动」三开关，是全站授权交互的固定套路。
- 状态统一用 `el-switch`（`active-value="0"` / `inactive-value="1"`）。
- 字典值统一用 `DictTag` 渲染成彩色 `el-tag`。
- 富文本用 `@vueup/vue-quill` 封装的 `Editor`；上传用 `FileUpload`/`ImageUpload`；头像裁剪用 `vue-cropper`。
- 外链/第三方控制台用 `iFrame` 组件包裹。

### 4.7 主题与密度

- Element Plus 2.13.1；主色、暗色模式通过 CSS 变量切换，顶栏与设置抽屉都可改。
- 全局尺寸（default / small / large）由顶栏 `SizeSelect` 控制，影响表格与表单密度。
- 表格默认无斑马纹、带表头底色、`show-overflow-tooltip` 处理长文本；行内操作收紧为 `small-padding`。
- 内容区统一 `.app-container` 内边距；页面由 `el-row/el-col` 做响应式分栏。
- 页脚版权可选。

### 4.8 Element Plus 组件映射

| 场景 | 组件 |
| --- | --- |
| 侧栏/顶部菜单 | `el-menu` / `el-sub-menu` / `el-menu-item` |
| 面包屑 | `el-breadcrumb` |
| 标签页 | 自研 `TagsView`（`router-link` + `ScrollPane`），非 `el-tabs` |
| 列表 | `el-table` + `el-table-column` + `el-pagination` |
| 查询/表单 | `el-form` / `el-form-item` / `el-input` / `el-select` / `el-tree-select` / `el-date-picker` / `el-radio-group` / `el-checkbox` |
| 弹窗/抽屉 | `el-dialog` / `el-drawer` |
| 树 | `el-tree`（授权）/ `el-tree-select`（归属部门） |
| 反馈 | `ElMessage` / `ElMessageBox` / `ElNotification` / `el-tooltip` / `el-popover` / `el-badge` |
| 状态/标签 | `el-switch` / `el-tag`（经 `DictTag` 封装） |
| 上传 | `el-upload`（封装 `FileUpload` / `ImageUpload`） |
| 图表 | `echarts`（仅缓存监控使用） |

### 4.9 非典型页面

- **首页不是看板**：没有统计卡片、待办、图表；是一页框架自述 + 更新日志 + 捐赠。若参照它，等于默认「首页可以只是欢迎页」。
- **登录页**：背景图 + 居中卡片；账号/密码/算术验证码/记住密码；底部版权。
- **锁屏页**：独立全屏页，输入当前密码解锁。
- **个人中心**：左头像卡 + 右 Tab（基本资料 / 修改密码）。

## 5. 与审批域天然相邻的能力

### 5.1 通知公告（公告/通知）

| 维度 | 事实 |
| --- | --- |
| 数据 | `sys_notice`：标题、类型（1 通知 / 2 公告）、富文本内容、状态；`sys_notice_read`：`(user_id, notice_id)` 唯一 + 阅读时间 |
| 发送面 | 管理端 CRUD；无「按部门/角色定向发布」字段，发布即全员可见 |
| 展示面 | 顶栏铃铛：未读数、顶部列表、点击已读、全部已读、详情弹层；**挂载时拉一次，无轮询/推送** |
| 与本项目审批的关系提示 | 若依的公告是「广播 + 已读回执」，与「待办即通知」不是同一模型；它没有收件人表，也没有已读范围筛选 |
| 复杂度 | 中（富文本 + 已读记录 + 顶栏组件） |

来源：[notice/index.vue](https://github.com/yangzongzhuan/RuoYi-Vue3/blob/master/src/views/system/notice/index.vue)、[ReadUsers.vue](https://github.com/yangzongzhuan/RuoYi-Vue3/blob/master/src/views/system/notice/ReadUsers.vue)、[notice API](https://github.com/yangzongzhuan/RuoYi-Vue3/blob/master/src/api/system/notice.js)。

### 5.2 审计：操作日志与登录日志

| 维度 | 操作日志 | 登录日志 |
| --- | --- | --- |
| 写入 | `@Log(title, businessType)` + AOP，自动抓请求参数/返回/异常 | 登录成功/失败事件写库 |
| 查询 | 模块、操作人、类型、状态、IP、时间 | 用户、IP、状态、时间 |
| 动作 | 详情、删除、清空、导出 | 删除、清空、解锁、导出 |
| 字段 | 模块、业务类型、方法、请求方式、操作人、部门、URL、IP、地点、参数、结果、状态、错误、耗时 | 访问编号、用户、IP、地点、浏览器、OS、状态、描述、时间 |
| 复杂度 | 中 | 低中 |

来源：[operlog/index.vue](https://github.com/yangzongzhuan/RuoYi-Vue3/blob/master/src/views/monitor/operlog/index.vue)、[logininfor/index.vue](https://github.com/yangzongzhuan/RuoYi-Vue3/blob/master/src/views/monitor/logininfor/index.vue)、`@Log` AOP（`ruoyi-framework/.../aspectj/LogAspect.java`）。

### 5.3 在线用户

- 数据源是 Redis 中的登录 token 会话，不是业务表。
- 列表含会话编号、登录名、部门、主机、登录地点、操作系统、浏览器、登录时间。
- 动作：单条强退（删除 token，用户下次请求即失效）。
- 复杂度：中（要维护会话登记语义，与停用/改密/改权限的失效联动需另行设计）。

来源：[online/index.vue](https://github.com/yangzongzhuan/RuoYi-Vue3/blob/master/src/views/monitor/online/index.vue)。

### 5.4 字典

- 两级：字典类型（`dict_type` 唯一编码）→ 字典数据（label/value/sort/样式）。
- 全局缓存到 Redis，管理端可「刷新缓存」。
- 前端以 `DictTag` 统一渲染，业务表只存 `value`。
- 与本项目的提示：若依把「枚举展示」做成了运行时可编辑的数据，而不是代码常量；这与「字典进 DB 还是留代码」是一个需要蓝图裁定的分叉。
- 复杂度：中。

### 5.5 参数设置

- `sys_config`：参数名称、键名、键值、系统内置、备注。
- 内置参数在 UI 上受保护；修改后需「刷新缓存」。
- 复杂度：低中。

### 5.6 定时任务

- Quartz 驱动，在线管理任务：任务名、任务组、调用目标字符串（Bean 方法）、cron、状态、执行策略、是否并发。
- 图形化 cron 生成器（秒/分/时/日/月/周/年）降低 cron 门槛。
- 调度日志独立子页：任务名、组、调用目标、日志信息、状态、异常信息、时间。
- 复杂度：高（调度器 + 执行策略 + 并发控制 + 日志）。

### 5.7 监控

- **服务监控**：CPU / 内存 / JVM / 服务器信息 / 磁盘，只读卡片。
- **缓存监控**：Redis 基本信息、命令统计（ECharts）、内存信息（ECharts）。
- **缓存列表**：按 cacheName → keyName 浏览与清理。
- **数据监控**：Druid 控制台 iframe（数据源、SQL、防火墙、URI、Session）。
- 复杂度：中（除 Druid 外都需自建采集/展示）。

### 5.8 工具

- **代码生成**：读表元数据 → 配置字段 → 预览/下载；生成 `domain/mapper/service/controller/xml/sql/vue` 等；支持「同步」表结构。
- **表单构建**：拖拽生成 Vue/HTML 表单代码；Palette 覆盖常见输入/选择/布局组件。
- **系统接口**：Swagger UI iframe。
- 复杂度：高（生成器）/ 高（搭建器）/ 低（iframe）。

### 5.9 未在 UI 暴露的框架能力

| 能力 | 事实 | 来源 |
| --- | --- | --- |
| 多数据源 | 后端有 `@DataSource` 注解 + `DataSourceAspect` + `DynamicDataSource`，**前端无管理页** | `ruoyi-common/.../annotation/DataSource.java`、`ruoyi-framework/.../aspectj/DataSourceAspect.java` |
| 国际化 | 前端**无 vue-i18n 依赖**、无语言包（仅一个 `language.svg` 图标）；后端有错误消息资源 | [package.json](https://github.com/yangzongzhuan/RuoYi-Vue3/blob/master/package.json)、仓库文件树 |
| 防重复提交 | `@RepeatSubmit` 注解 + 拦截器 | `ruoyi-common/.../annotation/RepeatSubmit.java` |
| 限流 | `@RateLimiter` 注解 + AOP | `ruoyi-common/.../annotation/RateLimiter.java` |
| XSS | `XssFilter` 全局过滤器 | `ruoyi-common/.../filter/XssFilter.java` |

## 6. 结论要点（给蓝图用的 10 条事实）

1. 若依分离版是「**权限底座 + 运维面板 + 开发工具**」三合一，业务域几乎为零；泛 OA 需要的组织/审计/公告/字典它都有，但都停在平台层。
2. 菜单不是写死的路由：**后端菜单表 → 动态路由**，页面增删是数据操作而非发版；代价是路由/组件名必须能被前端 `import.meta.glob` 解析。
3. 权限是「**角色 → 菜单（含按钮）**」一层，按钮权限点 61 个；没有部门/岗位维度的接口权限。
4. 数据权限是**注解 + SQL 片段注入**，只覆盖显式标注的查询，不是全局行级安全。
5. 组织模型是「**单部门 + 多角色 + 多岗位**」；部门同时是数据边界，岗位纯标签。
6. UI 的复用单元是「**查询表单 + 动作行 + 表格 + 分页 + 弹窗表单**」，全站同构，学习成本极低。
7. 多标签页 + keep-alive + 面包屑是「桌面后台」体感的核心，若依做得很完整；标签可持久化、可全屏、可批量关闭。
8. 首页是静态介绍页而非数据看板——若依并未提供「工作台」范式。
9. 公告是「广播 + 已读回执」，无定向发布；审计是「注解采集 + 列表查询」；在线用户是「Redis 会话 + 强退」。
10. 工具链（代码生成/表单构建/Swagger/Druid）是脚手架红利，不是 OA 业务能力；是否纳入需蓝图单独裁定。

## 7. 来源清单

- 前端仓库：<https://github.com/yangzongzhuan/RuoYi-Vue3>（master，v3.9.2）
- 后端仓库：<https://github.com/yangzongzhuan/RuoYi-Vue>（master，v3.9.2）
- 菜单/权限/组织 DDL 与种子：<https://github.com/yangzongzhuan/RuoYi-Vue/blob/master/sql/ry_20260417.sql>
- 内置功能 18 条：<https://github.com/yangzongzhuan/RuoYi-Vue3#内置功能>
- 路由与动态路由：<https://github.com/yangzongzhuan/RuoYi-Vue3/blob/master/src/router/index.js>
- 路由守卫：<https://github.com/yangzongzhuan/RuoYi-Vue3/blob/master/src/permission.js>
- 动态路由生成：<https://github.com/yangzongzhuan/RuoYi-Vue3/blob/master/src/store/modules/permission.js>
- 布局配置：<https://github.com/yangzongzhuan/RuoYi-Vue3/blob/master/src/settings.js>
- 按钮权限指令：<https://github.com/yangzongzhuan/RuoYi-Vue3/blob/master/src/directive/permission/hasPermi.js>
- 数据权限切面：<https://github.com/yangzongzhuan/RuoYi-Vue/blob/master/ruoyi-framework/src/main/java/com/ruoyi/framework/aspectj/DataScopeAspect.java>
- 数据范围常量：<https://github.com/yangzongzhuan/RuoYi-Vue/blob/master/ruoyi-common/src/main/java/com/ruoyi/common/constant/Constants.java>
- 菜单转路由：<https://github.com/yangzongzhuan/RuoYi-Vue/blob/master/ruoyi-system/src/main/java/com/ruoyi/system/service/impl/SysMenuServiceImpl.java>
- 后台手册：<http://doc.ruoyi.vip/ruoyi-vue/document/htsc.html>（权限注解 `#权限注解`、数据权限 `#数据权限`）
- 前端手册：<http://doc.ruoyi.vip/ruoyi-vue/document/qdsc.html>（路由使用 `#路由使用`、`#tab对象`、`#auth对象`）
- 在线演示：<https://vue.ruoyi.vip>（`admin / admin123`；实测菜单、用户管理、角色数据权限弹窗）
