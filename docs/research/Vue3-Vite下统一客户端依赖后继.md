# Vue 3 + Vite 下统一客户端依赖后继

> 研究票：[#25 Vue 3 + Vite 下统一客户端依赖的可用后继](https://github.com/lautung/guigu-oa/issues/25)  
> 地图票：[#22 升级到 Spring Boot 4 与 Vue 3 的依赖规格](https://github.com/lautung/guigu-oa/issues/22)  
> 来源：统一客户端 `前端代码/guigu-oa-client/package.json`；Vue / Vite / Element Plus / form-create / vue-router / Pinia / Vuex / axios / Vue Test Utils / Vitest 官方文档与 npm registry。  
> 说明：只记后继与兼容事实，**不选型、不定目标版本**。npm `dist-tag` 与「当前 latest」为 2026-08-19 观察值，规格票另钉。

---

## 0. 仓库里这些依赖怎么用

统一客户端现坐标（`前端代码/guigu-oa-client/package.json`）：Vue 2.6.10 + vue-cli 4.4.4 + Element UI 2.13.2。

| 能力 | 现用法 | 仓库出处 |
|------|--------|----------|
| 审批模板「表单设置」 | 全局 `Vue.use(FcDesigner)`，向导第二步 `<fc-designer ref="designer" />`，`setRule` / `setOption` 回填，`getRule` / `getOption` 写出 | `src/main.js`、`src/views/config/processTemplate/templateSet.vue` |
| 表单 JSON 落库 | `formProps` = `JSON.stringify(rule)`，`formOptions` = `JSON.stringify(option)` | `src/config/process-template.js` `applyFormDesign` |
| 办事发起 / 配置预览 | `<form-create :rule :option @submit>` 按库里的 JSON 动态画表 | `src/views/work/start/apply.vue`、`src/views/config/processTemplate/index.vue` |
| 办事提交 | `formValues: JSON.stringify({ formData, formShowData })`，`formShowData` 用 rule 的 `field`/`title` | `src/work/start-flow.js` |
| HTTP | axios 0.18.1 实例 + 拦截器；`baseURL` 读 `process.env.VUE_APP_BASE_API \|\| '/dev-api'` | `src/utils/request.js` |
| Token | `js-cookie` 读写 `guigu_oa_client_token` | `src/utils/auth.js` |
| 路由进度 | `nprogress` 在 `permission.js` | `src/permission.js` |
| 桌面表格 | Element UI `slot-scope` | 配置页若干 `el-table-column` |
| 开发代理 | vue-cli `devServer.proxy['/dev-api']` → `http://localhost:8800` | `vue.config.js` |
| 单元测试 | Jest + `@vue/cli-plugin-unit-jest` 4.4.4 + `@vue/test-utils` 1.0.0-beta.29 + `vue-jest` | `package.json`、`jest.config.js` |

领域约束（`CONTEXT.md`）：**办事必须能在手机浏览器完成**；**配置按桌面使用**。设计器只出现在配置向导；手机路径是渲染已保存的 JSON 规则，不是拖拽设计。

---

## 1. 坐标总表（后继 / 兼容事实）

「后继」= 官方或第一方声明的 Vue 3（及本票倾向的 Vite / Element Plus）对应物。版本号只作 2026-08-19 npm 观察，不是规格选择。

| 现坐标 | Vue 3 + Vite 后继（事实） | 硬约束 / 兼容坑 | 观察 tag（2026-08-19） |
|--------|---------------------------|-----------------|------------------------|
| `vue` 2.6.10 | `vue` 3.x；官方脚手架是 Vite（`create-vue`） | Vue 2 已 EOL（2023-12-31）；Vue 3 只要原生 ES2016，**不支持 IE11** | `latest` 3.5.41 |
| `vue-template-compiler` 2.6.10 | 换成 `@vue/compiler-sfc`；现随 `vue` 发布，推荐深导入 `vue/compiler-sfc` | 编译器必须与 runtime **同版本** | 与 `vue` 同号 3.5.41 |
| `vue-router` 3.0.6 | 官方迁移文档：v3 → **v4**（`createRouter`）。同仓库后又发了 **v5**（并入 unplugin-vue-router；不用文件路由则对 v4 **无 breaking**） | v4 相对 v3 有 breaking（见 §3）。v5 peer：`vue` `^3.5.34 \|\| ^4.0.0`；`vite`/`pinia` 等为 **optional** | v4 线仍有 4.6.4；`latest` 5.2.0；`legacy` 3.6.5 |
| `vuex` 3.1.0 | **Vuex 4**（Vue 3）；**Pinia** 是 Vue 核心团队现状推荐，Vuex 维护模式 | Vuex 4 peer `vue ^3.2.0`。Pinia 文档：2025 年起不再支持 Vue 2；Pinia 4 peer `vue ^3.5.11` | vuex `latest` 4.1.0；pinia `latest` 4.0.3 |
| `element-ui` 2.13.2 | **Element Plus**（Vue 3 组件库）。`element-ui` 仍是 Vue 2 | Element Plus peer `vue ^3.3.7`；≥2.5.0 浏览器矩阵含 Safari ≥14.1；≥2.8.5 要求 Sass ≥1.79.0 | element-plus `latest` 2.14.4；element-ui `latest` 2.15.14（仍 Vue 2） |
| `@form-create/element-ui` 2.5.17 | **有官方 Vue 3 + Element Plus 后继**：同一包名的 **`@next` / 3.x**，文档写明是 Element Plus 版 | 包名仍叫 `element-ui`，内容已是 Plus。`next` peer `vue ^3.5.12`。Vite 下官方不支持 unplugin 自动按需，需 `auto-import` 辅助（见 §5） | `latest` 2.6.3（Vue 2）；`super` 2.7.30（Vue 2.7）；`next` **3.3.3** |
| `@form-create/designer` 1.0.8 | **有官方后继**：`@form-create/designer@next`，README 写明基于 **Element Plus**，Vue 2/3 都宣称支持；Vue 3 安装命令用 `@next` | `next` peer `vue ^3.5`，依赖 `element-plus ^2.11.1` 与 `@form-create/element-ui ^3.3.1` | `latest` 1.3.0（peer Vue ^2.7.16）；`next` **3.5.0** |
| `axios` 0.18.1 | axios 本身与 Vue 解耦；1.x 是当前主线 | 0.x → 1.x 有官方迁移指南（错误处理、JSON、IE11） | `latest` 1.19.0；`v0x` 0.33.0 |
| `@vue/cli-*` 4.4.4 | 官方：**Vue CLI 维护模式**；新项目用 `create-vue` + **Vite**。Vue 3 SFC：`@vitejs/plugin-vue` | Vite 8 engines：Node `^20.19.0 \|\| >=22.12.0`。Vue 官方 Quick Start 对 `create-vue` 还写了更严的 Node（`^22.18.0 \|\| >=24.12.0`） | vite `latest` 8.2.1；plugin-vue `latest` 6.0.8 |
| Jest + VTU 1.x | Vue 3 组件测试：**`@vue/test-utils` 2.x**。Vite 项目官方推荐 **Vitest**；Jest 仍可用（需 `vue-jest@next`） | VTU 2 peer `vue` 3.x。Vitest 4：Vite ≥6、Node ≥20 | VTU `latest` 2.4.11，`legacy` 1.3.6；vitest `latest` 4.1.11 |
| `js-cookie` 2.2.0 | 与框架无关；3.x 仍是 cookie API | 现用法 `Cookies.get/set/remove` 仍是该库表面 | `latest` 3.0.8 |
| `nprogress` 0.2.0 | 与框架无关 | `latest` 仍是 0.2.0；`next` 1.0.0-1 为预发布 tag | `latest` 0.2.0 |
| `sass` 1.26.8 + `sass-loader` 8.0.2 | Vite **内置** `.scss`/`.sass`，装 `sass` 或 `sass-embedded` 即可，**不需要** `sass-loader` | Element Plus ≥2.8.5 → Sass ≥1.79.0。sass 1.102 engines：Node ≥20.19.0 | sass `latest` 1.102.0 |
| `core-js` 3.6.5 | Vue CLI/Babel 的 polyfill 入口。Vite 默认打现代浏览器；旧浏览器走官方 `@vitejs/plugin-legacy` | Vue 3 有 ES2016 特性 **无法** 用 polyfill 补到 IE11。办事手机浏览器若是近年版本，与 Vite 默认 production target 同向 | core-js `latest` 3.50.0（是否还需要由目标浏览器决定，本票不钉） |

---

## 2. vue 2.6.10 / vue-template-compiler

### 2.1 后继

- Vue 3 是当前主版本；新项目官方推荐 Vue 3。([Vue FAQ](https://vuejs.org/about/faq.html))
- Vue 2 于 2023-12-31 结束 LTS。([同上](https://vuejs.org/about/faq.html))
- 官方 SPA 脚手架：`npm create vue@latest`（create-vue），构建链是 **Vite + SFC**。([Vue Quick Start](https://vuejs.org/guide/quick-start.html)、[Tooling](https://vuejs.org/guide/scaling-up/tooling.html))
- `new Vue()` / `Vue.use()` 改为 `createApp` + `app.use` / `app.mount`。([Vue 3 Migration：Global API](https://v3-migration.vuejs.org/breaking-changes/global-api.html))
- 可选增量：`@vue/compat`（3.1 起）把 `vue-template-compiler` 换成 `@vue/compiler-sfc`，并可用 Vite 别名。([Migration Build](https://v3-migration.vuejs.org/migration-build.html))
- `@vue/compiler-sfc` 与 `vue` 同版本发布，已作为 `vue` 的依赖，**推荐** `vue/compiler-sfc` 深导入以免版本漂移。([Tooling：Lower-Level Packages](https://vuejs.org/guide/scaling-up/tooling.html))

### 2.2 与本仓库相关的 breaking（未穷尽）

客户端大量 Options API + `Vue.use`（`src/main.js`、`src/router/index.js`、`src/store/index.js`）。Options API 在 Vue 3 仍可用；必须改的是应用实例 API，以及模板里的 Vue 2 语法（配置页 `slot-scope` 等）。([Migration Build 特性表](https://v3-migration.vuejs.org/migration-build.html)；[Slots unification](https://v3-migration.vuejs.org/breaking-changes/slots-unification.html))

### 2.3 浏览器 / 手机办事

Vue 3.x 只要原生 ES2016 的浏览器，明确排除 IE11；部分特性无法 polyfill。([Vue FAQ：What browsers does Vue support?](https://vuejs.org/about/faq.html))  
办事走手机浏览器，与「不支持 IE11」不冲突；仍要对照目标 WebView/Safari/Chrome 是否达到 Vue 3 + Element Plus + Vite production 的下限（见 §4、§6）。

---

## 3. vue-router 3.0.6 / vuex 3.1.0

### 3.1 vue-router

官方从 Vue 2 迁 Vue 3 的文档标题是 **Migrating from Vue 2**，坐标是 **v3 → v4**：`new Router()` → `createRouter`；`mode` → `history`（`createWebHistory` / `createWebHashHistory` / `createMemoryHistory`）；catch-all `*` 改为自定义 param；`router-view` 与 `transition`/`keep-alive` 的组合方式变了。([Vue Router 迁移指南](https://router.vuejs.org/guide/migration/))

v4 仍在发布：npm `vue-router@4.6.4` peer `vue ^3.5.0`，仓库 `vuejs/router`。

其后 **Vue Router 5**（2026-01-29）：把 unplugin-vue-router 并进核心；**若只用 v4、不用文件路由，官方写明无 breaking，可只升依赖**。IIFE 构建不再打进 `@vue/devtools-api`。v6 预告 ESM-only 并删废弃 API。([v5.0.0 release](https://github.com/vuejs/router/releases/tag/v5.0.0)、[Migrating to Vue Router 5](https://router.vuejs.org/guide/migration/v4-to-v5.html))

npm `latest`（2026-08-19）= 5.2.0。peer：`vue` 必选 `^3.5.34 || ^4.0.0`；`vite` / `pinia` / `@pinia/colada` / `@vue/compiler-sfc` 均为 **optional**（`peerDependenciesMeta`）。

本仓库路由是手写 `routes`（`src/router/index.js`），不是文件路由。

### 3.2 vuex / Pinia

- **Vuex 4**：给 Vue 3；`createStore` + `app.use(store)`；几乎保持 Vuex 3 API，安装过程对齐 Vue 3。安装页仍出现 `npm install vuex@next`。([Vuex 4 安装](https://vuex.vuejs.org/installation.html)、[Migrating to 4.0](https://vuex.vuejs.org/guide/migrating-to-4-0-from-3-x.html))  
  npm：`latest` 4.1.0，peer `vue ^3.2.0`。
- **Pinia**：Vue 核心团队维护；文档写 Vuex 进入维护、不再加功能，**新应用推荐 Pinia**。([Vue State Management](https://vuejs.org/guide/scaling-up/state-management.html)、[Pinia 简介](https://pinia.vuejs.org/introduction.html))  
  Pinia 提供从 Vuex ≤4 迁过来的步骤（模块→store、mutations 并入 actions 等），并允许迁移期混用。([Migrating from Vuex](https://pinia.vuejs.org/cookbook/migration-vuex.html))  
  简介写明 **2025 年去掉 Vue 2 支持**。npm Pinia 4.0.3 peer `vue ^3.5.11`。

本票不在 Vuex 4 与 Pinia 之间做选择。地图 #22 也还未钉状态库。

---

## 4. element-ui 2.13.2

- `element-ui` 仍是 **Vue 2** 库（npm 2.15.14 peer `vue ^2.5.17`）。([npm `element-ui`](https://www.npmjs.com/package/element-ui))
- Vue 3 对应物是 **Element Plus**：文档用 `createApp` + `app.use(ElementPlus)`，并给出 **Vite** 全量 / `unplugin-vue-components` 按需示例。([Installation](https://element-plus.org/en-US/guide/installation.html)、[Quick Start](https://element-plus.org/en-US/guide/quickstart.html))
- 官方有 Element UI 2.x → Element Plus 迁移说明与迁移工具。compat build 上部分组件依赖 Vue 3 内部 API，建议 `compatConfig` MODE 3。([Migration](https://element-plus.org/en-US/guide/migration.html))
- 浏览器：Vue 3 已弃 IE11，Plus 也不支持 IE。≥2.5.0：Chrome/Edge ≥85、Firefox ≥79、Safari ≥14.1；文档写可跑 last 2 versions。([Installation：Compatibility](https://element-plus.org/en-US/guide/installation.html))
- Sass：≥2.8.5 最低 Sass 1.79.0；Vite 可配 `css.preprocessorOptions.scss.api: 'modern-compiler'`。([同上](https://element-plus.org/en-US/guide/installation.html))
- 现客户端 `Vue.use(ElementUI, { size: 'small' })` 对应 Plus 的 `app.use(ElementPlus, { size: 'small' })`。([Quick Start：Global Configuration](https://element-plus.org/en-US/guide/quickstart.html))
- 现 `import { Message } from 'element-ui'`（`request.js`、`permission.js`）在 Plus 里是独立 `ElMessage` API（组件库拆分，迁移时按 Plus 文档替换，本票不展开每个组件）。

Safari 14.1+ 覆盖 2021 年起的 iOS Safari；是否覆盖产品要支持的最旧手机，由规格另钉。

---

## 5. @form-create/designer 1.0.8 与 @form-create/element-ui 2.5.17

### 5.1 有没有官方 Vue 3 + Element Plus + Vite 后继？

**有官方 Vue 3 + Element Plus 后继。** Vite 不是单独的 form-create 发行版，而是同一套 npm 包在 Vite 工程里用；第一方 issue 按 Vite 用法答过。

事实：

1. **form-create `next` 分支 = Vue 3。** 官方 README：`latest` = Vue 2；`super` = Vue 2.7；`next` = Vue 3。Vue 3 安装：`npm install @form-create/element-ui@next`。表格把 `@form-create/element-ui` 标成 **「element-plus 版本」**，文档链接 `form-create.com/v3/element-ui/`。([form-create next README](https://github.com/xaboy/form-create/blob/next/README.md))
2. **渲染器 3.x 文档**写：`npm i @form-create/element-ui@^3` + `element-plus`，`Vue.createApp` + `app.use(formCreate)` + `app.use(ElementPlus)`。兼容栏只列 Element Plus。rule 仍是 `{ type, field, title, value, options }` 这种 JSON。([v3 Element Plus 安装](https://form-create.com/v3/element-ui/))
3. **设计器官方 README（next）**：`@form-create/designer`「基于 `@form-create/element-ui` 和 **ElementPlus**」。Node 安装：
   `npm install @form-create/designer@next`、`@form-create/element-ui@next`、`element-plus`；`app.use(FcDesigner)` + `app.use(FcDesigner.formCreate)`；模板仍是 `<fc-designer ref="designer"/>`。([form-create-designer next README](https://github.com/xaboy/form-create-designer/blob/next/README.md))
4. **npm（2026-08-19）**
   - `@form-create/element-ui@3.3.3`（`dist-tag` `next`）：description 为「Element Plus 版本」；peer `vue ^3.5.12`。
   - `@form-create/designer@3.5.0`（`next`）：peer `vue ^3.5`；dependencies 含 `element-plus ^2.11.1`、`@form-create/element-ui ^3.3.1`。
   - 当前仓库钉的 2.5.17 / 1.0.8 落在 **Vue 2 / Element UI** 线（`latest` 仍是 2.x / 1.x）。
5. **Vite**：作者在 Vite + `unplugin-vue-components` 场景下回复：暂不支持自动按需，要手动  
   `import install from '@form-create/element-ui/auto-import'` 再 `formCreate.use(install)`。([form-create#511](https://github.com/xaboy/form-create/issues/511))  
   因此：**有 Vue 3 + Element Plus 的官方包；在 Vite 里可用；按需策略与 Element Plus 的 unplugin 示例不是同一条路。**

### 5.2 与现审批模板数据的衔接（事实，非选型）

现实现把设计器 `getRule()` / `getOption()` 存进 `oa` 模板的 `formProps` / `formOptions`，办事页再 `:rule` / `:option` 喂给 `<form-create>`。v3 文档仍是同一套 JSON 驱动 API。**第一方没有保证 2.5.x 写出的 JSON 在 3.x 上零修改可渲染**；规格若沿用 form-create 3.x，需要用现网/种子模板做一次回放验证（本票不做该实验）。

现客户端方法名（`setRule` / `getRule` / `setOption` / `getOption`）与官方 Vue 3 设计器用法一致（`<fc-designer ref="designer"/>`）。([designer next README](https://github.com/xaboy/form-create-designer/blob/next/README.md))

### 5.3 同一官方线里的其它包（不是后继选型）

form-create next README 还列出 `@form-create/vant`（移动端渲染）和 `@form-create/vant-designer`（移动端设计器）。([next README 包表](https://github.com/xaboy/form-create/blob/next/README.md)；[designer next：移动端设计器](https://github.com/xaboy/form-create-designer/blob/next/README.md))  
按 `CONTEXT.md`：设计器属于**配置（桌面）**；手机只要能填已发布模板。vant-designer 不是当前桌面向导的必需后继，只是同一生态里另有移动端设计器这一事实。

### 5.4 「若无官方后继」条款

本票要求：若 form-create **没有** Vue 3 + Element Plus + Vite 官方后继，再列 Vue 3 里能画动态审批表的既有方案。  
**结论：有官方后继（§5.1），因此不展开第三方清单、也不做实现选型。** 地图 #22 仍允许「能力必须留、实现可换」——那是规格决策，不是本票事实缺口。

---

## 6. axios 0.18.1

axios 是 HTTP 客户端，不绑 Vue 大版本。([axios 文档](https://axios-http.com/docs/intro))

- npm `latest` 1.19.0；0.x 仍以 `v0x`（0.33.0）发布。
- 0.x → 1.x 官方迁移指南：错误处理更严（拦截器需 `return Promise.reject` / throw）；JSON 更严；**1.x 面向现代浏览器，不再把 IE11 当目标**。([MIGRATION_GUIDE.md](https://github.com/axios/axios/blob/v1.x/MIGRATION_GUIDE.md))
- 现用法（`axios.create`、请求/响应拦截器、`config.headers['token']`）在 1.x 仍是公开 API；TypeScript 下请求拦截器 config 类型改为 `InternalAxiosRequestConfig`（本仓库现为 JS）。([upgrade-guide](https://github.com/axios/axios/blob/v1.15.0/docs/pages/getting-started/upgrade-guide.md))

Vite 下现有 `process.env.VUE_APP_BASE_API` 不会按 vue-cli 规则注入，见 §7。

---

## 7. @vue/cli-* 4.4.4 与 Jest 栈

### 7.1 构建链

- Vue CLI 首页：**Maintenance Mode**；新项目用 create-vue（Vite）。([cli.vuejs.org](https://cli.vuejs.org/)、[Tooling：Vue CLI](https://vuejs.org/guide/scaling-up/tooling.html))
- Vite 官方 Vue 插件：`@vitejs/plugin-vue`（peer `vue ^3.2.25`，Vite `^5 || ^6 || ^7 || ^8`）。([Vite Plugins](https://vitejs.dev/plugins/))
- `npm create vite@latest -- --template vue` 也是官方 Vue 模板。([Vite Getting Started](https://vitejs.dev/guide/))
- 环境变量：vue-cli 只把 `VUE_APP_*` 打进客户端（`process.env.VUE_APP_*`）。([Vue CLI env](https://cli.vuejs.org/guide/mode-and-env.html))  
  Vite 默认暴露 `import.meta.env.VITE_*`。([Vite env](https://vitejs.dev/guide/env-and-mode.html))  
  仓库里 `request.js` / `process-template.js` 读 `process.env.VUE_APP_BASE_API`，换 Vite 必须改前缀或自配 `define`/`envPrefix`。
- 开发代理：现 `vue.config.js` 的 `devServer.proxy`；Vite 对等物是 `server.proxy`（本票只记差异，不写配置）。
- `@` alias：现 `configureWebpack.resolve.alias`；Vite 用 `resolve.alias`。
- Node：Vite 8 要求 `^20.19.0 || >=22.12.0`。([Vite Getting Started](https://vitejs.dev/guide/)) 地图 #22 把 Node 版本列为未钉。

### 7.2 测试

现：`@vue/cli-plugin-unit-jest` 4.4.4、`@vue/test-utils` 1.0.0-beta.29、`babel-jest` 23.6.0、`jest.config.js` 里 `vue-jest`。这是 **Vue 2** 测试栈。

后继事实：

- **Vue Test Utils 2** 面向 Vue 3；`createLocalVue` 删除；`propsData`→`props`（仍兼容）；`mocks`/`stubs` 进 `global`；`destroy`→`unmount`。([VTU 迁移](https://test-utils.vuejs.org/migration/))  
  安装页：**推荐测试运行器 Vitest**；Jest 需 `vue-jest@next`。([VTU Installation](https://test-utils.vuejs.org/installation/))
- Vue 官方 Testing：**create-vue 基于 Vite，推荐 Vitest**；已有 Jest 套件要迁到 Vite 时才优先考虑继续用 Jest。([Vue Testing](https://vuejs.org/guide/scaling-up/testing.html))
- Vitest：Vite 驱动；默认读 `vite.config.*`；Vitest 4 要求 Vite ≥6、Node ≥20。([Vitest Getting Started](https://vitest.dev/guide/))
- create-vue 交互项含「Add Vitest for Unit testing」。([Vue Quick Start](https://vuejs.org/guide/quick-start.html))

#22 *Not yet specified* 已写明：统一客户端测试栈（Jest 还是 Vitest）与 Node 等前端后继坐标出来再钉。本票只提供上述事实，不钉。

---

## 8. js-cookie / nprogress / sass / core-js

### 8.1 js-cookie 2.2.0

框架无关。现 `Cookies.get/set/remove`。npm `latest` 3.0.8（ESM `dist/js.cookie.mjs` + browser 包）。Vue 3 / Vite 不强制换大版本；3.x 是当前主线。

### 8.2 nprogress 0.2.0

框架无关。现 `import NProgress from 'nprogress'` + CSS。npm `latest` 仍为 **0.2.0**；`next` = `1.0.0-1`（预发布）。无 Vue 3 专属后继包。

### 8.3 sass 1.26.8 / sass-loader 8.0.2

- 客户端多处 `<style lang="scss">`。
- Vite 内置 Sass/Less/Stylus；**不必装 Vite 专用 plugin，但要装预处理器本身**（`sass` 或 `sass-embedded`）。([Vite CSS Pre-processors](https://vitejs.dev/guide/features.html))
- `sass-loader` 是 webpack loader，Vue CLI 用；Vite 不用。
- 与 Element Plus ≥2.8.5 的 Sass ≥1.79.0 约束叠加（§4）。sass 1.102 engines：Node ≥20.19.0。

### 8.4 core-js 3.6.5

- vue-cli + `@vue/cli-plugin-babel` 用 core-js 做语法/API polyfill。
- Vite 开发按现代浏览器；production 默认 Baseline Widely Available（该 major 约等于 2023 年中的浏览器）；更老的生产目标用官方 `@vitejs/plugin-legacy`。([Vite Getting Started：Browser Support](https://vitejs.dev/guide/)、[plugin-legacy](https://vitejs.dev/plugins/))
- Vue 3 部分 ES2016 特性 **不能** 靠 polyfill 回到 IE11。([Vue FAQ](https://vuejs.org/about/faq.html))
- 因此 core-js 不是 Vue 3 的「必须后继包」；是否保留取决于规格要覆盖的手机浏览器，而不是 Vue 大版本本身。

---

## 9. 和「办事手机 / 配置桌面」叠在一起的约束

| 层 | 事实 | 来源 |
|----|------|------|
| 设计器 | 只在配置向导第二步，桌面 | `templateSet.vue`、`CONTEXT.md` |
| 办事表单 | 手机浏览器渲染 JSON rule | `apply.vue`、`CONTEXT.md` |
| Vue 3 | 无 IE11 | Vue FAQ |
| Element Plus ≥2.5 | Safari ≥14.1 等 | Element Plus Installation |
| Vite 8 默认 production | 约 2023 年中起的 widely available 浏览器；可下调 target 或加 plugin-legacy | Vite Guide |
| form-create 3 | 官方宣称适配移动端；Element Plus 版渲染器是办事路径的官方后继 | form-create next README、v3 element-ui 文档 |

以上是下限与能力事实，不是「必须用哪一套包」的决定。

---

## 10. 本票不钉、留给地图 #22 的

- 具体版本号（只记录了 2026-08-19 的 dist-tag）。
- Vuex 4 vs Pinia。
- vue-router 4.6.x vs 5.x（对当前手写路由，官方称 5 无 breaking）。
- Jest 继续 vs 改 Vitest（#22 已列为未钉）。
- Node 版本（Vite / sass / Vitest / create-vue 文档下限不一致）。
- form-create 2.x JSON 与 3.x 是否零修改兼容（需回放，本票未测）。
- 是否动 form-create 实现（#22 允许换实现；本票确认官方后继存在）。

---

## 11. 主要来源

- 仓库：`前端代码/guigu-oa-client/package.json`、`src/main.js`、`src/views/config/processTemplate/*`、`src/views/work/start/apply.vue`、`src/config/process-template.js`、`src/work/start-flow.js`、`src/utils/request.js`、`vue.config.js`、`jest.config.js`；根目录 `CONTEXT.md`
- Vue：<https://vuejs.org/guide/quick-start.html> <https://vuejs.org/guide/scaling-up/tooling.html> <https://vuejs.org/guide/scaling-up/testing.html> <https://vuejs.org/guide/scaling-up/state-management.html> <https://vuejs.org/about/faq.html> <https://v3-migration.vuejs.org/migration-build.html> <https://v3-migration.vuejs.org/breaking-changes/global-api.html>
- Vite：<https://vitejs.dev/guide/> <https://vitejs.dev/guide/features.html> <https://vitejs.dev/guide/env-and-mode.html> <https://vitejs.dev/plugins/>
- Vue CLI：<https://cli.vuejs.org/> <https://cli.vuejs.org/guide/mode-and-env.html>
- vue-router：<https://router.vuejs.org/guide/migration/> <https://router.vuejs.org/guide/migration/v4-to-v5.html> <https://github.com/vuejs/router/releases/tag/v5.0.0>
- Vuex / Pinia：<https://vuex.vuejs.org/installation.html> <https://vuex.vuejs.org/guide/migrating-to-4-0-from-3-x.html> <https://pinia.vuejs.org/introduction.html> <https://pinia.vuejs.org/cookbook/migration-vuex.html>
- Element Plus：<https://element-plus.org/en-US/guide/installation.html> <https://element-plus.org/en-US/guide/quickstart.html> <https://element-plus.org/en-US/guide/migration.html>
- form-create：<https://github.com/xaboy/form-create/blob/next/README.md> <https://github.com/xaboy/form-create-designer/blob/next/README.md> <https://form-create.com/v3/element-ui/> <https://github.com/xaboy/form-create/issues/511> ；npm `@form-create/element-ui` / `@form-create/designer`
- axios：<https://axios-http.com/docs/intro> <https://github.com/axios/axios/blob/v1.x/MIGRATION_GUIDE.md>
- VTU / Vitest：<https://test-utils.vuejs.org/installation/> <https://test-utils.vuejs.org/migration/> <https://vitest.dev/guide/>
- npm registry 观察日期：2026-08-19
