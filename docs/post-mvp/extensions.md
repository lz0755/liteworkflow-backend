# 扩展点与发布边界

只能基于 MVP 的稳定 API、事件 envelope 和新增 migration 增量建设。MVP 的 Compose、五服务启动、health、migration 和演示不得读取 Post-MVP 配置或等待 Post-MVP 组件。

| Milestone | 扩展点 | 不破坏的 MVP 契约 |
|---|---|---|
| 高级通知/导出 | infra 增加模板版本、预览/发布/回滚；export job 增加复杂 XLSX/PDF、取消/过期治理 | 保留现有通知事件、内置基础邮件、CSV/XLSX API 和对象下载权限 |
| RAG 运维/性能 | rag 增加 rebuild run、checkpoint、index version、active version 切换和 HNSW | 保留增量 source version、Project metadata filter、`answer + sources` API；旧 active version 在重建中继续服务 |
| 可观测性/权限治理 | 在现有 traceId/MDC 上加 OpenTelemetry；在 `PermissionService` 后增加版本化策略 | 默认角色行为兼容；Exporter 不可用不阻断请求；策略变更有模拟/发布/回滚/审计 |
| Dashboard/Webhook | 领域事件驱动统计读模型；infra 增加公开事件 DTO、签名、重试和投递日志 | 不实时跨服务扫描；复用 Workspace/Project 权限；内部 Entity 不作为公开 payload |

数据库原则：不修改 已发布 migration；每个服务只迁移自己的 schema；大表回填和索引建设必须可恢复、可观测、可前滚。事件原则：保留 `eventId/eventType/version/occurredAt/scope`，新增字段向后兼容，公开 Webhook DTO 与内部事件解耦。

明确不属于 MVP 启动依赖：PDF 引擎、模板管理服务、RAG rebuild worker、HNSW、OTel Collector、策略引擎、Dashboard 聚合器和 Webhook dispatcher。
