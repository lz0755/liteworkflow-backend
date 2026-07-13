# 故障排查

| 症状 | 检查 | 处理 |
|---|---|---|
| `dev-up` 超时 | `docker compose --env-file .env -f infra/docker-compose.infra.yml ps` | 查看对应容器 health/log；确认端口未占用和 `.env` 无占位值 |
| PostgreSQL migration 失败 | 服务 console log、Flyway history、schema owner | 不修改已发布 migration；修正权限或新增前滚 migration |
| Gateway 返回 401 | Authorization Bearer、JWT issuer/secret、时钟 | 重新登录；确保五服务使用同一 JWT 配置，不记录 Token |
| 用户注册后搜不到 | identity outbox、RabbitMQ、core consumer、`core.user_directory` | 等待补偿；检查旧版本/重复事件，不手工跨 schema 复制认证表 |
| 通知/导出不推进 | outbox、queue、consumer retry、DLQ、S3 | 修复依赖后触发既有补偿；不要重复创建业务任务 |
| 文件上传 4xx | 文件名、大小、扩展名、声明 MIME、内容签名 | 使用允许的真实 PNG/JPEG/PDF/TXT/MD/DOCX/ZIP |
| ai-service 启动失败 | 模型/key/维度是否为真实值；rag migration | 配齐外部 API；维度变化按 migration/rebuild 处理 |
| AI 返回 429/502/504 | 配额、并发、供应商状态、超时 | 降低并发或等待；不要启用 Fake 回退 |
| SSE 中断 | Gateway header/timeout、代理 buffering、上游 timeout | 使用 `curl --no-buffer`；关闭代理缓冲；检查唯一 done/error |
| RAG 无来源 | 索引 job、S3、Embedding、source version、scope filter | 等待/重试单任务；不可取消 Workspace/Project filter |

常用命令：

```bash
scripts/check-infra
scripts/check-services
tail -n 100 logs/ai-service/error.log
scripts/check-log-redaction
```

排障输出不得包含 `.env`、Authorization、完整 Prompt、文件正文或邮件内容。需要共享日志时只提供脱敏后的 traceId、时间、服务、错误码和调用状态。
