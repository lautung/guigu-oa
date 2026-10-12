# 可观测性

## 健康探活

后端通过 Spring Boot Actuator 暴露 `GET /actuator/health`，不需要业务 `token`。本地默认地址是：

```bash
curl --fail http://localhost:8800/actuator/health
```

应用及已配置依赖正常时返回 HTTP 200 和 `UP`；应用或依赖不健康时返回非 2xx 和 `DOWN`。响应不包含组件详情。部署探活应请求应用服务的 `/actuator/health` 路径；该路径会检查数据库和 Redis。

只通过 Web 暴露 `health`，不开放 Actuator 的其他端点。

## 结构化日志与请求关联

应用输出 Logstash 格式的 JSON 到标准输出。每个 HTTP 请求会记录方法、路径、状态码和耗时；请求处理期间产生的日志带有根字段 `traceId`。

客户端可以传入 `X-Trace-Id`。1 到 64 位的 ASCII 字母、数字、点、下划线或连字符会原样沿用，并通过同名响应头返回；未传或格式不合法时，服务端生成 UUID 并在响应头返回。可用这个值关联响应与同一请求的日志。

示例：

```bash
curl -i http://localhost:8800/actuator/health \
  -H 'X-Trace-Id: interview-health-1'
```

完整 Prometheus 指标采集和商业 APM 不在当前实现范围。
