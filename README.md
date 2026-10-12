# 云尚办公

[![CI](https://github.com/lautung/guigu-oa/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/lautung/guigu-oa/actions/workflows/ci.yml)

**统一客户端**是唯一前端入口：办事与配置在同一个 Web 里，用用户名和密码登录。登录后落到待处理。微信不是产品的一部分。

## 起源与改造

本项目从尚硅谷《云尚办公》课程项目起步。旧 PC 管理端与公众号员工 H5 已退役，删前源码保存在 annotated tag `legacy-frontends`。两端能力与路由的历史对照见[研究文档](docs/research/现有两端能力与路由对照.md)。

## 启动

### 一键启动演示环境

安装 Docker Compose 后，在仓库根目录运行：

```bash
docker compose up --build
```

浏览器打开 `http://localhost:9530`。演示账号 `admin`、`alice`、`bob`、`carol` 的密码均为 `111111`。初始数据包括 Bob 的待处理和已结束审批、Carol 的抄送列表，以及一条带附件元数据和文件的审批。数据库、对象存储和流程定义会保存在 Compose 卷中。

重置演示数据（会删除该 Compose 项目的数据库、Redis、MinIO 与流程定义卷）：

```bash
docker compose down --volumes --remove-orphans
docker compose up --build
```

端口、演示数据和附件配置见[Compose 演示说明](docs/development/compose-demo.md)。

### 本地开发启动

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

## 审批附件对象存储

附件内容使用私有 S3 兼容桶。开发环境可以用 MinIO；先创建桶，再配置：

| 环境变量 | 用途 | 本地默认值 |
|---|---|---|
| `S3_ENDPOINT` | 后端访问对象存储的地址 | `http://localhost:9000` |
| `S3_PUBLIC_ENDPOINT` | 浏览器直传和下载所用地址，必须从浏览器可达 | `http://localhost:9000` |
| `S3_BUCKET` | 私有桶名称 | `guigu-oa-attachments` |
| `S3_REGION` | S3 签名 region | `us-east-1` |
| `S3_ACCESS_KEY` / `S3_SECRET_KEY` | 对象存储凭据 | 本地开发占位值 |

浏览器直传需要在桶上允许客户端来源的 `PUT` 和 `Content-Type` 请求头。容器部署时，`S3_ENDPOINT` 可指向 compose 内的 `http://minio:9000`，而 `S3_PUBLIC_ENDPOINT` 应指向浏览器可访问的地址；签名时使用的主机名必须与浏览器实际请求的主机名一致。不要把本地占位凭据用于部署环境。

预签名上传默认 5 分钟，下载默认 15 分钟；未发起的 pending 附件在 24 小时后由后台清理任务回收。单个附件默认上限 50 MiB，可用 `ATTACHMENT_MAX_FILE_SIZE_BYTES` 调整。

## 领域与规格

- 词汇：[CONTEXT.md](CONTEXT.md)
- 规格：[docs/spec/云尚办公统一客户端改造规格.md](docs/spec/云尚办公统一客户端改造规格.md)
- 探活与请求日志：[docs/observability.md](docs/observability.md)
