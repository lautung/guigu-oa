# ZAP 2.17.0 Core 被动 baseline

- 开始：2026-10-29T10:53:59.899Z
- 结束：2026-10-29T10:54:05.895Z
- 目标一（客户端 SPA）：http://localhost:9530
- 目标二（后端匿名面）：http://localhost:8800
- 模式：**被动 only**（逐条 accessUrl / sendRequest；未启用 spider、未导入 OpenAPI、未做主动扫描）

## 目标清单

- http://localhost:9530/
- http://localhost:9530/index.html
- http://localhost:9530/login
- http://localhost:9530/work/pending
- http://localhost:9530/nonexistent-zapmvtfe8d7
- http://localhost:9530/dev-api/actuator/health
- http://localhost:9530/dev-api/nonexistent-zapmvtfe8d7
- http://localhost:9530/static/index-DjiCNbjl.js
- http://localhost:9530/static/_plugin-vue_export-helper-9ElrwJaG.js
- http://localhost:9530/static/runtime-dom.esm-bundler-CL73xe5l.js
- http://localhost:9530/static/message-DM8UThp6.js
- http://localhost:9530/static/message-box-BENi8Byt.js
- http://localhost:9530/static/user-ivnTWWtV.js
- http://localhost:9530/static/useApi-CROJJdhE-BIVH0U7g.js
- http://localhost:9530/static/devtools-Bpr7ZAVB-B1m6BObO.js
- http://localhost:9530/static/paths-D7QPgCmr.js
- http://localhost:9530/static/constants-Bfmwcq_0.js
- http://localhost:9530/static/config-permission-DLWEciY1.js
- http://localhost:9530/static/router-D6Ug3Cvk.js
- http://localhost:9530/static/navigation-fS3h79p3.js
- http://localhost:9530/static/index-DPUFp6HS.css
- http://localhost:8800/actuator/health
- http://localhost:8800/actuator/health/readiness
- http://localhost:8800/actuator/health/../env
- http://localhost:8800/v3/api-docs
- http://localhost:8800/swagger-ui/index.html
- http://localhost:8800/swagger-ui.html
- http://localhost:8800/admin/system/index/login

## 告警分布

- High：0
- Medium：5
- Low：0
- Informational：11

## 告警明细（同 URL 合并）

| 名称 | 风险 | 置信度 | 方法 | URL | CWE | 次数 |
|---|---|---|---|---|---|---|
| CSP: style-src unsafe-inline | Medium | High | GET | http://localhost:9530/work/pending | 693 | 1 |
| CSP: style-src unsafe-inline | Medium | High | GET | http://localhost:9530/ | 693 | 1 |
| CSP: style-src unsafe-inline | Medium | High | GET | http://localhost:9530/nonexistent-zapmvtfe8d7 | 693 | 1 |
| CSP: style-src unsafe-inline | Medium | High | GET | http://localhost:9530/index.html | 693 | 1 |
| CSP: style-src unsafe-inline | Medium | High | GET | http://localhost:9530/login | 693 | 1 |
| Information Disclosure - Suspicious Comments | Informational | Low | GET | http://localhost:9530/static/runtime-dom.esm-bundler-CL73xe5l.js | 615 | 1 |
| Modern Web Application | Informational | Medium | GET | http://localhost:9530/nonexistent-zapmvtfe8d7 | -1 | 1 |
| Modern Web Application | Informational | Medium | GET | http://localhost:9530/work/pending | -1 | 1 |
| Modern Web Application | Informational | Medium | GET | http://localhost:9530/ | -1 | 1 |
| Modern Web Application | Informational | Medium | GET | http://localhost:9530/index.html | -1 | 1 |
| Modern Web Application | Informational | Medium | GET | http://localhost:9530/login | -1 | 1 |
| Information Disclosure - Suspicious Comments | Informational | Low | GET | http://localhost:9530/static/message-DM8UThp6.js | 615 | 1 |
| Information Disclosure - Suspicious Comments | Informational | Low | GET | http://localhost:9530/static/_plugin-vue_export-helper-9ElrwJaG.js | 615 | 1 |
| Information Disclosure - Suspicious Comments | Informational | Low | GET | http://localhost:9530/static/index-DjiCNbjl.js | 615 | 1 |
| Information Disclosure - Suspicious Comments | Informational | Low | GET | http://localhost:9530/static/devtools-Bpr7ZAVB-B1m6BObO.js | 615 | 1 |
| Information Disclosure - Suspicious Comments | Informational | Low | GET | http://localhost:9530/static/router-D6Ug3Cvk.js | 615 | 1 |

## 重点证据

### CSP: style-src unsafe-inline（Medium）

- URL：http://localhost:9530/work/pending
- CWE：693 / WASC：-
- 证据：`default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob: https://gw.alicdn.com; font-src 'self' data:; connect-src 'self' http://localhost:9000; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'`
- 描述：Content Security Policy (CSP) is an added layer of security that helps to detect and mitigate certain types of attacks. Including (but not limited to) Cross Site Scripting (XSS), and data injection attacks. These attacks are used for everything from data theft to site defacement or distribution of malware. CSP provides a set of standard HTTP headers that allow website owners to declare approved so

### CSP: style-src unsafe-inline（Medium）

- URL：http://localhost:9530/
- CWE：693 / WASC：-
- 证据：`default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob: https://gw.alicdn.com; font-src 'self' data:; connect-src 'self' http://localhost:9000; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'`
- 描述：Content Security Policy (CSP) is an added layer of security that helps to detect and mitigate certain types of attacks. Including (but not limited to) Cross Site Scripting (XSS), and data injection attacks. These attacks are used for everything from data theft to site defacement or distribution of malware. CSP provides a set of standard HTTP headers that allow website owners to declare approved so

### CSP: style-src unsafe-inline（Medium）

- URL：http://localhost:9530/nonexistent-zapmvtfe8d7
- CWE：693 / WASC：-
- 证据：`default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob: https://gw.alicdn.com; font-src 'self' data:; connect-src 'self' http://localhost:9000; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'`
- 描述：Content Security Policy (CSP) is an added layer of security that helps to detect and mitigate certain types of attacks. Including (but not limited to) Cross Site Scripting (XSS), and data injection attacks. These attacks are used for everything from data theft to site defacement or distribution of malware. CSP provides a set of standard HTTP headers that allow website owners to declare approved so

### CSP: style-src unsafe-inline（Medium）

- URL：http://localhost:9530/index.html
- CWE：693 / WASC：-
- 证据：`default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob: https://gw.alicdn.com; font-src 'self' data:; connect-src 'self' http://localhost:9000; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'`
- 描述：Content Security Policy (CSP) is an added layer of security that helps to detect and mitigate certain types of attacks. Including (but not limited to) Cross Site Scripting (XSS), and data injection attacks. These attacks are used for everything from data theft to site defacement or distribution of malware. CSP provides a set of standard HTTP headers that allow website owners to declare approved so

### CSP: style-src unsafe-inline（Medium）

- URL：http://localhost:9530/login
- CWE：693 / WASC：-
- 证据：`default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob: https://gw.alicdn.com; font-src 'self' data:; connect-src 'self' http://localhost:9000; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'`
- 描述：Content Security Policy (CSP) is an added layer of security that helps to detect and mitigate certain types of attacks. Including (but not limited to) Cross Site Scripting (XSS), and data injection attacks. These attacks are used for everything from data theft to site defacement or distribution of malware. CSP provides a set of standard HTTP headers that allow website owners to declare approved so

## 说明

- 被动扫描不会攻击目标，只分析已发出的请求/响应。
- 完整 HTML/JSON 报告留在本机执行目录（见 execution 文档），不入仓。
- 匿名面探测里的穿越尝试：http://localhost:8800/actuator/health/../env

