# liteworkflow backend

liteworkflow 是一个 Java 21 / Spring Boot 3 的五服务协作系统 MVP。它覆盖用户与成员管理、Issue/Comment、文件、通知与导出，以及通过外部 API 提供的 Direct LLM、SSE 和增量 RAG。MVP 不包含本地模型，也不依赖 M15–M18 的 Post-MVP 能力。

## 服务与端口

| 服务 | 端口 | 职责 |
|---|---:|---|
| `gateway-service` | 8080 | JWT、路由、CORS、限流、Swagger 聚合 |
| `identity-service` | 8081 | 注册、登录、Token、密码与用户事件 |
| `core-service` | 8082 | 用户目录、Workspace/Project、Issue、Comment、权限 |
| `infra-service` | 8083 | 文件、通知、邮件、CSV/XLSX 导出 |
| `ai-service` | 8084 | Direct LLM、SSE、Embedding 与 RAG |

基础设施由 Docker Compose 启动：PostgreSQL/pgvector、Redis、RabbitMQ、Nacos、SeaweedFS S3 和 MailHog。

## 快速启动

前置条件：Java 21+、Maven 3.8.7+、Docker Compose v2、`curl`、Python 3；运行 Shell 演示还需要 `jq`，运行 Bruno 验收还需要 Node.js/npm（或已安装的 Bruno CLI）。

```bash
cp .env.example .env
# 将 .env 中所有 replace_me_* 替换为本机值；AI/Embedding 必须是真实外部 API 配置

scripts/dev-up
scripts/services-up
scripts/check-services
```

启动顺序固定为：基础设施 → core → identity → infra → ai → gateway。脚本会在启动下一项前等待健康检查。Swagger UI 位于 `http://127.0.0.1:8080/swagger-ui.html`。

运行可重复演示：

```bash
DEMO_PASSWORD='<符合密码策略的临时密码>' scripts/demo
```

脚本每次生成唯一邮箱，覆盖注册、登录、Workspace/Project 加成员、Issue、Comment/Mention、文件、通知、Direct LLM、SSE、RAG 和 CSV 导出；Token 与临时文件仅保存在权限为 `0700` 的临时目录并在退出时删除。

用原生 Bruno Collection 执行同一条端到端链路：

```bash
read -rsp 'Demo password: ' DEMO_PASSWORD
export DEMO_PASSWORD
scripts/test-bruno
unset DEMO_PASSWORD
```

默认临时运行已验证的 Bruno CLI 3.5.1；若本机已有兼容的 `bru`，可设置 `BRUNO_USE_INSTALLED=true`。集合不会把密码、Token 或运行时 ID 写回仓库。

停止服务和基础设施：

```bash
scripts/services-down
scripts/dev-down
```

## 验证与交付资产

```bash
mvn clean test
scripts/check-secrets
scripts/check-log-redaction
scripts/export-openapi
```


- [架构](docs/architecture.md)
- [部署与环境变量](docs/deploy/deployment.md)
- [数据库](docs/db/database.md)
- [MQ、Outbox 与幂等](docs/mq/events.md)
- [Security](docs/security/security.md)
- [OpenAPI、Bruno 与 Postman](docs/api/openapi.md)
- [Direct LLM 与 SSE](docs/ai/direct-llm-and-sse.md)
- [RAG](docs/rag/rag.md)
- [故障排查](docs/deploy/troubleshooting.md)
- [数据重置](docs/deploy/data-reset.md)
- [演示手册](docs/demo-script.md)
- [扩展点](docs/post-mvp/extensions.md)

## 开发约束

- 五服务不能跨 schema 直接读取其他服务数据；使用 API、版本化事件或本地读模型。
- Secret 只从环境或 Secret Manager 注入，不提交 `.env`，不写日志或 OpenAPI 示例。
- dev/demo/prod 使用外部 LLM/Embedding API。Fake Model 仅允许在 test profile。
- 业务事务与 Local Outbox 同库提交；消费者以 `eventId` 幂等，有限重试后进入 DLQ。
- Post-MVP 只能通过新 migration、稳定 API/事件增量扩展，不能成为 MVP 启动条件。
