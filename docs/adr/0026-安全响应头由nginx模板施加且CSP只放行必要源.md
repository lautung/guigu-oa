# 安全响应头由 nginx 模板施加，CSP 只放行必要源

#139 复现出一个 S3：9530 的所有 HTML 响应都没有 `Content-Security-Policy`、`X-Frame-Options` 和
`X-Content-Type-Options`。页面可以被任意站点 iframe 嵌套做点击劫持；缺 CSP 让任何 XSS 少一层
兜底；缺 `nosniff` 允许部分浏览器嗅探 MIME。此前 nginx 只负责把 `dist/` 的静态文件发出去，
没有任何安全响应头。

现在 `client/guigu-oa-client/nginx.conf.template` 在 `server` 级加三条头，全部带 `always`，
所以 SPA 回退、静态资源和 404 都覆盖到；`location` 块都不再自己写 `add_header`，继承这一份。

CSP 按「只放行页面真实需要的源」写定：

- `script-src 'self'`：构建产物只有外链 module 脚本，没有内联脚本，也没有 `eval` 或
  `Function` 构造器（逐 chunk 核对过 dist）；XSS 注入点靠这一条兜底。
- `style-src 'self' 'unsafe-inline'`：Element Plus 的浮层、Vue 过渡与 nprogress 都写内联样式。
  把 `<style>` 元素与 style 属性拆成 `style-src-elem 'self'` + `style-src-attr 'unsafe-inline'`
  能少放行一类，但 Safari 14.1 与 Firefox 79–107 不认识这两个指令，会回退到
  `default-src 'self'` 把内联样式全拦掉，破坏声明过的构建目标（`chrome85`/`firefox79`/
  `safari14.1`）。所以整体放行 `unsafe-inline`，并用 `SEC-HDR-01` 把它钉成唯一形态：
  `style-src` 必须逐字等于 `'self' 'unsafe-inline'`。残余风险是样式注入，不能执行脚本，
  按 S4 接受。
- `img-src 'self' data: blob: https://gw.alicdn.com`：审批模板图标的 URL 存在
  `oa_process_template.icon_url`，历史数据与图标选择器共用；改成同源地址会让存量模板的图标
  失效，所以只放行这一个图片源。图片不能执行脚本。
- `connect-src 'self' ${CSP_CONNECT_SRC}`：附件上传是浏览器拿预签名 URL 直接
  `fetch PUT` 到 MinIO，不走同源代理。这个源与 `service-oa` 的 `S3_PUBLIC_ENDPOINT` 成对配置，
  由 nginx 官方镜像的模板机制在容器启动时用 envsubst 展开；`$host`、`$uri` 等 nginx 自身变量
  不在环境变量表里，保持原样。
- `object-src 'none'`、`base-uri 'self'`、`form-action 'self'`、`frame-ancestors 'none'` 收口。

顺带不再下发第三方占位头像：`/admin/system/index/info` 原先硬编码阿里云 OSS 的示例图，
客户端默认头像又是钉钉 CDN 的图，两者都只是装饰。现在 `avatar` 字段保留但下发空串（`/info`
的字段名不变，见 `docs/spec/升级到Spring-Boot-4与Vue-3的依赖规格.md`），客户端默认图换成
同源 `public/avatar.svg`。这样 `img-src` 不必为两个占位 CDN 开口子，页面也不再每次加载
都去 ping 第三方 CDN。

不采用 Report-Only 起步：ZAP 的规则只认强制的 `Content-Security-Policy`，Report-Only 不会让
那条 Medium 消失，也无法证明「没有任何资源被拦」。代替做法是把「零违规」做成浏览器用例
`SEC-HDR-05`：登录、办事列表与详情、模板列表与表单设计器都监听 `securitypolicyviolation`。
只有一种已知违规被豁免：`@form-create/designer` 把 core-js 打进自己的 dist，模块初始化的
全局探测执行 `Function('return this')()`，严格模式下每次加载必然被 `script-src` 拦一次，
`catch` 里回退到 `window`，功能不受影响；除这种 `script-src` + `eval` 之外一律失败。

否决的替代：在 Vite dev server 也加同样的头——dev 跑 HMR WebSocket 与未打包模块，策略与产物
不可能一致，安全基线打的是 nginx 产物，两套只会制造两种事实；把 CSP 放进 `index.html` 的
`<meta>`——`frame-ancestors` 在 meta 里被忽略，API 响应也拿不到；`img-src https:` 或额外放行两个
头像 CDN——会把装饰图变成任意可跟踪像素，而这两个地址本来就不是真实头像来源。

代价：改 nginx 配置要重建 `client` 镜像（容器重启不会重新生成）；部署时漏配 `CSP_CONNECT_SRC`
会让附件上传被浏览器拦截，`SEC-HDR-04` 把这条契约变成用例；`compose-baseline-check.sh` 多 3 条
头部断言（20 → 23）；安全脚本新增 SEC-HDR 族，`SEC-XSS-01` 与 `SEC-HDR-05` 用 `--grep` 各跑
自己的 Playwright spec。

同一被动 baseline 复跑：Missing Anti-clickjacking（5 Medium）、X-Content-Type-Options
（5 Low）与 9530 的 CSP 未设置（5 Medium）全部消失；剩下的 `CSP: style-src unsafe-inline`
（5 Medium）是上面接受的形态，`8800/swagger-ui` 的 CSP 与两端的 `Server` 版本号归 #140。

Related: #139（S3）；#140（`server_tokens` 与 springdoc）；#142（存储型 XSS，CSP 是兜底）；
#127（安全基线修复批次）。
