# OpenAPI 与 API Collection

开发/演示环境中，每个服务暴露独立分组，Gateway Swagger UI 可以切换五组：

| 分组 | URL | 快照 |
|---|---|---|
| gateway | `/v3/api-docs/gateway` | `docs/api/openapi/gateway.json` |
| identity | Gateway `/openapi/identity`；直连 `/v3/api-docs/identity` | `docs/api/openapi/identity.json` |
| core | Gateway `/openapi/core`；直连 `/v3/api-docs/core` | `docs/api/openapi/core.json` |
| infra | Gateway `/openapi/infra`；直连 `/v3/api-docs/infra` | `docs/api/openapi/infra.json` |
| ai | Gateway `/openapi/ai`；直连 `/v3/api-docs/ai` | `docs/api/openapi/ai.json` |

启动五服务后重新导出并规范化 JSON：

```bash
scripts/export-openapi
```

脚本对每份文档检查 `openapi`、`info.title` 和 `paths`。prod profile 关闭 API Docs、Swagger UI 和四个下游文档代理路由。

原生 [Bruno Collection](bruno) 覆盖主演示流程，并带响应断言、运行时变量、异步通知/RAG/导出轮询和本地上传 fixture。五服务启动后执行：

```bash
read -rsp 'Demo password: ' DEMO_PASSWORD
export DEMO_PASSWORD
scripts/test-bruno
unset DEMO_PASSWORD
```

脚本默认通过 `npx` 使用已实测的 Bruno CLI 3.5.1；设置 `BRUNO_USE_INSTALLED=true` 可使用本机 `bru`。2026-07-13 的真实环境执行结果为 30/30 requests、30/30 tests，覆盖 Direct LLM、SSE、RAG 和 CSV 下载。密码只从进程环境读取，Token 与 ID 只存在于本次 Collection runtime。

可导入的 [Postman Collection](liteworkflow.postman_collection.json) 适合手工讲解。导入后设置唯一 `runId`、不保存到共享空间的 `demoPassword`，以及 attachment/document 本地路径，按目录顺序执行。Collection 用测试脚本保存 Token 与资源 ID；团队共享前应清空当前变量。

SSE 的真实行为已由 `curl --no-buffer`、`scripts/demo` 和 Bruno CLI 验证；客户端断连取消语义仍由集成测试覆盖。统一 JSON 响应为 `ApiResponse<T>`；文件与导出下载返回二进制流。
