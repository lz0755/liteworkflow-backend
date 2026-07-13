# 数据库

PostgreSQL 数据库固定为 `liteworkflow_backend`，初始化脚本创建 `vector`、`hstore`、`uuid-ossp` extension，以及 `identity`、`core`、`infra`、`ai`、`rag` schema。应用账户由初始化脚本创建为非 superuser。

## Schema 所有权

| Schema | 服务 | 主要表 |
|---|---|---|
| `identity` | identity-service | `identity_users`, `refresh_tokens`, `password_reset_tokens`, `login_logs`, `local_outbox_events` |
| `core` | core-service | `user_directory`, `workspaces`, `workspace_members`, `projects`, `project_members`, `issues`, `issue_comments`, `activities`, `local_outbox_events`, `consumed_events` |
| `infra` | infra-service | `stored_files`, `notifications`, `email_outbox`, `email_logs`, `export_jobs`, `export_files`, `consumed_events` |
| `ai` | ai-service | `ai_conversations`, `ai_messages`, `ai_usage_logs`, `ai_daily_quotas` |
| `rag` | ai-service | `vector_store`, `rag_index_jobs`, `rag_source_heads`, `rag_index_chunks` |

各服务只迁移自己的 schema。Flyway migration 在 `services/*/src/main/resources/db/`，已发布 migration 不修改；结构变化必须新增版本化 migration。跨服务不得直接查询其他服务 schema。

## 一致性原则

- Workspace/Project 成员、Issue 变更、Activity 和 core Outbox 同事务提交。
- Identity 用户变更和 identity Outbox 同事务提交。
- Project 内 Issue 编号由数据库计数器原子分配。
- `source_type + source_id + source_version + chunk_index` 保证增量索引幂等；新版本激活时旧版本软失效。
- `LITEWORKFLOW_AI_EMBEDDING_DIMENSIONS` 由 Flyway placeholder、PgVector 配置和启动校验共同约束。更换维度不是热配置变更，必须新增 migration/rebuild 方案。

## 检查

```bash
scripts/check-infra
docker compose --env-file .env -f infra/docker-compose.infra.yml exec postgres \
  psql -U "$POSTGRES_USER" -d liteworkflow_backend -c '\dn'
```

备份/恢复、保留期和在线升级自动化尚未进入 MVP，生产落地前必须单独设计。
