# Spring Boot 4 线与后端生态坐标

> 研究票：[#24 Spring Boot 4 线与后端生态坐标](https://github.com/lautung/guigu-oa/issues/24)  
> 地图票：[#22 升级到 Spring Boot 4 与 Vue 3 的依赖规格](https://github.com/lautung/guigu-oa/issues/22)  
> 来源：Spring 官方文档 / wiki / 博客、Maven Central、MyBatis-Plus / Knife4j / jjwt / Fastjson2 / Connector/J 第一方文档；仓库坐标来自 `server/guigu-oa-parent` 父 POM 与模块 POM。
> 说明：时点 **2026-08-19**。只报事实与来源，**不锁定**规格采用 Spring Boot 4.0.x 还是 4.1.x。

---

## 1. 仓库现状（对照基线）

父 POM `server/guigu-oa-parent/pom.xml` 当前坐标：

| 属性 / 声明 | 当前值 |
|-------------|--------|
| `spring-boot-starter-parent` | `2.3.6.RELEASE` |
| `java.version` / compiler `source`/`target` | `1.8` / `8` |
| `mybatis-plus.version` | `3.4.1`（GAV：`com.baomidou:mybatis-plus-boot-starter`） |
| `mysql.version` | `8.0.30`（GAV：`mysql:mysql-connector-java`） |
| `knife4j.version` | `3.0.3`（GAV：`com.github.xiaoymin:knife4j-spring-boot-starter`） |
| `jwt.version` | `0.9.1`（GAV：`io.jsonwebtoken:jjwt`） |
| `fastjson.version` | `2.0.21`（GAV：`com.alibaba:fastjson`） |
| Spring Security | 无显式版本；`common/spring-security` 与 `service-util` 使用 `spring-boot-starter-security` |
| `maven-compiler-plugin` | **显式钉死** `3.1`，`<source>1.8</source>` / `<target>1.8</target>` |

`service-oa/pom.xml` 另将 `mybatis-plus-generator` 钉在 `3.4.1`。模块 POM 均把 `maven.compiler.source/target` 设为 `8`。

---

## 2. Spring Boot 4.0.x 与 4.1.x（2026-08 时点）

### 2.1 当前补丁

Maven Central `org.springframework.boot:spring-boot-starter-parent` 的 `maven-metadata.xml`（`lastUpdated=20260625105826`）给出：

- **4.1 线最新 GA：`4.1.0`**（亦为 metadata 的 `latest` / `release`）
- **4.0 线最新 GA：`4.0.7`**
- 下一批尚未 GA：`4.1.1-SNAPSHOT`、`4.0.8-SNAPSHOT`、`4.2.0-SNAPSHOT`（出现在 [docs.spring.io 版本切换器](https://docs.spring.io/spring-boot/reference/) 的 Snapshot 列表，不在 Maven Central release）

与官方发布公告一致：

- 4.0.0 GA：2025-11-20（[Spring Boot 4.0.0 available now](https://spring.io/blog/2025/11/20/spring-boot-4-0-0-available-now)）
- 4.1.0 GA：2026-06-10；该文写明 4.1.0「includes all of the bug fixes, documentation improvements and security fixes from Spring Boot 4.0.7」（[Spring Boot 4.1.0 available now](https://spring.io/blog/2026/06/10/spring-boot-4)）

[GitHub Releases](https://github.com/spring-projects/spring-boot/releases) 同期列出的 4.x GA 标签为 `v4.1.0` 与 `v4.0.7`。截至 2026-08-19，**没有** 4.1.1 或 4.0.8 的 GA。

### 2.2 OSS 支持窗口

第一方政策来自 [Supported Versions wiki](https://github.com/spring-projects/spring-boot/wiki/Supported-Versions)（页面仍写「Spring Boot follows the VMware Tanzu OSS support policy」）：

- **Minor：至少 12 个月** OSS
- **Major：自该 major 发布日起至少 3 年**，但「you must run a supported minor version」
- 节奏：每六个月一个 major/minor（目标 5 月与 11 月）
- 「As much as possible, we recommend that all users migrate to the latest supported release.」

据此用 **GA 日 + 政策下限** 计算（官方未在该 wiki 写死 4.0/4.1 的日历日 EOL）：

| 线 | GA | Minor OSS 下限（≥12 个月） | Major 4.x 下限（≥3 年，且须落在仍支持的 minor 上） |
|----|----|---------------------------|------------------------------------------------------|
| 4.0.x | 2025-11-20 | **至少到 2026-11-20** | 4.x major 至少到 **2028-11-20** |
| 4.1.x | 2026-06-10 | **至少到 2027-06-10** | 同上（须该 minor 仍在 12 个月窗内，或后续 minor 接棒） |

2026-08-19 时点：两条 minor **都仍在**「至少 12 个月」窗内。文档版本切换器把 **4.1.0 与 4.0.7 都标为 Stable**（另有 3.5.16 / 3.4.13 / 3.3.13）。[Spring Boot 3.5.16 公告](https://spring.io/blog/2026/06/25/spring-boot-3-5-16-available-now) 写 3.5 是该代最后一次 OSS 发布，并说「To continue to receive OSS support, upgrade to **4.0.x or 4.1.x**」——官方在此并列两条 4.x 线，没有指定只能升其中一条。

第一方 **没有** 公布「4.0 OSS 止于某月某日」的日历。第三方（如 HeroDevs）给出的 2026-12-31 / 2027-07-31 数字 **不是** Spring 文档或 wiki 原文，本笔记不采用。

### 2.3 系统要求（Java / Jakarta / Jackson）

| 项 | 4.0.7 | 4.1.0 | 来源 |
|----|-------|-------|------|
| Java | **至少 17**，兼容至 **Java 26**（含） | 同左 | [4.0 system requirements](https://docs.spring.io/spring-boot/4.0/system-requirements.html)、[4.1 system requirements](https://docs.spring.io/spring-boot/4.1/system-requirements.html)；未版本化入口 [system-requirements.html](https://docs.spring.io/spring-boot/system-requirements.html) 与 4.1.0 页相同 |
| Spring Framework | **7.0.8+** | **7.0.8+** | 同上 |
| Maven | 3.6.3+ | 3.6.3+ | 同上 |
| Servlet | **6.1**（Tomcat 11.0.x / Jetty 12.1.x） | 同左 | 同上 |
| Jakarta EE | **Jakarta EE 11**，Servlet 6.1 baseline | 同左（4.x 基线） | [Spring Boot 4.0 Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)：「Spring Boot 4 is based on Jakarta EE 11」 |
| Jackson | **首选 Jackson 3**（`tools.jackson.*`，BOM `3.1.4`）；仍管理 Jackson 2（`com.fasterxml.jackson.*` 2.21.4）作过渡 | 同左 Jackson 3.1.4 / 2.21.4 | 迁移指南「Upgrading Jackson」；[4.0 coordinates](https://docs.spring.io/spring-boot/4.0/appendix/dependency-versions/coordinates.html)、[4.1 coordinates](https://docs.spring.io/spring-boot/4.1/appendix/dependency-versions/coordinates.html)；`spring-boot-jackson2` **已 deprecated，将在未来版本移除** |
| 托管 `jakarta.servlet-api` | 6.1.0 | 6.1.0 | 同上 coordinates |
| 4.1 额外下限 | — | 托管 jOOQ 3.20 **需要 Java 21+**（本仓库未用 jOOQ） | [4.1 Release Notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.1-Release-Notes) |

迁移指南原文：「Spring Boot 4.0 requires Java 17 or later. **Using the latest LTS release of Java is encouraged.**」地图票已定 JDK 21，落在该鼓励范围内，且为 4.0/4.1 均声明兼容的版本。

`javax.*` Servlet / JAXB 运行时不是 Boot 4 基线。4.0.0 公告写「First class support for Java 25 (whilst retaining Java 17 compatibility)」；**当前** 4.0.7 / 4.1.0 系统要求页已写到 Java 26。

### 2.4 官方文档更鼓励哪条线（事实，不代规格拍板）

可核对的第一方信号：

1. **未版本化文档站指向 4.1.0**：[`https://docs.spring.io/spring-boot/system-requirements.html`](https://docs.spring.io/spring-boot/system-requirements.html) 正文是「Spring Boot **4.1.0** requires…」。
2. **4.0 文档顶栏横幅**：「For the latest stable version, please use **Spring Boot 4.1.0**!」（见 [4.0 system requirements](https://docs.spring.io/spring-boot/4.0/system-requirements.html) 等多页）。
3. **项目主页** [spring.io/projects/spring-boot](https://spring.io/projects/spring-boot) 标题版本为 **4.1.0**。
4. **Supported Versions wiki**：建议用户迁到 **latest supported release**。
5. **并存事实**：版本切换器仍把 **4.0.7 列为 Stable**；3.5.16 公告允许升到 **4.0.x or 4.1.x**。
6. **从旧 major 过来的官方路径**：[4.0 Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide) 要求先升到 **最新 3.5.x**，再升 **4.0 的 latest maintenance release**；[4.0 Release Notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Release-Notes)：「If you’re currently running with an earlier version of Spring Boot, we strongly recommend that you upgrade to Spring Boot 3.5 before migrating to Spring Boot 4.0。」本仓库在 2.3.6，官方推荐路径是 **2.3 →（经中间代）3.5.x → 4.0.x**；地图票另有「一次跳到 Boot 4」的产品决策，那是规格层选择，不是本票要锁的 4.0 vs 4.1。

**结论（仅事实）**：文档与项目页把 **4.1.0 标成当前 latest stable**，同时 **4.0.7 仍在 OSS Stable 列表**。本票不把其中一条写成规格必选项。

---

## 3. 各坐标：Boot 4 + JDK 21 后继事实

判定口径：**留** = 现 GAV（及当前版本）在 Boot 4 + JDK 21 下仍是官方支持的用法；**必须换** = 官方已改坐标、基线不兼容、或当前版本在 JDK 21 上明确不可运行。版本号凡写「Boot BOM 托管」均随所选 4.x 线变化，本票不锁线。

### 3.1 Spring Boot `2.3.6.RELEASE` → 4.x

| 项 | 事实 |
|----|------|
| 能否留 | **必须换。** 2.3.6 不在当前文档 Stable 列表；Boot 4 下限 Java 17，与仓库 JDK 8 坐标互斥（[4.0/4.1 system requirements](https://docs.spring.io/spring-boot/4.1/system-requirements.html)）。 |
| 后继 GAV | **不变**：`org.springframework.boot:spring-boot-starter-parent`（仍作 parent）。 |
| 后继版本（时点补丁） | **4.1.0** 或 **4.0.7**（见 §2；不锁线）。 |
| 附带 | `java.version` 须 ≥17；地图目标 21 被迁移指南鼓励。Jackson 默认 3、Jakarta EE 11、Servlet 6.1。 |

### 3.2 MyBatis-Plus `3.4.1`（`mybatis-plus-boot-starter`）

| 项 | 事实 |
|----|------|
| 能否留 | **必须换 artifact（及版本）。** 官方安装页把 `mybatis-plus-boot-starter` 标为 **Spring Boot 2**；Boot 3 用 `mybatis-plus-spring-boot3-starter`；**Boot 4 从 3.5.13 起**用 `mybatis-plus-spring-boot4-starter`（[Installation](https://baomidou.com/en/getting-started/install/)）。`3.4.1` 早于 boot4 starter。 |
| 后继 GAV | `com.baomidou:mybatis-plus-spring-boot4-starter`（Boot 4）。 |
| 后继版本 | 官方安装页示例与 Maven Central `release` 均为 **`3.5.17`**（metadata `lastUpdated=20260708151743`）。[v3.5.13 发行说明](https://github.com/baomidou/mybatis-plus/releases/tag/v3.5.13)（2025-08-28）：「feat: 新增 `spring-boot4` 支持」。 |
| JDK | 文档写 MP 3.0 要求 **JDK 8+**；Boot 4 starter 随 Boot 4，即运行面 Java 17+。BOM：`com.baomidou:mybatis-plus-bom`（文档写 3.5.9+）。JDK 11+ 的 jsqlparser 模块为 `mybatis-plus-jsqlparser`。 |
| 同仓库钉死 | `service-oa` 的 `mybatis-plus-generator:3.4.1` 与 starter 同代，升级 starter 时需一并对照 3.5.x generator（本票不展开 generator API）。 |

### 3.3 `mysql:mysql-connector-java:8.0.30`

| 项 | 事实 |
|----|------|
| 能否留 | **必须换 GAV。** Connector/J **8.0.31** 起官方坐标改为 `com.mysql:mysql-connector-j`；旧坐标仅 relocation POM，「switch to the new coordinates as soon as possible, as the old coordinates could be discontinued anytime without notice」（[8.0.31 发行说明](https://dev.mysql.com/doc/relnotes/connector-j/en/news-8-0-31.html)；[Maven 安装](https://dev.mysql.com/doc/connector-j/en/connector-j-installing-maven.html)）。Maven Central 上 `mysql:mysql-connector-java` 最后 `release=8.0.33`（`lastUpdated=20230418211038`），其后不再发实包。 |
| 后继 GAV | `com.mysql:mysql-connector-j`。 |
| 后继版本（两条官方事实，不锁） | **Boot 4.0.7 与 4.1.0 BOM 都托管 `9.7.0`**（coordinates 表；属性 `mysql.version`）。Oracle **当前推荐生产 GA 是 Connector/J 26.7**：「MySQL Connector/J 26.7 supersedes the 9.7 series and is recommended for use on production systems. It is for use with MySQL Server 8.0 and up.」（[Developer Guide](https://dev.mysql.com/doc/connector-j/en/)，文档生成 2026-08-07）。Maven Central `com.mysql:mysql-connector-j` `release=26.7.0`（`lastUpdated=20260729151114`）。 |
| JDK 21 | 8.0.30 旧坐标已停更；新坐标 9.7.0 / 26.7.0 均晚于 Java 9 模块化，与 Boot 4 + JDK 21 无第一方「不可用」声明。若使用 Boot parent 且不覆盖 `mysql.version`，解析到的是 **9.7.0**。 |

### 3.4 Knife4j `3.0.3`（`knife4j-spring-boot-starter`）

| 项 | 事实 |
|----|------|
| 能否留 | **必须换。** `knife4j-spring-boot-starter` 在 Maven Central 的 **最后一版就是 3.0.3**（`lastUpdated=20210628080313`）。官方快速开始：该历史线依赖 **Springfox / OpenAPI2**，Boot 3 起「只支持 OpenAPI3」，「JDK 版本必须 >= 17」（[快速开始](https://doc.xiaominfo.com/docs/quick-start)）。Boot 4 为 Jakarta EE 11 / Servlet 6.1，javax + Springfox 不能作为后继。官方还写：Boot **&lt; 2.4** 才建议 Knife4j 4.0 之前的版本——本仓库 3.0.3 正落在这条旧线上。 |
| 官方文档给出的 Boot 3 后继 | `com.github.xiaoymin:knife4j-openapi3-jakarta-spring-boot-starter`。快速开始示例版本 **4.4.0**；[更新日志](https://doc.xiaominfo.com/docs/changelog) 最新一条为 **4.5.0（2024-01-08）**。Maven Central 该 artifact `release=4.5.0`（`lastUpdated=20240107154929`）。GitHub 模块说明：jakarta starter 用于 **Spring Boot &gt;= 3.0.0-M1**（[xiaoymin/knife4j `dev` 树](https://github.com/xiaoymin/knife4j/tree/dev/knife4j)）。 |
| Boot 4 专用官方 starter | **截至 2026-08-19，`com.github.xiaoymin:knife4j-openapi3-boot4-spring-boot-starter` 在 Maven Central 不存在（404）。** 官方快速开始 **没有 Spring Boot 4 小节**。上游 issue [#972](https://github.com/xiaoymin/knife4j/issues/972)（2025-11 开，2026-03 仍有跟贴）、[#994](https://github.com/xiaoymin/knife4j/issues/994)（2026-03，Boot 4.0.2 + knife4j 4.5.0 UI 报错）表明 **xiaoymin 线尚未发布 Boot 4 官方 starter**。 |
| 本票口径 | 后继 **官方坐标** 是 jakarta OpenAPI3 starter **4.5.0**（文档示例仍写 4.4.0）；**是否足以跑 Boot 4 不是本票能从官方文档得到的肯定句**——官方未宣称 Boot 4 支持。社区 fork 不作为第一方后继写入规格坐标。 |

### 3.5 jjwt `0.9.1`（`io.jsonwebtoken:jjwt`）

| 项 | 事实 |
|----|------|
| 能否留 | **必须换（版本 + 推荐模块化 GAV）。** 0.9.1 使用 `javax.xml.bind.DatatypeConverter`，JDK 11+ 默认 classpath 无 JAXB，表现为 `ClassNotFoundException`（[jwtk/jjwt#510](https://github.com/jwtk/jjwt/issues/510)、[#297](https://github.com/jwtk/jjwt/issues/297)）。维护者回复：确定性 Base64 在 **0.10.0** 修复（CHANGELOG 0.10.0：「Deterministic Base64(URL) support on all JDK and Android platforms」；[#297 关闭说明 fixed in 0.10.0](https://github.com/jwtk/jjwt/issues/297)）。这与仓库「必须用 JDK8 才能登录」的运行时现象一致。 |
| 官方安装坐标（README `project-version: 0.13.0`） | JDK 项目：`io.jsonwebtoken:jjwt-api`（compile）+ `jjwt-impl`（runtime）+ `jjwt-jackson`（runtime）。「Fully functional on all **Java 8+** JDKs」。JDK 21 落在该范围内；PS256 等在 JDK 11+ 不需 BouncyCastle（[jwtk/jjwt README](https://github.com/jwtk/jjwt)）。 |
| Maven Central | `jjwt-api` **`0.13.0`**（`lastUpdated=20250820233934`）。同版本仍发布遗留 artifact `io.jsonwebtoken:jjwt`（Central 在 0.12.5 的 POM 描述为 *Legacy Transitive Dependency Jar*，并指向 README Installation）。官方安装章节推荐的是 **拆开的三个模块**，不是继续只声明 `jjwt`。 |
| Boot 4 | jjwt **不是** Spring Boot BOM 托管库；与 Jackson 3 的运行时搭配需自行验证 `jjwt-jackson`（其传递依赖历史上是 Jackson 2）。这是集成事实，不是「不能升」的官方禁令。 |

### 3.6 fastjson `2.0.21`（`com.alibaba:fastjson`）

| 项 | 事实 |
|----|------|
| 能否留 GAV | **兼容模式可以保留 `com.alibaba:fastjson`，但不能停在 2.0.21。** FASTJSON 2 升级指南：兼容模式继续用旧 groupId `com.alibaba` + artifact `fastjson`；新 API 用 `com.alibaba.fastjson2:fastjson2`（[fastjson_1_upgrade_en.md](https://github.com/alibaba/fastjson2/blob/main/docs/fastjson_1_upgrade_en.md)；[README](https://github.com/alibaba/fastjson2/)）。 |
| 后继版本 | Maven Central `com.alibaba:fastjson` **`2.0.64`**（`lastUpdated=20260802104514`）；`com.alibaba.fastjson2:fastjson2` 同期 **`2.0.64`**（另有 `.android*` 变体，Java 项目用无后缀 GA）。README 示例与 Maven Central badge 均为 2.0.64。 |
| JDK 21 | README：「Optimized for **JDK 8/11/17/21**」。 |
| Spring 集成 | 官方提供 `fastjson2-extension-spring5` 与 **`fastjson2-extension-spring6`**（2.0.23+ 起按 Spring 5/6 拆包）。**没有** 名为 spring7 的第一方模块。Boot 4 基于 Spring Framework 7；若要把 Fastjson 接到 MVC HttpMessageConverter，第一方文档只写到 Spring 6 扩展。 |
| 本票口径 | 现坐标 **不是** 1.2.x 那条 CVE 线；2.0.21 → **2.0.64** 是同一 2.x 兼容 artifact 的补丁后继。新 API 后继是 `com.alibaba.fastjson2:fastjson2:2.0.64`。是否继续用 Fastjson 做 HTTP JSON（Boot 4 默认 Jackson 3）属规格选择，本票不锁。 |

### 3.7 Spring Security（随 Boot）

| 项 | 事实 |
|----|------|
| 能否留 artifact | **GAV 可留**：`org.springframework.boot:spring-boot-starter-security`，**不要自己写版本**（[Getting Spring Security](https://docs.spring.io/spring-security/reference/getting-spring-security.html)）。 |
| 随线版本 | Boot **4.0.7** BOM：`spring-security.version` **7.0.6**（[4.0 coordinates](https://docs.spring.io/spring-boot/4.0/appendix/dependency-versions/coordinates.html) / `spring-boot-dependencies-4.0.7.pom`）。Boot **4.1.0** BOM：**7.1.0**（[4.1 coordinates](https://docs.spring.io/spring-boot/4.1/appendix/dependency-versions/coordinates.html)；Security 文档示例亦写 `7.1.0`）。 |
| JDK | Spring Security 7.x：**Java 17+**（[Prerequisites](https://docs.spring.io/spring-security/reference/prerequisites.html)、[7.1 prerequisites](https://docs.spring.io/spring-security/reference/7.1/prerequisites.html)）。JDK 21 满足。 |
| 覆盖方式 | 仅当需要偏离 BOM 时设 `spring-security.version`（Boot [Version Properties](https://docs.spring.io/spring-boot/4.1/appendix/dependency-versions/properties.html)）。当前仓库未钉 Security 版本，升级 parent 后会自动换代。2.3.6 代的 Security 5.x API（`WebSecurityConfigurerAdapter` 等）与 Security 7 不兼容，那是 **代码迁移** 而非坐标能否保留。 |

### 3.8 `maven-compiler-plugin` `3.1`

| 项 | 事实 |
|----|------|
| 能否留 | **必须换。** 父 POM **覆盖** Boot parent 的 pluginManagement，钉死 3.1 且 `source/target=1.8`。Boot 4 要求在 Java 17+ 上构建；Apache 当前稳定文档以 **3.15.0** 为编译 mojo 版本（[maven-compiler-plugin 3.15.0 compile-mojo](https://maven.apache.org/plugins-archives/maven-compiler-plugin-3.15.0/compile-mojo.html)）。该页写明：自 **JDK 21** 起 `proc`（annotation processing）**必须显式设置**。`<release>` 用于 JDK 9+ 的 `--release`（[set-compiler-release](https://maven.apache.org/plugins/maven-compiler-plugin/examples/set-compiler-release.html)）；示例插件版本为 3.15.0。 |
| Boot BOM 托管 | 4.0.7：`maven-compiler-plugin.version` **3.14.1**。4.1.0：**3.15.0**（[4.1 Release Notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.1-Release-Notes) 亦列出 Maven Compiler Plugin 3.15.0）。属性名 `maven-compiler-plugin.version`（[Version Properties](https://docs.spring.io/spring-boot/4.1/appendix/dependency-versions/properties.html)）。 |
| Maven Central | 稳定线最新为 **3.15.0**；`4.0.0-beta-4` 存在但是 beta（`lastUpdated=20260201190625`）。 |
| 后继做法（事实） | 删除对 3.1 的显式 `<version>` 与 1.8 source/target 后，会落到 **所选 Boot 线托管的 3.14.1 或 3.15.0**；`java.version`/`release` 对齐 JDK 21。不必、也不应继续在父 POM 钉一个早于 Boot BOM 的 3.1。 |

---

## 4. 对照总表（Boot 4 + JDK 21）

| 当前坐标 | 判定 | 官方后继（时点事实） |
|----------|------|----------------------|
| Spring Boot `2.3.6.RELEASE` | 必须换版本 | `spring-boot-starter-parent` **4.1.0**（latest stable）或 **4.0.7**（仍为 Stable 维护线） |
| MyBatis-Plus `mybatis-plus-boot-starter:3.4.1` | 必须换 artifact | `mybatis-plus-spring-boot4-starter` **3.5.17**（自 3.5.13） |
| `mysql:mysql-connector-java:8.0.30` | 必须换 GAV | `com.mysql:mysql-connector-j`；Boot BOM **9.7.0**；Oracle 当前推荐 **26.7.0** |
| Knife4j `knife4j-spring-boot-starter:3.0.3` | 必须换 | 官方文档后继：`knife4j-openapi3-jakarta-spring-boot-starter` **4.5.0**（快速开始仍示例 4.4.0）；**无** xiaoymin Boot 4 starter |
| jjwt `jjwt:0.9.1` | 必须换 | `jjwt-api` + `jjwt-impl` + `jjwt-jackson` **0.13.0**（0.10.0 起去掉 DatatypeConverter 路径） |
| fastjson `fastjson:2.0.21` | GAV 可留兼容线，版本必须升；新 API 则换 GAV | 兼容：`com.alibaba:fastjson:2.0.64`；新 API：`com.alibaba.fastjson2:fastjson2:2.0.64` |
| `spring-boot-starter-security` | artifact 可留，勿钉版本 | 随 parent：4.0.7 → Security **7.0.6**；4.1.0 → **7.1.0** |
| `maven-compiler-plugin:3.1` | 必须去掉钉死 | 随 parent：4.0.7 → **3.14.1**；4.1.0 → **3.15.0**（Apache 稳定文档亦为 3.15.0） |

---

## 5. 来源

- Spring Boot：[docs.spring.io/spring-boot](https://docs.spring.io/spring-boot/)、[4.0 / 4.1 system requirements](https://docs.spring.io/spring-boot/4.1/system-requirements.html)、[dependency coordinates 4.0](https://docs.spring.io/spring-boot/4.0/appendix/dependency-versions/coordinates.html) / [4.1](https://docs.spring.io/spring-boot/4.1/appendix/dependency-versions/coordinates.html)、[4.0 Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)、[4.0 Release Notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Release-Notes)、[4.1 Release Notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.1-Release-Notes)、[Supported Versions](https://github.com/spring-projects/spring-boot/wiki/Supported-Versions)、[4.0.0 公告](https://spring.io/blog/2025/11/20/spring-boot-4-0-0-available-now)、[4.1.0 公告](https://spring.io/blog/2026/06/10/spring-boot-4)、[3.5.16 公告](https://spring.io/blog/2026/06/25/spring-boot-3-5-16-available-now)、Maven Central `spring-boot-starter-parent` metadata、`spring-boot-dependencies` 4.0.7 / 4.1.0 POM
- Spring Security：[Getting Spring Security](https://docs.spring.io/spring-security/reference/getting-spring-security.html)、[Prerequisites](https://docs.spring.io/spring-security/reference/prerequisites.html)
- MyBatis-Plus：[Installation](https://baomidou.com/en/getting-started/install/)、[v3.5.13](https://github.com/baomidou/mybatis-plus/releases/tag/v3.5.13)、Maven Central `mybatis-plus-spring-boot4-starter`
- Connector/J：[8.0.31 notes](https://dev.mysql.com/doc/relnotes/connector-j/en/news-8-0-31.html)、[Maven 安装](https://dev.mysql.com/doc/connector-j/en/connector-j-installing-maven.html)、[Developer Guide](https://dev.mysql.com/doc/connector-j/en/)
- Knife4j：[快速开始](https://doc.xiaominfo.com/docs/quick-start)、[更新日志](https://doc.xiaominfo.com/docs/changelog)、[xiaoymin/knife4j#972](https://github.com/xiaoymin/knife4j/issues/972)、[#994](https://github.com/xiaoymin/knife4j/issues/994)
- jjwt：[README](https://github.com/jwtk/jjwt)、[CHANGELOG 0.10.0](https://raw.githubusercontent.com/jwtk/jjwt/master/CHANGELOG.md)、[#510](https://github.com/jwtk/jjwt/issues/510)、[#297](https://github.com/jwtk/jjwt/issues/297)
- Fastjson2：[README](https://github.com/alibaba/fastjson2/)、[1.x 升级指南](https://github.com/alibaba/fastjson2/blob/main/docs/fastjson_1_upgrade_en.md)
- Maven Compiler Plugin：[3.15.0 mojo](https://maven.apache.org/plugins-archives/maven-compiler-plugin-3.15.0/compile-mojo.html)、[--release 示例](https://maven.apache.org/plugins/maven-compiler-plugin/examples/set-compiler-release.html)
- 仓库：`server/guigu-oa-parent/pom.xml` 及 `common/*`、`model`、`service-oa` 模块 POM
