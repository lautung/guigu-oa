# 低成本常驻 demo 托管方案

> 研究票：[常驻演示环境有哪些低成本方案](https://github.com/lautung/guigu-oa/issues/69)
> 抓取时间：2026-08-20（各家定价随时改；本文每个数字都附官方页链接，过期请按链接复核）
> 来源：各平台官方定价页与官方文档；Spring Boot 官方参考文档；GitHub Actions 官方文档；本仓库源码与 jar 内 Flowable 8.0.0 建表脚本。
> 说明：只报官方页面上的数字与官方文档写明的行为。凡是官方没写、只能自己测的（冷启动秒数、境内延迟），本文一律标成「无一手数据」并给测法，不引用二手博客的转述。

---

## 结论

**没有一家平台的免费档能一个人扛下「JVM 常驻 + MySQL 8 + Redis + 静态前端」全套。** 能白嫖的路子只有两条：

1. **一台免费的整机**，自己在上面跑 docker compose —— 目前只有 **Oracle Cloud Always Free**（Ampere A1，2 OCPU / 12 GB，另送一套 2 ECPU / 8 GB / 50 GB 的托管 MySQL HeatWave）。代价是绑卡、A1 常抢不到容量、**闲置会被回收**、没有中国大陆区域。
2. **把三件事拆到三家的免费档**：应用放会休眠的免费 PaaS（Render 免费 Web Service，512 MB / 0.1 CPU，15 分钟无流量休眠、唤醒约 1 分钟），MySQL 放 **Aiven 免费档**（1 CPU / 1 GB RAM / 1 GB 盘，不用信用卡），Redis 放 **Upstash 免费档**（256 MB / 50 万命令每月）。代价是面试官点开要等唤醒 + Spring Boot 冷启动。

2026 年几个**已经变了**的事实，容易踩：

| 平台 | 变化 |
|------|------|
| Fly.io | 2024-10-07 起**对新用户不再有任何免费额度**，所有组织必须绑卡，按秒计费 |
| Hugging Face Spaces | **Gradio / Docker Space 需要付费计划**（个人 PRO $9/月），只有 Static Space 对所有人免费 |
| Koyeb | 官方定价页只剩 Pro **$29/月**起，页面上已无免费实例档 |
| Railway | 免费档每月只有 **$1** 额度；按 RAM $10/GB/月 算 = 0.1 GB·月，跑不动 JVM |
| PlanetScale | **没有免费档**；MySQL(Vitess) 最低 $39/月，Postgres 最低 $5/月 |
| Oracle Always Free | A1 额度按官方文档现在写的是 **1,500 OCPU 小时 + 9,000 GB 小时/月**，对 Always Free 租户等价 **2 OCPU / 12 GB**（不是老文章里的 4 OCPU / 24 GB） |
| Zeabur | 已改成「订阅 + 自带/购买服务器」模式，Free 档只给面板功能（1 台可纳管服务器、48h 日志），不再是「免费跑一个服务」 |

推荐见 §7，三个方案分别是 **Oracle Always Free 单机 compose（$0）**、**Render+Aiven+Upstash 拼装（$0，会休眠）**、**Fly.io 1 GB 常驻 + 外挂免费库（约 $6/月，不休眠）**。

---

## 1. 本项目的实际负载（对比的前提）

| 事实 | 值 | 出处 |
|------|----|------|
| 后端可执行 jar | 60 MB | 本仓库 `service-oa/target/service-oa.jar` |
| JDK | 21 | `AGENTS.md` / `pom.xml` |
| Flowable 引擎首启建表数 | **32 张**（30 张 `ACT_*` + `FLW_RU_BATCH` / `FLW_RU_BATCH_PART`） | 本地统计：解包 jar 内 `flowable-*-8.0.0.jar` 的 `org/flowable/**/db/create/*mysql*create*.sql`，去重 `CREATE TABLE` |
| 建表触发条件 | `flowable.database-schema-update: true`，`history-level: full` | `service-oa/src/main/resources/application-dev.yml` |
| 依赖的中间件 | MySQL 8（库名 `guigu-oa`）、Redis（权限 JSON） | 同上 + `AGENTS.md` |
| 前端 | Vite 构建出 `dist/` 静态文件 | `前端代码/guigu-oa-client/vite.config.js` |
| 前端调后端的方式 | `axios.baseURL = import.meta.env.VITE_APP_BASE_API \|\| '/dev-api'`；开发期 Vite 代理把 `/dev-api` **前缀剥掉**再转 8800 | `src/utils/request.js`、`vite.config.js` |

对托管的两个硬约束：

- **后端必须常驻**（Flowable 有定时 job 表与异步执行器，scale-to-zero 会让引擎反复重建连接、重跑 job 获取）。
- **上线时前端不能沿用 `/dev-api`**：Vite 那条代理会 rewrite 掉前缀，生产要么把 `VITE_APP_BASE_API` 直接指到后端根地址（`https://api.xxx/`），要么在网关上配一条同样剥前缀的反代。这是纯配置工作，但每家平台的做法不同，见 §5。

---

## 2. 候选平台：免费档与低价档的具体数字

### 2.1 应用（跑 JVM 常驻进程）

| 平台 | 免费档具体额度 | 是否休眠 | 是否要绑卡 | 免费额度有无时限 |
|------|----------------|----------|-----------|------------------|
| **Render** | 免费 Web Service **512 MB RAM / 0.1 CPU**；每工作区每月 **750 免费实例小时**；Hobby 工作区含 5 GB 出网流量、500 构建分钟 | **会**：15 分钟无入站流量即 spin down，再来请求时**唤醒约 1 分钟**（期间给浏览器一个 loading 页） | 创建免费服务不要求；超出流量/构建分钟且未绑卡则**挂起全部免费服务** | 免费 Web Service 无到期日；免费 Postgres **创建 30 天后过期** |
| **Fly.io** | **无免费额度**（2024-10-07 起停售含免费额度的计划，仅老组织沿用）；`shared-cpu-1x` 1 GB 各地域 **$5.70–$7.42/月**，256 MB **$1.94–$2.53/月** | 可选：`auto_stop_machines`/`min_machines_running`，停机时不计 CPU/RAM，只按 rootfs $0.15/GB/月 计 | **要**：除 Linked Organization 外所有组织都必须有信用卡（或预充 ≥$25 credit） | 不适用 |
| **Railway** | Free 档 **$1 额度/月**（不滚存），单服务上限 0.5 GB RAM / 1 vCPU；Trial 一次性 $5、30 天 | 不休眠，但额度用完即停 | Trial 不用卡；Hobby $5/月必付 | Trial 30 天；Free 每月重置 |
| **Koyeb** | 官方定价页（2026-08-20）**只列 Pro $29/月起**（含 $10 compute），未见免费实例档 | — | — | — |
| **Zeabur** | Free **$0/月**：可纳管**自有**服务器 1 台、构建机 2C4G、日志留存 48h。运行时资源来自你自己买/自带的服务器 | — | — | Dev $5/月、Pro $19/月各有 14 天试用 |
| **Hugging Face Spaces** | CPU Basic 硬件（**2 vCPU / 16 GB / 50 GB 非持久盘**）本身 $0/小时，**但 Gradio/Docker Space 需要付费计划**（个人 PRO **$9/月**）；Static Space 对所有人免费 | **会**：免费硬件上「a period of time if unused」后 go to sleep（官方未给具体分钟数） | PRO 要付费 | — |
| **Oracle Cloud Always Free** | **A1(Arm)**：每月前 **1,500 OCPU 小时 + 9,000 GB 小时**，Always Free 租户等价 **2 OCPU / 12 GB**，可拆成 1–2 台；**AMD micro** 2 台（1/8 OCPU、1 GB）；块存储合计 **200 GB**；**出网 10 TB/月**；免费 Flexible Load Balancer 10 Mbps | 不休眠，**但闲置会被回收**：7 天内 CPU 95 分位 <20% 且网络 <20%（A1 另加内存 <20%）即判定 idle 可回收 | 注册要卡做身份验证（Always Free 资源本身不扣费） | **无时限**（$300 试用额度是另一回事，30 天） |
| **Google Cloud Run** | 每月免费额度（按 us-central1）：实例计费模式 **240,000 vCPU 秒 + 450,000 GiB 秒**；请求计费模式 **180,000 vCPU 秒 + 360,000 GiB 秒 + 200 万请求** | 默认 scale-to-zero；设 `min-instances≥1` 才「keep warm」，此时请求计费模式下按 idle 价 **$0.0000025/vCPU 秒 + $0.0000025/GiB 秒** 计费 | 要（结算账号） | 免费额度按月重置，无到期 |
| **腾讯云轻量 Lighthouse**（境内） | 无免费常驻档。官方公告表：入门型 **2核2G / 40G SSD / 2 Mbps / 100 GB 流量 = 35 元/月**；产品页 2核2G / 3 Mbps / 200 GB = **45 元/月**（459 元/年） | 不休眠 | 实名 + 付费 | — |
| **Sealos 公有云** | 按量计费，无常驻免费档。广东（腾讯云）区：CPU **0.017420 元/核·小时**、内存 **0.008786 元/GB·小时**；北京（火山）区 0.017125 / 0.008637 | 暂停即停计算费 | 需充值 | — |

一次成本换算（自算，非官方数字）：Sealos 广东区一个 1C2G 常驻容器 = (0.017420 + 2×0.008786) × 24 × 30 ≈ **25.2 元/月**；Cloud Run 用 `min-instances=1` 保温 1 vCPU / 1 GiB 一整月 ≈ (0.0000025+0.0000025) × 2,592,000 ≈ **$13/月**（再减掉免费额度约 $1.4）。

### 2.2 托管 MySQL 8 与 Redis

| 服务 | 协议 | 免费档 | 关键限制 |
|------|------|--------|----------|
| **Aiven for MySQL** | MySQL | **1 CPU / 1 GB RAM / 1 GB 存储**，单节点，**不要信用卡**，**无时间限制**，含备份与网络费 | 「Service powers off after a period of inactivity」（会先邮件通知）；只有文档支持；官方明说不适合生产 |
| **Aiven for Valkey** | Redis 协议 | 官方 FAQ 写明 Free plan 覆盖 PostgreSQL / MySQL / OpenSearch / Kafka / **Valkey** | 同上 |
| **Upstash Redis** | Redis | **256 MB 数据 + 每月 50 万命令 + 10 GB 带宽**，$0 | 免费档无 IP 白名单、无静态加密、无 SLA |
| **Oracle MySQL HeatWave Always Free** | MySQL | 每租户 1 套：`MySQL.Free` 形状 **2 ECPU / 8 GiB**，**50 GiB 存储**，自动备份保留 1 天 | **总是被升到最新大版本**（不保证停在 8.x）；不支持 HA / 只读副本 / 手动备份 / PITR；无 SLA、不享 Oracle Support |
| **Render Postgres / Key Value** | Postgres / Redis | 免费 Postgres 1 GB，**创建 30 天后过期**（再 14 天宽限后删库）；免费 Key Value **25 MB / 50 连接、纯内存不落盘** | **Render 没有托管 MySQL**，只能自己用 Docker 镜像跑成付费 Private Service |
| **PlanetScale** | MySQL(Vitess) / Postgres | **无免费档**。Vitess 最低 PS-10 **$39/月**（3 节点）；Postgres 单节点最低 PS-5 **$5/月** | 定价页明写按 cluster size + 存储 + 备份 + 出网计费 |
| **Neon / Supabase** | **只有 Postgres** | — | 本项目 MyBatis-Plus + Flowable 用的是 MySQL 方言与 MySQL 建表脚本，**不能直接换 Postgres** |
| **TiDB Cloud Starter** | MySQL 兼容 | 免费额度：**5 GiB 行存 + 5 GiB 列存 + 5,000 万 RU/月**，不要信用卡，每组织最多 5 个 | 强制 TLS；用户名要带实例前缀；**Flowable 官方支持矩阵里没有 TiDB**，`ACT_*` 建表与锁行为未验证，属于额外风险 |

**「和应用挤在同一台机器上跑 docker compose」的代价**：省钱且延迟最低（同 loopback），但

- 内存是硬账：JVM 堆 + Flowable 32 张表的元数据 + MySQL 8 的 `innodb_buffer_pool` 默认 128 MB 起 + Redis + 前端静态服务，**512 MB 的免费档基本不可能**，1 GB 很紧，2 GB 才舒服。Render / Railway / HF 的免费档都拿不到 2 GB。
- 免费 PaaS 大多是**易失文件系统**：Render 免费 Web Service 明确没有持久盘，重启/休眠即丢；Fly 要单独买 volume。数据库放这类机器上=每次重启丢库（对纯演示反而可能是优点，见 §6）。
- 一台机器一个进程模型的平台（Fly Machine、Cloud Run、Render）要把多个进程塞进一个镜像（supervisord/s6），偏离标准 compose，出问题不好查。
- 只有「一台真 VM」（Oracle A1、腾讯云轻量、Sealos 的容器编排）能原样吃 `docker compose up`。

---

## 3. 冷启动：官方没有数据，只能自己测

**先说清楚：Spring Boot 与 Flowable 官方文档都不发布启动耗时基准，更没有「512 MB / 1 GB 容器里 Boot 4.1 + Flowable 8 启动几秒」这种数字。** 本节不编造秒数。

官方写明的、与冷启动相关的事实：

- **Flowable 首启会建 32 张表**（本仓库 jar 内建表脚本统计，见 §1），且 `history-level: full` 会让每个流程/任务事件都写历史表。这是首次启动比后续启动慢的主因。
- **Spring Boot 4 给了两个官方降冷启动手段**：`AOT cache`（需要 **Java 25+**）与 `CDS`（Java 21 用这个）。用法是先解包再做一次训练运行：

  ```bash
  java -Djarmode=tools -jar service-oa.jar extract --destination application
  cd application
  java -XX:ArchiveClassesAtExit=application.jsa -Dspring.context.exit=onRefresh -jar service-oa.jar
  java -XX:SharedArchiveFile=application.jsa -jar service-oa.jar
  ```

  官方强调**必须配合解包形态**使用，否则无效。本项目锁 JDK 21，所以走 CDS 这条；哪天升到 25 再换 AOT cache。

**自己测的办法**（建议在写文档之前先跑一遍，把真实数字填回本票）：

1. `-Dspring.context.exit=onRefresh` 让进程在容器刷新完就退出，配 `/usr/bin/time -v java ...`，得到「纯启动 + 建表」耗时与峰值 RSS。
2. 分两次测：空库（Flowable 建 32 张表）与已有库（只做版本校验），差值就是建表成本。
3. 内存受限场景用 `docker run -m 512m --cpus=0.1` 复现平台规格，配 `-XX:MaxRAMPercentage` 观察是否 OOMKilled。
4. 端到端体验用 `curl -w '%{time_total}'` 打冷的服务首页，把「平台唤醒 + 应用启动」一起量。

**平台侧休眠/唤醒（官方口径）**：

| 平台 | 休眠触发 | 唤醒 |
|------|----------|------|
| Render 免费 Web Service | 15 分钟无 HTTP/WebSocket 入站流量 | 下一个请求触发，**约 1 分钟**（官方原文 “takes about one minute”），期间显示 loading 页；注意这 1 分钟是**平台层**，之后还要加 Spring Boot 自己的启动时间 |
| Fly.io | 由你配：`auto_stop_machines = "stop"` / `"suspend"`；`fly launch` 默认 `stop` + `min_machines_running = 0` | `suspend` 比 `stop` 恢复快（官方原话）；想彻底不休眠就 `auto_stop_machines = "off"` 或 `min_machines_running ≥ 1` |
| Cloud Run | 默认无请求就缩到 0 | 设 `min-instances ≥ 1` 保温；官方明说这是为了 “avoid slow container start times”，但保温实例按 idle 价持续计费；且保温是 **best-effort**，容量紧张时可能掉到配置值以下 |
| Hugging Face Spaces | 免费硬件「a period of time if unused」后 sleep（官方未给分钟数） | 需手动或访问触发；付费硬件可常驻 |
| Oracle A1 / 腾讯云轻量 / Sealos | 不休眠 | 不适用（Oracle 有 §2.1 的闲置回收，那是「删机器」不是「休眠」） |

「保活 keep-alive」的正当性：Render 官方对免费 Web Service 明确写了**会因为「service-initiated traffic」异常偏高而挂起服务**（举例是访问外部数据库、调外部 API）。所以用 cron 每 10 分钟自己 ping 自己这种做法，在 Render 免费档上属于走钢丝。真要不休眠，正确解法是花 $7 升 Starter，或换到 §7 方案 A / C。

---

## 4. 中国大陆访问可达性与备案

**这一节没有一手延迟数据**——没有任何一家平台官方发布「从中国大陆访问的 RTT」。下面只列官方写明的事实与地理位置，速度请自测（在境内机器上 `curl -w '%{time_connect} %{time_total}'` 打候选域名，或用国内测速平台跑一遍）。

| 候选 | 节点位置 | 备案 | 可达性说明 |
|------|----------|------|-----------|
| Oracle Always Free | Commercial realm，**无中国大陆区域**；亚太有东京/大阪/新加坡/首尔等 | 不需要 | 走国际出口，无 CDN，实测为准 |
| Render | 官方列 Global regions & CDN，无中国大陆区域 | 不需要 | 同上 |
| Fly.io | 定价页列出的地域含 Tokyo(nrt)、Singapore(sin)、Sydney(syd)、Mumbai(bom)，**无中国大陆** | 不需要 | 同上 |
| Cloud Run | 有 **Hong Kong (asia-east2)**、Taiwan、Tokyo、Singapore 等 | 香港节点不需要 | 香港对境内通常较近，但仍无官方数字 |
| 腾讯云轻量（境内地域：上海/北京/广州/成都/南京） | 中国大陆 | **必须备案**：腾讯云官方文档原文「所有对中国大陆境内提供服务的网站都必须先进行 ICP 备案」 | 境内直连最快 |
| 腾讯云轻量（香港/海外地域） | 境外 | 不需要备案 | 折中选择 |
| Sealos（杭州/北京/广东可用区） | 中国大陆 | 用自有域名对外提供网站服务同样受 ICP 备案约束；平台自带二级域名是否已备案**未核实**，上线前需向平台确认 | 境内直连 |

备案本身的门槛（腾讯云官方文档）：域名须在有效期内、完成实名认证且实名信息与备案主体一致、**境外注册商注册的域名不能直接备案**（要先转入境内有资质的服务商）、实名认证完成后建议等约 3 天再提交。对一个「挂着给面试官点」的 demo 来说，这是几天到几周的行政成本 + 必须用真实身份证/营业执照。

**给面试场景的取舍**：如果面试官在境内且你不想备案，**香港/新加坡/东京节点**是唯一现实解；如果愿意备案，腾讯云轻量 35–45 元/月 的体验最好。

---

## 5. Docker 复用度（本项目还没有 Dockerfile）

现状：仓库里**没有任何 Dockerfile / docker-compose.yml**（`find` 全仓无匹配）。所以无论选哪家，第一步工作量都一样：写一个后端镜像（JDK 21 基底 + `service-oa.jar`）、一个前端静态镜像或静态站点、以及 MySQL/Redis 的编排。

| 平台 | 吃标准 Dockerfile | 吃 docker-compose | 平台专有配置 |
|------|------------------|-------------------|-------------|
| Render | **是**。Language 选 Docker，可指定 Dockerfile 路径；官方明说「JVM 语言（Java/Kotlin/Scala）应当用 Docker」，因为原生 runtime 只覆盖 Node/Python/Ruby/Go/Rust/Elixir | 否（一个服务一个镜像） | `render.yaml` Blueprint（可选）；免费档不能挂持久盘 |
| Fly.io | 是 | 否 | `fly.toml`（`[http_service]`、`auto_stop_machines`、`min_machines_running`） |
| Railway | 是 | 否 | 服务级资源上限随计划变 |
| Cloud Run | 是（要求容器监听 `PORT`） | 否 | `--min-instances`、`--memory`、结算模式（instance-based / request-based）选择 |
| Hugging Face Spaces | 是（Docker Space），但**要付费计划**；只放行 80/443/8080 出网端口 | 否 | `README.md` front-matter 里的 `sdk: docker` |
| Oracle A1 / 腾讯云轻量 | 是（就是台 VM，自己装 Docker） | **是，原样跑** | 无（要自己配安全组/防火墙、TLS 证书） |
| Sealos | 是（部署容器镜像） | 部分（平台提供数据库与应用编排，不是直接吃 compose 文件） | 平台自己的应用/数据库面板 |

一个容易漏的点：**前端的 `/dev-api` 前缀**。Vite 那条代理会把前缀 rewrite 掉，所以生产环境要么构建时把 `VITE_APP_BASE_API` 设成后端根 URL，要么在 Nginx/Caddy 上写一条同样 `rewrite ^/dev-api/(.*) /$1` 的规则。选「前后端同域」（同一台机器上 Caddy 反代）能顺手解决 CORS 与 Cookie 问题；选「静态站点 + 独立后端域名」则要额外配后端 CORS。

---

## 6. 演示数据重置（面试官会乱点）

四种做法与代价：

| 做法 | 怎么做 | 代价 |
|------|--------|------|
| **定时重跑种子 SQL** | GitHub Actions `on: schedule`（cron，**最短 5 分钟一次**）跑 `mysql -h ... < sql/guigu-oa-init.sql` | 要把数据库暴露到公网 + 凭据放 repo secrets（Aiven/TiDB 本来就是公网 TLS，Oracle 上要开安全组，风险自负）。官方另有三条硬约束：高峰期 **schedule 可能被延迟甚至丢弃**；只能跑在默认分支；**公共仓库 60 天无活动会自动停用定时工作流** |
| **平台侧 cron** | Render Cron Job（Starter 档 $0.00016/分钟，**不在免费档内**）；Oracle/腾讯云上直接用 systemd timer 或 crontab | 平台 cron 要额外付费；VM 上的 cron 免费但要自己运维 |
| **容器定期重建** | VM 上 `docker compose down -v && docker compose up -d`，让 MySQL 从初始化脚本重建 | 最干净，但每次重建后 **Flowable 要重新建那 32 张表**，冷启动最慢的一次会落在重建后第一个访客头上。缓解办法：重建后立刻自己 warm 一次 |
| **只读演示账号** | 本项目权限走「配置权限决定侧栏与占用」这条 seam（见 `CONTEXT.md` 与 #61/#63 系列），做一个只挂查询类菜单的角色最省事 | 审批 demo 的核心就是「发起→待处理→通过」，全只读等于没得玩。更实际的是「只读账号 + 一个可写的沙箱账号 + 每日重置」组合 |

**重置一定要连 `ACT_*` 一起清**：`sql/guigu-oa-init.sql` 只建业务 `sys_*`/`oa_*` 表，Flowable 的 32 张表是引擎首启自建的。只重放业务 SQL 而不清引擎表，会留下一堆孤儿流程实例，`oa_process.process_instance_id` 对不上，待处理列表会出现「查得到实例查不到业务」的脏状态。最省心的是 `DROP DATABASE` + 重建 + 重放种子 SQL + 让 `flowable.database-schema-update: true` 重新建表。

另注：`AGENTS.md` 已记录 `/admin/process/findStarted`（已发起）有既有 MyBatis 绑定缺陷，`findPending`/`findProcessed` 正常。上 demo 前要么修，要么在演示引导里绕开这个入口。

---

## 7. 三个具体推荐

### 方案 A — Oracle Cloud Always Free 单机 compose（**$0/月**，首选）

**部署形态**：申请一台 `VM.Standard.A1.Flex`（**2 OCPU / 12 GB**，Always Free 额度全给它，boot volume 50 GB），装 Docker，一份 `docker-compose.yml` 起四个容器：`service-oa`（JDK 21 + jar）、`mysql:8`、`redis`、`caddy`（同时托管前端 `dist/` 并把 `/dev-api/*` 反代剥前缀到 8800，顺便自动签 TLS）。地域选东京或新加坡。
可选变体：MySQL 换成同租户的 **Always Free MySQL HeatWave**（2 ECPU / 8 GiB / 50 GiB），把 12 GB 内存全留给 JVM。

**每月成本**：$0（含 10 TB/月 出网）。
**冷启动体验**：不休眠，常驻，面试官点开即用；只有你手动重启或数据重置后那一次要等 Spring Boot + 建表。
**上手工作量**：中偏高（半天到一天）。要写 Dockerfile/compose、开 VCN 安全组与 iptables、配域名与证书、写重置脚本。
**主要缺点**：
- 注册要绑卡做身份验证；A1 形状经常报 “out of host capacity”（官方文档单独提示了这一条，建议换可用域或稍后重试）。
- **闲置回收**：7 天内 CPU 95 分位 <20% + 网络 <20% + 内存 <20% 会被判 idle 并可能被 Oracle 回收。一个没人访问的 demo 正好符合这个画像。缓解手段（如升级成 Pay As You Go 是否豁免）**官方文档没有明确写**，属于未解问题，见 §8。
- 无中国大陆节点、无 SLA。
- MySQL HeatWave 变体的额外坑：官方写明 Always Free DB system「总是被创建/升级到最新版本」，你控制不了它停在 MySQL 8.x。

### 方案 B — Render 免费 Web Service + Aiven 免费 MySQL + Upstash 免费 Redis（**$0/月**，会休眠）

**部署形态**：后端做成 Docker Web Service 跑在 Render 免费档（512 MB / 0.1 CPU）；前端 `dist/` 放 Render Static Site（免费）；MySQL 用 Aiven 免费档（1 CPU / 1 GB / 1 GB）；Redis 用 Upstash 免费档（256 MB / 50 万命令）。三家都选同一大区（如新加坡）以压低跨网延迟。

**每月成本**：$0。注意配额：Render 每月 750 免费实例小时、5 GB 出网、500 构建分钟。
**冷启动体验**：**最差的一环**。15 分钟没人访问就休眠，唤醒平台层约 1 分钟，再叠加 Spring Boot 启动。面试官点开等 1 分半以上是常态，很可能直接关掉。缓解只能靠「面试前 10 分钟自己先点一下」——因为 Render 明确会因异常的 service-initiated 流量挂起免费服务，自动 keep-alive 不安全。
**上手工作量**：低（一两个小时）。三家都是控制台点选 + 一个 Dockerfile，不用碰 Linux。
**主要缺点**：512 MB / 0.1 CPU 对 Boot 4.1 + Flowable 8 非常紧，建表那次尤其容易超时或 OOM（必须先按 §3 实测）；Aiven 免费实例**长期闲置会被断电**；Upstash 免费档无 IP 白名单；数据库在别家，重置脚本要走公网。

### 方案 C — Fly.io 1 GB 常驻 + 外挂免费库（**约 $6/月**，不休眠、不备案）

**部署形态**：Fly 上一台 `shared-cpu-1x` **1 GB** Machine 跑后端镜像（镜像里顺手用 Caddy/Spring 静态资源托管前端 `dist/`，前后端同域，省掉 CORS），`fly.toml` 里 `auto_stop_machines = "off"`；MySQL 用 Aiven 免费档、Redis 用 Upstash 免费档，region 与 Fly 的 `nrt`/`sin` 对齐。

**每月成本**：**$5.70–$7.42**（1 GB `shared-cpu-1x` 的地域价区间）+ 出网流量。若把前端拆到静态托管、后端降到 512 MB，可压到 $3.19–$4.16，但内存风险变大。
**冷启动体验**：常驻不休眠，点开即用。
**上手工作量**：低到中（两三个小时）。`fly launch` 读 Dockerfile 自动生成 `fly.toml`，主要工作是把 DB/Redis 连接串写成 secrets。
**主要缺点**：**必须绑卡且没有任何免费额度**，忘了删机器就一直扣；数据库在第三方，跨服务商网络延迟不可控（务必同区）；同样无中国大陆节点。

**国内变体（面试官在境内且你愿意备案）**：腾讯云轻量入门型 2核2G，**35–45 元/月**，一台机器跑完整 compose，体验等价于方案 A 但延迟最好；代价是 ICP 备案（真实身份、域名须境内注册商、周期以天/周计）。不想备案就选香港地域的轻量。

**一句话选型**：能接受折腾 Linux → **A**；只想点点鼠标且能接受慢 → **B**；愿意每月花一杯咖啡换「点开就有」→ **C**。

---

## 8. 没查到确切答案的问题

1. **Boot 4.1 + Flowable 8 在 512 MB / 1 GB 下的真实启动秒数与峰值 RSS**：官方零基准，本文拒绝给数字。必须按 §3 的四步自测，测完把结果回填到本票。
2. **Oracle 闲置回收能否通过升级 Pay As You Go 规避**：官方 Always Free 文档只写了回收判据，没写「付费账户下的 Always Free 实例是否豁免」。上线前建议先小规模验证，或干脆让 demo 有真实访问量。
3. **Hugging Face 免费 Space 的具体休眠分钟数**：官方文档只写 “a period of time if unused”，没有数字（且本项目用不上，因为 Docker Space 已要付费计划）。
4. **Sealos 国内可用区自带二级域名是否已备案**：平台文档未说明，需要向 Sealos 确认后才能判断「不用自己备案」是否成立。
5. **Zeabur 现在的服务器实际售价**：定价页只列订阅档（Free/Dev $5/Pro $19/Team $79）与「可纳管服务器台数」，运行时机器的价格要进控制台才看得到，本文未取到。
6. **托管 MySQL 是否允许开 `event_scheduler`**（用库内 EVENT 做定时重置的前提）：Aiven / Oracle HeatWave 文档均未在本次调研范围内确认。
7. **各候选从中国大陆访问的真实延迟**：无任何一手数据，只能自测。

---

## 来源索引

1. Render 定价（免费/Starter 规格、Postgres、Key Value、带宽、构建分钟）：<https://render.com/pricing>
2. Render 免费档行为（15 分钟休眠、约 1 分钟唤醒、750 实例小时、免费 Postgres 30 天过期、service-initiated 流量挂起、免费档无持久盘）：<https://render.com/docs/free>
3. Render Docker 支持（JVM 语言应走 Docker）：<https://render.com/docs/docker>
4. Fly.io 资源定价（`shared-cpu-1x` 各地域价、所有组织需信用卡）：<https://fly.io/docs/about/pricing/>
5. Fly.io 计费（停机按 rootfs $0.15/GB/月、信用卡与 credit 规则）：<https://fly.io/docs/about/billing/>
6. Fly.io 已停售计划与遗留免费额度（2024-10-07 起）：<https://fly.io/docs/about/discontinued-plans/>
7. Fly.io autostop/autostart（`auto_stop_machines`、`min_machines_running`、`fly launch` 默认值）：<https://fly.io/docs/launch/autostop-autostart/>
8. Railway 计划与资源单价（Free 0.5 GB、RAM $10/GB/月、Hobby $5 含 $5 用量）：<https://docs.railway.com/reference/pricing/plans>
9. Railway 免费试用（一次性 $5 / 30 天，之后 Free 每月 $1）：<https://docs.railway.com/reference/pricing/free-trial>
10. Koyeb 定价（Pro $29/月起，页面无免费实例档）：<https://www.koyeb.com/pricing>
11. Zeabur 订阅档：<https://zeabur.com/pricing> 与 <https://zeabur.com/docs/billing/pricing>
12. Hugging Face Spaces（CPU Basic 2 vCPU/16 GB、Gradio/Docker Space 需付费计划、免费硬件会 sleep、出网仅 80/443/8080）：<https://huggingface.co/docs/hub/spaces-overview>
13. Oracle Always Free 资源（A1 1,500 OCPU 小时 + 9,000 GB 小时 = 2 OCPU/12 GB、AMD micro ×2、200 GB 块存储、10 TB 出网、闲置回收判据、out of host capacity 提示、MySQL HeatWave 50 GB）：<https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm>
14. Oracle MySQL HeatWave Always Free（`MySQL.Free` 2 ECPU/8 GiB、50 GiB、总是最新版本、无 HA/PITR/支持）：<https://docs.oracle.com/en-us/iaas/mysql-database/doc/features-mysql-heatwave-service.html>、<https://docs.oracle.com/en-us/iaas/mysql-database/doc/supported-shapes.html>、<https://docs.oracle.com/en-us/iaas/mysql-database/doc/creating-always-free-db-system.html>
15. Cloud Run 定价（免费额度、idle 价、instance/request 计费模式）：<https://cloud.google.com/run/pricing>
16. Cloud Run 最小实例（保温、best-effort、计费差异）：<https://cloud.google.com/run/docs/configuring/min-instances>
17. Aiven 免费 MySQL（1 CPU/1 GB/1 GB、不要信用卡、无时限、闲置断电）：<https://aiven.io/free-mysql-database>；Free plan 覆盖 MySQL/Valkey 等：<https://aiven.io/pricing>
18. Upstash Redis 免费档（256 MB、50 万命令/月、10 GB 带宽）：<https://upstash.com/pricing/redis>
19. PlanetScale 定价（无免费档、Vitess 最低 $39、Postgres 最低 $5）：<https://planetscale.com/pricing>
20. TiDB Cloud Starter 免费额度（5 GiB 行存 + 5 GiB 列存 + 5,000 万 RU、无需信用卡、强制 TLS、用户名前缀）：<https://docs.pingcap.com/tidbcloud/select-cluster-tier/>
21. 腾讯云轻量应用服务器：产品概述含「所有对中国大陆境内提供服务的网站都必须先进行 ICP 备案」：<https://cloud.tencent.com/document/product/1207/44361>；入门型套餐价格公告（2核2G/2Mbps/100GB = 35 元/月）：<https://cloud.tencent.com/document/product/1207/118332>；产品页套餐价（2核2G/3Mbps/200GB = 45 元/月）：<https://cloud.tencent.com/product/lighthouse>
22. 腾讯云 ICP 备案域名要求（实名一致、境外注册商域名不可直接备案、实名后等约 3 天）：<https://cloud.tencent.com/document/product/243/18905>
23. Sealos 计价标准（各可用区 CPU/内存/存储/网络单价）：<https://sealos.run/docs/billing>
24. Spring Boot AOT cache 与 CDS（Java 25+ 用 AOT cache，Java 21 用 CDS；须配合 `-Djarmode=tools extract`）：<https://docs.spring.io/spring-boot/reference/packaging/aot-cache.html>
25. GitHub Actions `schedule`（最短 5 分钟、高峰延迟或丢弃、仅默认分支、公共仓库 60 天无活动自动停用）：<https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows>
26. 本仓库：`service-oa/target/service-oa.jar`（60 MB）、`application-dev.yml`、`sql/guigu-oa-init.sql`、`前端代码/guigu-oa-client/vite.config.js`、`src/utils/request.js`、`AGENTS.md`；Flowable 8.0.0 建表脚本 32 张表为本地解包统计
