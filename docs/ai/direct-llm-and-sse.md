# Direct LLM 与 SSE

ai-service 通过 Spring AI `ChatClient`/`ChatModel` 连接 OpenAI-compatible 外部 API。dev/demo/prod 没有 Ollama、本地模型或 Fake 回退；test profile 才装配 Fake Model/MockWebServer。

## 配置与失败语义

必填：`LITEWORKFLOW_AI_BASE_URL`、`LITEWORKFLOW_AI_API_KEY`、`LITEWORKFLOW_AI_CHAT_MODEL`。路径、连接/请求超时、重试、输出 Token、并发、流并发与每日请求/Token 配额见 `.env.example`。Gateway 的 `LITEWORKFLOW_GATEWAY_AI_RESPONSE_TIMEOUT` 必须大于 ai-service 请求超时，默认分别为 75 秒和 60 秒。

429 和可重试 5xx 使用受控退避；上游不可用/超时映射为明确的 502/504 类业务错误。配置缺失或仍是公开占位值时服务拒绝启动。AI 只返回建议，不直接写 Workspace/Project/Issue 表。usage 记录模型、Token、耗时与状态，不记录完整内容。

## API

- `POST /api/v1/ai/assist`
- `POST /api/v1/ai/issues/generate`
- `POST /api/v1/ai/issues/{issueId}/breakdown`
- `POST /api/v1/ai/issues/{issueId}/summarize`
- `POST /api/v1/ai/projects/{projectId}/weekly-report`
- `POST /api/v1/ai/assist/stream`

结构化输出经过 JSON/DTO/Bean Validation；解析失败返回错误，不返回半对象。

## SSE 契约

流式接口返回 `text/event-stream`，事件顺序为 `context`、零到多个 `delta`、可选 `usage`，最后恰好一个 `done` 或 `error`。Gateway 对该路由禁用响应缓存和超时，并设置 `X-Accel-Buffering: no`。客户端应把 `done/error` 当作应用层终止信号并关闭连接；客户端提前取消也会取消上游订阅并释放并发许可。

```bash
curl --no-buffer --request POST http://127.0.0.1:8080/api/v1/ai/assist/stream \
  --header "Authorization: Bearer $ACCESS_TOKEN" \
  --header 'Content-Type: application/json' \
  --data '{"workspaceId":"...","projectId":"...","message":"Break this work down"}'
```

实现使用 Spring AI 流接口，不包含供应商通用 chunk parser。日志只能记录 requestId、模型、耗时、Token 和终止状态。
