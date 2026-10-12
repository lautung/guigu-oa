# 云尚办公

**统一客户端**是唯一前端入口：办事与配置在同一个 Web 里，用用户名和密码登录。登录后落到待处理。微信不是产品的一部分。

## 起源与改造

本项目从尚硅谷《云尚办公》课程项目起步。旧 PC 管理端与公众号员工 H5 已退役，删前源码保存在 annotated tag `legacy-frontends`。两端能力与路由的历史对照见[研究文档](docs/research/现有两端能力与路由对照.md)。

## 启动

后端：

```bash
cd server/guigu-oa-parent/service-oa
mvn -DskipTests install
java -jar target/service-oa.jar
```

默认 API：`http://localhost:8800`。

统一客户端：

```bash
cd client/guigu-oa-client
npm install
npm run dev
```

开发代理把 `/dev-api` 转到 `http://localhost:8800`。浏览器打开终端提示的本地地址（默认端口 9530）。

生产构建：

```bash
cd client/guigu-oa-client
npm run build:prod
```

## 流程 zip 存储

流程 zip 保存在后端文件系统的 `processes/` 子目录，不写入 classpath，也不进入审批附件桶。数据库中的 `processDefinitionPath` 是相对路径，例如 `processes/qingjia.zip`。

后端目录由 `GUIGU_OA_PROCESS_DEFINITION_DIR` 配置，默认是启动目录下的 `./data/process-definitions`。容器部署应将环境变量指向持久卷挂载点，例如：

```yaml
services:
  service-oa:
    environment:
      GUIGU_OA_PROCESS_DEFINITION_DIR: /var/lib/guigu-oa/process-definitions
    volumes:
      - process-definitions:/var/lib/guigu-oa/process-definitions

volumes:
  process-definitions:
```

上传和发布使用同一目录。容器可以只读运行，只需保证上述挂载卷可写且在容器重建后保留；后续 Compose 配置应沿用这个挂载目标。

## 领域与规格

- 词汇：[CONTEXT.md](CONTEXT.md)
- 规格：[docs/spec/云尚办公统一客户端改造规格.md](docs/spec/云尚办公统一客户端改造规格.md)
- 探活与请求日志：[docs/observability.md](docs/observability.md)
