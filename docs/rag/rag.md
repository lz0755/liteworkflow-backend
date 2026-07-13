# 增量 RAG

MVP 对 Issue、Comment 和 Project Document 做增量索引。Embedding 和 Chat 可以使用不同 OpenAI-compatible endpoint/key，但模型与输出维度必须明确配置。

## 索引流程

1. core/infra 发布只含必要元数据的版本化事件；文档正文不进入 MQ。
2. ai-service 幂等创建 `rag_index_jobs`，从 core 内部 API或 S3 获取受控内容。
3. Issue/Comment 整体索引；Document 抽取并按 token 数切片。
4. 以 source version 更新，激活新 chunk 后软失效旧版本；单任务可安全重试。
5. `POST /api/v1/ai/projects/{projectId}/ask` 先校验权限，再在数据库查询层同时过滤 `workspaceId` 与 `projectId`。
6. 回答返回 `sources`；没有可靠上下文时明确回答“没有找到依据”，不伪造引用。

## 维度变更

`LITEWORKFLOW_AI_EMBEDDING_DIMENSIONS` 同时传给 Flyway、Spring AI PgVector 和启动验证器。供应商模型维度变化必须作为部署变更处理：新增 migration/重建计划、验证空库和升级路径，再切换模型。不能只改环境变量后复用旧向量表。

`LITEWORKFLOW_RAG_SIMILARITY_THRESHOLD` 是模型相关参数；`.env.example` 的 `0.50` 只是演示起点。上线前必须用本供应商 Embedding 模型的标注查询校准召回/精度，不能为了演示取消 Workspace/Project filter。

## 边界与排障

MVP 没有全库重建、双版本切换、HNSW 或在线召回率基准，这些属于 M16。单个任务失败先检查 `rag_index_jobs` 的 attempt/lease/error code、Embedding API 可达性、对象权限和维度，不要直接删除 source head。跨 Project 零泄漏是发布阻断项。
