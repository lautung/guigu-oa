# ZAP 2.17.0 Core 被动 baseline

- 开始：2026-10-29T03:47:30.083Z
- 结束：2026-10-29T03:47:36.833Z
- 目标一（客户端 SPA）：http://localhost:9530
- 目标二（后端匿名面）：http://localhost:8800
- 模式：**被动 only**（逐条 accessUrl / sendRequest；未启用 spider、未导入 OpenAPI、未做主动扫描）

## 目标清单

- http://localhost:9530/
- http://localhost:9530/index.html
- http://localhost:9530/login
- http://localhost:9530/work/pending
- http://localhost:9530/nonexistent-zapmvt05r4z
- http://localhost:9530/dev-api/actuator/health
- http://localhost:9530/dev-api/nonexistent-zapmvt05r4z
- http://localhost:8800/actuator/health
- http://localhost:8800/actuator/health/readiness
- http://localhost:8800/actuator/health/../env
- http://localhost:8800/v3/api-docs
- http://localhost:8800/swagger-ui/index.html
- http://localhost:8800/swagger-ui.html
- http://localhost:8800/admin/system/index/login

## 告警分布

- High：0
- Medium：11
- Low：12
- Informational：6

## 告警明细（同 URL 合并）

| 名称 | 风险 | 置信度 | 方法 | URL | CWE | 次数 |
|---|---|---|---|---|---|---|
| Missing Anti-clickjacking Header | Medium | Medium | GET | http://localhost:9530/index.html | 1021 | 1 |
| Missing Anti-clickjacking Header | Medium | Medium | GET | http://localhost:9530/login | 1021 | 1 |
| Missing Anti-clickjacking Header | Medium | Medium | GET | http://localhost:9530/work/pending | 1021 | 1 |
| Missing Anti-clickjacking Header | Medium | Medium | GET | http://localhost:9530/nonexistent-zapmvt05r4z | 1021 | 1 |
| Missing Anti-clickjacking Header | Medium | Medium | GET | http://localhost:9530/ | 1021 | 1 |
| Server Leaks Version Information via "Server" HTTP Response Header Field | Low | High | GET | http://localhost:9530/dev-api/actuator/health | 497 | 1 |
| Content Security Policy (CSP) Header Not Set | Medium | High | GET | http://localhost:9530/ | 693 | 1 |
| Server Leaks Version Information via "Server" HTTP Response Header Field | Low | High | GET | http://localhost:9530/dev-api/nonexistent-zapmvt05r4z | 497 | 1 |
| Content Security Policy (CSP) Header Not Set | Medium | High | GET | http://localhost:9530/login | 693 | 1 |
| Content Security Policy (CSP) Header Not Set | Medium | High | GET | http://localhost:9530/index.html | 693 | 1 |
| Content Security Policy (CSP) Header Not Set | Medium | High | GET | http://localhost:9530/work/pending | 693 | 1 |
| Content Security Policy (CSP) Header Not Set | Medium | High | GET | http://localhost:9530/nonexistent-zapmvt05r4z | 693 | 1 |
| Modern Web Application | Informational | Medium | GET | http://localhost:9530/ | -1 | 1 |
| Modern Web Application | Informational | Medium | GET | http://localhost:9530/nonexistent-zapmvt05r4z | -1 | 1 |
| Modern Web Application | Informational | Medium | GET | http://localhost:9530/login | -1 | 1 |
| Modern Web Application | Informational | Medium | GET | http://localhost:9530/work/pending | -1 | 1 |
| Modern Web Application | Informational | Medium | GET | http://localhost:9530/index.html | -1 | 1 |
| Server Leaks Version Information via "Server" HTTP Response Header Field | Low | High | GET | http://localhost:9530/login | 497 | 1 |
| Content Security Policy (CSP) Header Not Set | Medium | High | GET | http://localhost:8800/swagger-ui/index.html | 693 | 1 |
| Server Leaks Version Information via "Server" HTTP Response Header Field | Low | High | GET | http://localhost:9530/nonexistent-zapmvt05r4z | 497 | 1 |
| Server Leaks Version Information via "Server" HTTP Response Header Field | Low | High | GET | http://localhost:9530/ | 497 | 1 |
| X-Content-Type-Options Header Missing | Low | Medium | GET | http://localhost:9530/nonexistent-zapmvt05r4z | 693 | 1 |
| Modern Web Application | Informational | Medium | GET | http://localhost:8800/swagger-ui/index.html | -1 | 1 |
| X-Content-Type-Options Header Missing | Low | Medium | GET | http://localhost:9530/login | 693 | 1 |
| Server Leaks Version Information via "Server" HTTP Response Header Field | Low | High | GET | http://localhost:9530/work/pending | 497 | 1 |
| Server Leaks Version Information via "Server" HTTP Response Header Field | Low | High | GET | http://localhost:9530/index.html | 497 | 1 |
| X-Content-Type-Options Header Missing | Low | Medium | GET | http://localhost:9530/ | 693 | 1 |
| X-Content-Type-Options Header Missing | Low | Medium | GET | http://localhost:9530/index.html | 693 | 1 |
| X-Content-Type-Options Header Missing | Low | Medium | GET | http://localhost:9530/work/pending | 693 | 1 |

## 重点证据

### Missing Anti-clickjacking Header（Medium）

- URL：http://localhost:9530/index.html
- CWE：1021 / WASC：-
- 证据：`（见完整报告）`
- 描述：The response does not protect against 'ClickJacking' attacks. It should include either Content-Security-Policy with 'frame-ancestors' directive or X-Frame-Options.

### Missing Anti-clickjacking Header（Medium）

- URL：http://localhost:9530/login
- CWE：1021 / WASC：-
- 证据：`（见完整报告）`
- 描述：The response does not protect against 'ClickJacking' attacks. It should include either Content-Security-Policy with 'frame-ancestors' directive or X-Frame-Options.

### Missing Anti-clickjacking Header（Medium）

- URL：http://localhost:9530/work/pending
- CWE：1021 / WASC：-
- 证据：`（见完整报告）`
- 描述：The response does not protect against 'ClickJacking' attacks. It should include either Content-Security-Policy with 'frame-ancestors' directive or X-Frame-Options.

### Missing Anti-clickjacking Header（Medium）

- URL：http://localhost:9530/nonexistent-zapmvt05r4z
- CWE：1021 / WASC：-
- 证据：`（见完整报告）`
- 描述：The response does not protect against 'ClickJacking' attacks. It should include either Content-Security-Policy with 'frame-ancestors' directive or X-Frame-Options.

### Missing Anti-clickjacking Header（Medium）

- URL：http://localhost:9530/
- CWE：1021 / WASC：-
- 证据：`（见完整报告）`
- 描述：The response does not protect against 'ClickJacking' attacks. It should include either Content-Security-Policy with 'frame-ancestors' directive or X-Frame-Options.

### Content Security Policy (CSP) Header Not Set（Medium）

- URL：http://localhost:9530/
- CWE：693 / WASC：-
- 证据：`（见完整报告）`
- 描述：Content Security Policy (CSP) is an added layer of security that helps to detect and mitigate certain types of attacks, including Cross Site Scripting (XSS) and data injection attacks. These attacks are used for everything from data theft to site defacement or distribution of malware. CSP provides a set of standard HTTP headers that allow website owners to declare approved sources of content that 

### Content Security Policy (CSP) Header Not Set（Medium）

- URL：http://localhost:9530/login
- CWE：693 / WASC：-
- 证据：`（见完整报告）`
- 描述：Content Security Policy (CSP) is an added layer of security that helps to detect and mitigate certain types of attacks, including Cross Site Scripting (XSS) and data injection attacks. These attacks are used for everything from data theft to site defacement or distribution of malware. CSP provides a set of standard HTTP headers that allow website owners to declare approved sources of content that 

### Content Security Policy (CSP) Header Not Set（Medium）

- URL：http://localhost:9530/index.html
- CWE：693 / WASC：-
- 证据：`（见完整报告）`
- 描述：Content Security Policy (CSP) is an added layer of security that helps to detect and mitigate certain types of attacks, including Cross Site Scripting (XSS) and data injection attacks. These attacks are used for everything from data theft to site defacement or distribution of malware. CSP provides a set of standard HTTP headers that allow website owners to declare approved sources of content that 

### Content Security Policy (CSP) Header Not Set（Medium）

- URL：http://localhost:9530/work/pending
- CWE：693 / WASC：-
- 证据：`（见完整报告）`
- 描述：Content Security Policy (CSP) is an added layer of security that helps to detect and mitigate certain types of attacks, including Cross Site Scripting (XSS) and data injection attacks. These attacks are used for everything from data theft to site defacement or distribution of malware. CSP provides a set of standard HTTP headers that allow website owners to declare approved sources of content that 

### Content Security Policy (CSP) Header Not Set（Medium）

- URL：http://localhost:9530/nonexistent-zapmvt05r4z
- CWE：693 / WASC：-
- 证据：`（见完整报告）`
- 描述：Content Security Policy (CSP) is an added layer of security that helps to detect and mitigate certain types of attacks, including Cross Site Scripting (XSS) and data injection attacks. These attacks are used for everything from data theft to site defacement or distribution of malware. CSP provides a set of standard HTTP headers that allow website owners to declare approved sources of content that 

### Content Security Policy (CSP) Header Not Set（Medium）

- URL：http://localhost:8800/swagger-ui/index.html
- CWE：693 / WASC：-
- 证据：`（见完整报告）`
- 描述：Content Security Policy (CSP) is an added layer of security that helps to detect and mitigate certain types of attacks, including Cross Site Scripting (XSS) and data injection attacks. These attacks are used for everything from data theft to site defacement or distribution of malware. CSP provides a set of standard HTTP headers that allow website owners to declare approved sources of content that 

## 说明

- 被动扫描不会攻击目标，只分析已发出的请求/响应。
- 完整 HTML/JSON 报告留在本机执行目录（见 execution 文档），不入仓。
- 匿名面探测里的穿越尝试：http://localhost:8800/actuator/health/../env

