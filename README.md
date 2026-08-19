# 云尚办公

**统一客户端**是唯一前端入口：办事与配置在同一个 Web 里，用用户名和密码登录。登录后落到待处理。微信不是产品的一部分。

旧的 PC 管理端（`前端代码/管理端/guigu-oa-admin`）和公众号员工 H5（`前端代码/员工端/guigu-oa-web`）已退役，不再作为产品入口维护。

## 启动

后端：

```bash
cd 后端代码/guigu-oa-parent
mvn -pl service-oa -am spring-boot:run
```

默认 API：`http://localhost:8800`。

统一客户端：

```bash
cd 前端代码/guigu-oa-client
npm install
npm run dev
```

开发代理把 `/dev-api` 转到 `http://localhost:8800`。浏览器打开终端提示的本地地址（默认端口 9530）。

生产构建：

```bash
cd 前端代码/guigu-oa-client
npm run build:prod
```

## 领域与规格

- 词汇：[CONTEXT.md](CONTEXT.md)
- 规格：[docs/spec/云尚办公统一客户端改造规格.md](docs/spec/云尚办公统一客户端改造规格.md)
