# Compose 演示环境

2026-10-28 在本机（macOS arm64、Docker Desktop 29.6.2、Compose 5.3.1、10 vCPU / 8 GB）实测通过，
下文的时间与版本均来自这次运行。测试基线的判定命令是 `./scripts/compose-baseline-check.sh`。

## 环境要求

- Docker Desktop（或等价 Docker 引擎）与 Compose v2。
- 可用内存 8 GB 以上：`mysql`、`redis`、`minio`、`service-oa`、`client` 五个容器同时运行。
- 端口 `3306`、`6379`、`9000`、`9001`、`8800`、`9530` 空闲。这些端口与本地开发路径
  （`npm run dev` 的 9530、`java -jar` 的 8800、系统 MySQL 3306、系统 Redis 6379）**冲突**，
  跑 Compose 前先停掉本地开发进程与系统服务。
- 网络受限（镜像站/代理）时通过下面两个环境变量指定镜像源，留空则用官方源：

  ```bash
  export MAVEN_MIRROR_URL=https://maven.aliyun.com/repository/public
  export NPM_REGISTRY=https://registry.npmmirror.com
  ```

  它们是可选的构建参数，默认不改变标准行为。

## 启动

从仓库根目录运行：

```bash
docker compose up -d --build --wait
```

Compose 会启动 MySQL、Redis、MinIO、Spring Boot 服务和统一客户端。Flyway 创建业务表、抄送表和演示账号；
Flowable 首次启动时创建 `ACT_*` / `FLW_*` 引擎表。浏览器打开 `http://localhost:9530`。

`--wait` 会在所有服务就绪后才返回；不加 `-d` 则是前台模式，日志直接打到终端。

### 镜像与版本

| 服务 | 镜像 | 实测版本 |
|---|---|---|
| mysql | `mysql:8.4` | MySQL 8.4.13 |
| redis | `redis:7.4-alpine` | Redis 7.4.12 |
| minio | `bitnamilegacy/minio:2025.7.23-debian-12-r5` | MinIO DEVELOPMENT.2025-07-23T15-54-02Z |
| minio-init | `bitnamilegacy/minio-client:2025.7.21-debian-12-r3` | mc 2025-07-21 |
| service-oa | 本地构建（`server/lautung-oa-parent/lautung-oa-server/Dockerfile`） | JDK 21.0.12 / Spring Boot 4.1.0 / Flowable 8.0.0 |
| client | 本地构建（`client/guigu-oa-client/Dockerfile`） | 构建镜像 node 22.18，运行镜像 nginx 1.31.8 |

MinIO 的官方镜像 `minio/minio`、`minio/mc` 已从 Docker Hub 与 quay.io 撤下（Docker Hub 返回
`object not found`，quay 匿名拉取返回 401），所以这里使用 Bitnami 存档的同一条上游二进制线。
换版本时同时更新 `compose.yaml` 里的两处镜像和本节表格。

### 端口

| 页面/服务 | 地址 |
|---|---|
| 统一客户端 | `http://localhost:9530` |
| 后端 API | `http://localhost:8800` |
| 后端健康检查 | `http://localhost:8800/actuator/health`（聚合）、`/actuator/health/readiness`（就绪探针） |
| MinIO Console | `http://localhost:9001` |
| MinIO S3 | `http://localhost:9000` |

### 演示账号

密码均为 `111111`：

| 用户名 | 用途 |
|---|---|
| `admin` | 配置与办事（配置菜单权限来自种子角色「配置管理员」） |
| `user` | 只办事，没有配置菜单权限 |
| `alice` | 发起审批 |
| `bob` | 查看待处理并完成一条审批 |
| `carol` | 查看被抄送的审批 |

已结束场景由 Bob 处理。演示数据包含一条 Bob 待处理的审批、一条已结束审批、Carol 的节点抄送，
以及一条保存在 MinIO 的附件和对应元数据。

### 接口文档

演示栈以 `dev,demo` 启动，后加载的 `application-demo.yml` 覆盖了 `dev` 的接口文档开关：
匿名面**不提供** `/v3/api-docs` 与 `/swagger-ui/index.html`（#140）。这是有意的——演示栈的
8800 端口直接对外开放，保留文档等于把全部端点、参数与数据模型交给未登录访问者。

本地需要看接口文档时只跑 `dev`：用 `java -jar target/lautung-oa-server.jar` 起后端（`application.yml`
的默认 profile 就是 `dev`）。前端 `npm run dev` 只是 9530 的 Vite 开发服务器，接口文档由 8800
后端提供，和前端是否运行无关。前端 nginx 同时设置了 `server_tokens off`，`Server` 头只回
`nginx`，不带版本号。

### 演示审批的发布

演示迁移只把「演示请假申请」种成**未发布**（`status=0`）。`service-oa` 以 `dev,demo` 启动时，
`DemoApprovalPublisher` 先读 classpath 的 `processes/demo-approval.bpmn20.xml` 现打一个流程 zip
写入 `process-definitions`，再走正常发布流程，把发布记录、Flowable 部署和 `status=1` 一次性补齐；
随后 `DemoScenarioSeeder` 才生成待处理、已结束、抄送和附件场景。整个过程没有手工步骤。

重复启动只读不写：模板已发布且发布证据可用时直接复用，不会再次部署或改状态。
日志出现 `Compose 演示数据就绪 pending=1 processed=1 carolCopied=2` 即为就绪。
早于本次修复创建的演示卷由 demo 迁移 `V101` 把「已发布但没有发布记录」的演示模板退回未发布，
同样在启动时自动补发布；已有发布记录的模板不受影响。

## 验收

环境运行后，先跑基线检查，再跑公开接口冒烟：

```bash
./scripts/compose-baseline-check.sh     # 容器、健康、安全头、匿名暴露面、种子账号、引擎表、演示审批
node scripts/compose-demo-smoke.mjs     # 登录页、Bob 待处理/已处理、Carol 抄送与附件授权
```

`compose-baseline-check.sh` 全部通过时输出 `结果：26 通过，0 失败`；冒烟脚本成功时输出
`Compose demo OK: Bob pending=1, ended=1, Carol copied=2, attachments=1`。

### 兼容性矩阵

矩阵与判定口径见 `docs/test/plan/compatibility-matrix.md`（#114/#117）。栈运行时：

```bash
cd client/guigu-oa-client
npx playwright install chromium webkit
E2E_BASE_URL=http://127.0.0.1:9530 npm run test:e2e   # 四格 + 1440 关键页扫描
npm run test:e2e:stack                                 # 真实栈套件（含 WebKit 手机审批 smoke）
```

`E2E_BASE_URL` 指向 nginx 生产构建时不再启动 Vite dev server；不设该变量时保持原行为。
生产构建的 CSP 要求 mock 的附件直传源落在 `connect-src` 里，e2e 统一使用默认的
`http://localhost:9000`。截图与逐页控制台日志写到 `test-results/compatibility/`。

## 安全响应头

`client` 的 nginx 对每个响应加 `Content-Security-Policy`、`X-Frame-Options: DENY` 和
`X-Content-Type-Options: nosniff`（#139）。CSP 里的 `connect-src` 除了 `'self'` 还要放行浏览器
直连的对象存储源——附件上传是浏览器拿着预签名 URL 直接 `fetch PUT` 到 MinIO，不在同源路径下。
这个源由 `client` 的 `CSP_CONNECT_SRC` 提供，默认 `http://localhost:9000`，必须与 `service-oa`
的 `S3_PUBLIC_ENDPOINT` 成对配置：

```bash
export S3_PUBLIC_ENDPOINT=https://files.example.com
export CSP_CONNECT_SRC=$S3_PUBLIC_ENDPOINT
docker compose up -d --build client
```

配置文件本体是 `client/guigu-oa-client/nginx.conf.template`，官方 nginx 镜像启动时用 envsubst
展开 `${CSP_CONNECT_SRC}`；nginx 自己的 `$host`、`$uri` 等变量保持不变。改了模板要重建
`client` 镜像，容器重启不会重新生成。

## 跨域

API 与客户端同源：浏览器只访问 9530，`/dev-api` 由客户端 nginx 反代到 `service-oa:8800`。
所以 `service-oa` 不下发任何 CORS 响应头，也没有开启 Spring 的 CORS 处理（#141）；开发态的
9530 同样经 Vite 代理访问 8800，不需要跨域。演示栈里唯一的跨源请求是附件直传 MinIO，由
`minio` 服务的 `MINIO_API_CORS_ALLOW_ORIGIN` 单独放行。

如果部署时把 API 放到另一个来源，在入口网关按受信来源配置 `Access-Control-Allow-Origin`，
不要用 `*`；也不要在控制器上加 `@CrossOrigin`，`CorsContractTest` 会因此失败。

## 重置

下面的命令删除当前 Compose 项目的所有持久数据，再由 Flyway、Flowable 和演示初始化器重建。
重置后演示审批由启动流程自动重新发布，无需额外操作。

```bash
docker compose down --volumes --remove-orphans
docker compose up -d --build --wait
```

本机实测：`down --volumes --remove-orphans` 2.1 s，`up -d --build --wait` 84.5 s
（镜像已拉取、构建层缓存命中）。冷启动还要加上首次拉取约 4.5 GB 基础镜像，以及
Maven / npm 依赖的全量下载；使用上面的镜像源时，首次构建约 110 s。

## 数据与地址

- `mysql-data` 保存业务与 Flowable 表；`redis-data` 保存本地缓存；`minio-data` 保存附件；
  `process-definitions` 保存模板流程 zip（发布时写入，容器内用户 `app` 需要可写）。
- 后端容器通过 `http://minio:9000` 访问对象存储。浏览器使用 `http://localhost:9000` 访问预签名 URL。
  MinIO 不实现 `PutBucketCors`（`mc cors set` 返回 `NotImplemented`），跨域由 `minio` 服务的
  `MINIO_API_CORS_ALLOW_ORIGIN=http://localhost:9530` 放行；实测 `OPTIONS` 预检返回 204 且带
  `Access-Control-Allow-Origin: http://localhost:9530`。
- Compose 中的密码和 MinIO 凭据只用于本地演示，不要用于部署环境。
- `service-oa` 的健康检查探测 `/actuator/health/readiness`，该路径与 `/actuator/health` 一样在匿名白名单内
  （只放行 health 端点及其分组，不开放其他 actuator 端点，且 `show-details: never` 不返回明细）。
  就绪分组由 Spring Boot 的 `readinessState` 决定：只有 `ApplicationReadyEvent` 之后才返回
  `{"status":"UP"}`，因此 Flyway 迁移、Flowable 建表和演示审批播种（`DemoScenarioSeeder` 等
  `ApplicationRunner`）完成后容器才会变 healthy；未就绪时返回 503，`curl --fail` 判定失败。
  `docker compose up --wait` 因此会等到后端真正可用再返回，`client` 的 `service_healthy` 依赖也才有意义。
  基线验收仍可用聚合健康端点 `/actuator/health`。
