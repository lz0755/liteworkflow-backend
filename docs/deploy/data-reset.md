# 数据重置

停止应用不会删除数据：

```bash
scripts/services-down
scripts/dev-down
```

完整空环境重置会永久删除本 Compose 项目的 PostgreSQL、Redis、RabbitMQ、Nacos、SeaweedFS 和 MailHog volume：

```bash
scripts/services-down
scripts/dev-reset --yes
```

只删除且不重启：

```bash
scripts/dev-reset --yes --no-start
```

脚本必须显式 `--yes`，避免误删。重置不会删除仓库、`.env`、Maven cache 或 `logs/`；`scripts/services-up` 会从空库执行全部 Flyway migration。不要用手工删除单个 schema/queue/object 的方式模拟全量重置，这会破坏跨服务投影一致性。

生产环境禁止使用此脚本；应使用经过审查的备份、恢复、保留和变更流程。
