# 本地部署与环境变量

## 前置条件

- Java 21 或更高版本，Maven 3.8.7+
- Docker Engine 与 Docker Compose v2
- `bash`、`curl`、Python 3，演示需要 `jq`
- 可访问的 OpenAI-compatible Chat 和 Embedding API
- 建议至少 8 GB 可用内存和 10 GB 磁盘

## 环境文件

复制 `.env.example` 为不受 Git 跟踪的 `.env`。示例文件中的所有 Secret 都只能是 `replace_me_*` 占位值。不要把 `.env` 粘贴到 Issue、日志或测试报告。

关键变量如下；完整可选项和默认值以根目录 `.env.example` 为准。

| 类别 | 必填变量 | 说明 |
|---|---|---|
| PostgreSQL | `POSTGRES_DB`, `POSTGRES_ADMIN_PASSWORD`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | DB 必须为 `liteworkflow_backend`；应用用户不是 superuser |
| Redis | `REDIS_PASSWORD` | Gateway 限流与权限缓存 |
| RabbitMQ | `RABBITMQ_USER`, `RABBITMQ_PASSWORD`, `RABBITMQ_VHOST` | VHost 默认为 `liteworkflow_backend` |
| JWT/内部调用 | `JWT_SECRET`, `JWT_ISSUER`, `INTERNAL_SERVICE_TOKEN` | JWT Secret 是至少 32 随机字节的 Base64；内部 Token 独立生成 |
| AI Chat | `LITEWORKFLOW_AI_BASE_URL`, `LITEWORKFLOW_AI_API_KEY`, `LITEWORKFLOW_AI_CHAT_MODEL` | dev/demo/prod 必填，不允许 Fake 回退 |
| Embedding | `LITEWORKFLOW_AI_EMBEDDING_BASE_URL`, `LITEWORKFLOW_AI_EMBEDDING_API_KEY`, `LITEWORKFLOW_AI_EMBEDDING_MODEL`, `LITEWORKFLOW_AI_EMBEDDING_DIMENSIONS` | 维度必须和供应商实际输出一致 |
| S3 | `SEAWEEDFS_ACCESS_KEY`, `SEAWEEDFS_SECRET_KEY`, `S3_BUCKET`, `S3_REGION`, `S3_ENDPOINT`, `S3_PATH_STYLE` | 本地走 SeaweedFS；AWS 留空 endpoint 并关闭 path style |
| SMTP | `SMTP_HOST`, `SMTP_PORT`, `SMTP_FROM` | 本地 MailHog 为 1025/8025 |
| Nacos | `NACOS_AUTH_TOKEN`, `NACOS_AUTH_IDENTITY_KEY`, `NACOS_AUTH_IDENTITY_VALUE`, `NACOS_ADMIN_PASSWORD` | 当前 MVP 不把 Nacos 作为服务启动硬依赖，但 M0 对其做健康检查 |
| 日志 | `LOG_DIR`, `LOG_MAX_FILE_SIZE`, `LOG_MAX_HISTORY`, `LOG_TOTAL_SIZE_CAP` | 每服务 application/error 滚动日志 |

外部 AI 的超时、重试、并发和配额以 `LITEWORKFLOW_AI_*` 配置；导出、邮件、Outbox 和 RAG 重试也全部在 `.env.example` 中列出。

## 从空环境启动

`dev-reset --yes` 会永久删除此 Compose 项目的 PostgreSQL、Redis、RabbitMQ、Nacos、SeaweedFS 和 MailHog volume。仅在明确需要空环境时使用。

```bash
scripts/services-down || true
scripts/dev-reset --yes
scripts/check-infra
scripts/services-up
scripts/check-services
```

启动顺序：

1. PostgreSQL/pgvector、Redis、RabbitMQ、Nacos、SeaweedFS、MailHog；
2. `core-service`（创建 core migration 并先接收用户事件）；
3. `identity-service`；
4. `infra-service`；
5. `ai-service`（校验模型配置、Embedding 维度和 rag schema）；
6. `gateway-service`。

`scripts/services-up` 默认先执行 `mvn -DskipTests package`，PID 写到忽略目录 `.run/`，控制台输出写到忽略目录 `logs/<service>/console.log`。它不会启动 Ollama，也不会创建任何 Post-MVP 组件。

## 生产注意事项

- 使用 `prod` profile，Swagger/API Docs 默认关闭。
- 以 Secret Manager/编排平台注入 Secret，不使用仓库 `.env`。
- AWS S3 使用默认 credential provider chain；`S3_ENDPOINT` 留空、`S3_PATH_STYLE=false`。
- 终止 TLS、设置可信代理边界、限制 `/actuator`，并为 PostgreSQL/RabbitMQ/S3 做备份与告警。
- 本仓库提供的是首次 MVP 本地部署基线，不包含 Kubernetes、HA、OpenTelemetry 或灾备自动化。
