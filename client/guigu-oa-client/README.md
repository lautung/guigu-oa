# 云尚办公统一客户端

云尚办公的唯一前端。登录、办事、配置都在这里。

```bash
npm install
npm run dev
```

API 代理见 `.env.example` 与 `vite.config.js`（默认 `/dev-api` → `http://localhost:8800`）。

## 浏览器冒烟

首次运行先安装 Chromium，然后执行：

```bash
npx playwright install chromium
npm run test:e2e
```

用例覆盖桌面和手机视口下的登录、发起含附件审批及通过/拒绝。它们使用受控 API 和对象存储响应，验证客户端页面与请求组装，不验证后端持久化或真实 Flowable 流转。
