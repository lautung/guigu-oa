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
| service-oa | 本地构建（`server/guigu-oa-parent/service-oa/Dockerfile`） | JDK 21.0.12 / Spring Boot 4.1.0 / Flowable 8.0.0 |
| client | 本地构建（`client/guigu-oa-client/Dockerfile`） | 构建镜像 node 22.18，运行镜像 nginx 1.31.8 |

MinIO 的官方镜像 `minio/minio`、`minio/mc` 已从 Docker Hub 与 quay.io 撤下（Docker Hub 返回
`object not found`，quay 匿名拉取返回 401），所以这里使用 Bitnami 存档的同一条上游二进制线。
换版本时同时更新 `compose.yaml` 里的两处镜像和本节表格。

### 端口

| 页面/服务 | 地址 |
|---|---|
| 统一客户端 | `http://localhost:9530` |
| 后端 API | `http://localhost:8800` |
| 后端健康检查 | `http://localhost:8800/actuator/health` |
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

### 首次启动后：发布演示审批（当前缺陷的临时步骤）

演示迁移把「演示请假申请」直接写成 `status=1`，但**没有创建发布记录，也没有把流程 zip 放进
`process-definitions` 卷**。结果是 `DemoScenarioSeeder` 打印
`Compose 演示审批模板不可发起…跳过演示审批写入`，Bob 没有待处理任务，`compose-demo-smoke.mjs` 会失败。

在修复落地前，按下面的步骤补齐（用 admin 调公开接口）：

```bash
# 1. 从仓库里的 BPMN 打包 zip（仓库只有 .bpmn20.xml，没有 zip）
mkdir -p /tmp/demo-approval && cd /tmp/demo-approval
cp <repo>/server/guigu-oa-parent/service-oa/src/main/resources/processes/demo-approval.bpmn20.xml .
zip -q demo-approval.zip demo-approval.bpmn20.xml

# 2. 登录拿 token
TOKEN=$(curl -s -X POST http://localhost:8800/admin/system/index/login \
  -H 'Content-Type: application/json' -d '{"username":"admin","password":"111111"}' \
  | python3 -c "import json,sys;print(json.load(sys.stdin)['data']['token'])")

# 3. 上传 zip，返回 processDefinitionPath
curl -s -X POST http://localhost:8800/admin/process/processTemplate/uploadProcessDefinition \
  -H "token: $TOKEN" -F "file=@demo-approval.zip"
```

把返回的 `processDefinitionPath` 写进模板 3，再发布：

```sql
-- 发布需要模板处于未发布状态；种子写成已发布但没有发布记录，接口会拒绝保存和发布
UPDATE oa_process_template SET status = 0 WHERE id = 3 AND is_deleted = 0;
```

```bash
curl -s -X PUT http://localhost:8800/admin/process/processTemplate/update \
  -H "token: $TOKEN" -H 'Content-Type: application/json' -d @tpl3.json   # 含新的 processDefinitionPath
curl -s "http://localhost:8800/admin/process/processTemplate/publish/3" -H "token: $TOKEN"
docker compose restart service-oa     # 让 DemoScenarioSeeder 重新生成演示场景
```

成功时 `service-oa` 日志出现 `Compose 演示数据就绪 pending=1 processed=1 carolCopied=2`。

## 验收

环境运行后，先跑基线检查，再跑公开接口冒烟：

```bash
./scripts/compose-baseline-check.sh     # 容器、健康、5 个种子账号、ACT_*/FLW_* 表、演示审批
node scripts/compose-demo-smoke.mjs     # 登录页、Bob 待处理/已处理、Carol 抄送与附件授权
```

`compose-baseline-check.sh` 全部通过时输出 `结果：20 通过，0 失败`；冒烟脚本成功时输出
`Compose demo OK: Bob pending=1, ended=1, Carol copied=2, attachments=1`。
基线检查里「演示审批」四项失败即为上面「首次启动后」尚未执行。

## 重置

下面的命令删除当前 Compose 项目的所有持久数据，再由 Flyway、Flowable 和演示初始化器重建。
重置后演示审批需要重新执行一次「首次启动后」的发布步骤。

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
- `service-oa` 的健康检查用 `/actuator/health/readiness`，该路径目前在匿名白名单之外，
  未登录访问返回 `{"code":208,"message":"认证失败"}` 且 HTTP 200，`curl --fail` 因此判定通过。
  容器健康状态**不能**当作后端就绪的证据，验收请用 `/actuator/health` 或基线脚本。
