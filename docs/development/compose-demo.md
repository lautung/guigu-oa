# Compose 演示环境

## 启动

从仓库根目录运行：

```bash
docker compose up --build
```

Compose 会启动 MySQL、Redis、MinIO、Spring Boot 服务和统一客户端。Flyway 创建业务表、抄送表和演示账号；Flowable 首次启动时创建 `ACT_*` / `FLW_*` 引擎表；后端部署演示审批定义并幂等生成场景数据。浏览器打开 `http://localhost:9530`。

| 页面/服务 | 地址 |
|---|---|
| 统一客户端 | `http://localhost:9530` |
| 后端 API | `http://localhost:8800` |
| MinIO Console | `http://localhost:9001` |

演示账号的密码均为 `111111`：

| 用户名 | 用途 |
|---|---|
| `admin` | 配置与办事 |
| `alice` | 发起审批 |
| `bob` | 查看待处理并完成一条审批 |
| `carol` | 查看被抄送的审批 |

已结束场景由 Bob 处理。演示数据包含一条 Bob 待处理的审批、一条已结束审批、Carol 的节点抄送，以及一条保存在 MinIO 的附件和对应元数据。

## 重置

下面的命令删除当前 Compose 项目的所有持久数据，再由 Flyway、Flowable 和演示初始化器重建：

```bash
docker compose down --volumes --remove-orphans
docker compose up --build
```

## 验收 API

环境运行后，可用 Node 22 执行公开接口冒烟检查：

```bash
node scripts/compose-demo-smoke.mjs
```

它会检查登录页、Bob 的待处理和已处理列表、Carol 的抄送列表、抄送详情，以及该审批的附件元数据和只读授权。

## 数据与地址

- `mysql-data` 保存业务与 Flowable 表；`redis-data` 保存本地缓存；`minio-data` 保存附件；`process-definitions` 保存后续上传的流程 zip。
- 后端容器通过 `http://minio:9000` 访问对象存储。浏览器使用 `http://localhost:9000` 访问预签名 URL，Compose 初始化器为演示桶设置 localhost CORS。
- Compose 中的密码和 MinIO 凭据只用于本地演示，不要用于部署环境。
