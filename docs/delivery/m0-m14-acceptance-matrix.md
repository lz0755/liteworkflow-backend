# M0–M14 交付矩阵

验收日期：2026-07-13（Australia/Sydney）。状态只使用 `PASS` 或 `NOT DONE`；存在代码但未得到本次测试/实跑证据的条目不算通过。

## 验证基线

- `mvn clean test`：14/14 模块成功；76 份 Surefire report，244 tests，0 failure，0 error，2 条条件测试 skipped。
- 三项 M13 必跑 Testcontainers report 均 `skipped=0`：core 基础设施 6、AI quota PostgreSQL 2、PgVector RAG 8。
- `SEAWEEDFS_CONTRACT_ENABLED=true ... SeaweedFsS3CompatibilityContractTest`：1 test，0 skipped，真实本地 SeaweedFS put/head/get/exists/delete 通过。
- AWS S3 contract 因没有本次交付可用的 AWS bucket/credential 而 skipped；对应验收保持 `NOT DONE`。
- 空环境演练：删除本项目 6 个 volume 后重建；PostgreSQL vector 0.8.2、5 schema、非 superuser 应用角色及 Redis/RabbitMQ/Nacos/SeaweedFS/MailHog 功能检查通过。
- 五服务实跑：8080–8084 health 全部 `UP`；未认证业务请求 401；五组 Swagger 可访问。
- `scripts/demo`：14 步全部通过；MailHog 实际有 16 封消息；Direct LLM、Embedding/RAG 使用本机配置的真实外部 API。
- `scripts/test-bruno`（Bruno CLI 3.5.1）：30/30 requests、30/30 tests；真实 Direct LLM、SSE、RAG、文件、通知和 CSV 下载通过。
- `scripts/check-secrets` 与 15 个运行日志文件的 `scripts/check-log-redaction` 均通过。

## M0–M3

| Milestone | 验收标准 | 状态 | 本次证据 |
|---|---|---|---|
| M0 | 所有容器健康 | PASS | 空 volume `dev-reset --yes` 后 6/6 healthy |
| M0 | 数据库 `liteworkflow_backend` 可连接 | PASS | `check-infra` 数据库名和应用角色检查 |
| M0 | vector extension 存在 | PASS | vector 0.8.2 |
| M0 | Redis、RabbitMQ、SeaweedFS、MailHog 可访问 | PASS | authenticated PING/API/SigV4/MailHog API |
| M1 | `mvn clean test` 通过 | PASS | 244 tests；0 failure/error |
| M1 | 5 个服务独立启动 | PASS | packaged JAR 8080–8084 实跑 |
| M1 | `/actuator/health` 返回 UP | PASS | `scripts/check-services` 5/5 |
| M1 | traceId 进入日志和响应 | PASS | common/gateway 测试；实跑结构化日志 |
| M2 | 无 Token 访问业务接口返回 401 | PASS | 实跑 Gateway check |
| M2 | 合法 Token 透传用户上下文 | PASS | `GatewaySecurityIntegrationTest` + demo |
| M2 | `/api/v1/users/search` 进入 core | PASS | Gateway route test + demo candidate search |
| M2 | Gateway Swagger UI 可切换服务分组 | PASS | 修复路径冲突后五组实跑；5 JSON 已导出 |
| M3 | 注册登录正常 | PASS | demo Adam/Alice/Bob 注册和 Alice 登录 |
| M3 | refresh token 轮换正常 | PASS | `IdentityM3IntegrationTest` |
| M3 | 用户事件包含 userId/email/displayName/status/version | PASS | identity event 测试 + core 投影实跑 |
| M3 | API Key/Token 不进入日志 | PASS | DTO redaction 测试 + 15 文件运行日志扫描 |

## M4–M7

| Milestone | 验收标准 | 状态 | 本次证据 |
|---|---|---|---|
| M4 | 注册用户自动进入 user_directory | PASS | demo 等待事件投影后读取 core profile |
| M4 | OWNER/ADMIN 可按邮箱或显示名搜索候选用户 | PASS | demo 以 Adam 搜索 Alice；M4 tests |
| M4 | 普通 MEMBER 不能搜索全站候选人 | PASS | `CoreM4IntegrationTest` / permission matrix |
| M4 | 可添加、修改、移除 Workspace Member | PASS | demo add；M4 CRUD tests |
| M4 | 不能移除或降级最后一个 OWNER | PASS | M4/M5 edge/concurrency tests |
| M4 | Disabled 用户不出现在结果 | PASS | M4 integration tests |
| M4 | 搜索日志不包含完整 keyword | PASS | HTTP 只记 path；日志脱敏测试/扫描 |
| M5 | Project 创建后有 PROJECT_ADMIN | PASS | demo + `CoreM5IntegrationTest` |
| M5 | Project 候选人只来自所属 Workspace | PASS | demo PROJECT search + M5 tests |
| M5 | 非 Workspace Member 不能加入 Project | PASS | M5 tests |
| M5 | Project Admin 可管理成员 | PASS | member permission matrix |
| M5 | Workspace Owner/Admin 可管理下属 Project 成员 | PASS | member permission matrix |
| M5 | 移除 Workspace Member 后 Project 权限立即失效 | PASS | M5 edge/integration tests |
| M6 | 并发 Issue 编号不重复 | PASS | `concurrentCreatesAllocateUniqueMonotonicNumbersWithinProject` |
| M6 | 非 Project Member 无法读写 | PASS | M6 project-scoped access test |
| M6 | 已移除成员不能被新分配 | PASS | M6 assignee eligibility test |
| M6 | MQ 不可用时事务成功且 Outbox 可补偿 | PASS | `publisherFailureDoesNotRollbackIssueAndRecoveryPublishesPendingEvent` |
| M7 | 评论权限正确 | PASS | demo Alice comment + M7 role tests |
| M7 | mention 不存在/非成员返回业务错误 | PASS | M7 project-scoped mention tests |
| M7 | 重复消息不产生重复通知 | PASS | notification idempotency + concurrent duplicate tests |

## M8–M10

| Milestone | 验收标准 | 状态 | 本次证据 |
|---|---|---|---|
| M8 | 本地 SeaweedFS 上传下载正常 | PASS | demo 文件/导出对象；真实 S3 compatibility contract 1/1 |
| M8 | AWS S3 只需改配置即可切换 | **NOT DONE** | adapter/profile 已实现，但 AWS compatibility contract 本次 skipped；没有真实 AWS endpoint 证据 |
| M8 | 未授权用户不能下载文件 | PASS | `FileApplicationServiceTest` IDOR cases |
| M8 | 项目文档事件含必要元数据且无正文 | PASS | document MQ contract + demo RAG document event |
| M9 | 成员添加、分配、提及产生通知 | PASS | demo 验证 Alice/Adam 通知 |
| M9 | CSV/XLSX 可下载且大数据不加载全部 JSON | PASS | demo CSV；bounded keyset CSV + SXSSF tests |
| M9 | 基础邮件可在 MailHog 查看 | PASS | MailHog API `total=16` |
| M9 | 重复 MQ 不重复写通知 | PASS | projection idempotency/concurrency tests |
| M9 | 日志容量有限且无敏感信息 | PASS | rolling cap 配置；15 文件扫描通过 |
| M10 | dev/demo 请求走外部 API | PASS | demo Direct summary 使用真实配置 |
| M10 | 工程无 Ollama 依赖和配置 | PASS | POM/config/code 扫描无 Ollama |
| M10 | API 不可用返回明确 502/504 类错误 | PASS | MockWebServer 429/5xx/timeout tests |
| M10 | 不静默回退 Fake Model | PASS | Fake 仅 test profile；非 test 配置验证 |
| M10 | AI 不直接写 core 业务表 | PASS | 服务边界/依赖审计；AI 只调用内部 context API |
| M10 | usage log 记录模型、Token、耗时 | PASS | AI tests + 实跑 usage/log summary |

## M11–M13

| Milestone | 验收标准 | 状态 | 本次证据 |
|---|---|---|---|
| M11 | `curl -N` 可看到增量输出 | PASS | demo 收到 context/delta/usage/done |
| M11 | 客户端断开取消上游 | PASS | AI 和 Gateway disconnect tests |
| M11 | SSE 日志只记录摘要 | PASS | 运行日志仅 requestId/model/latency/token/status；扫描通过 |
| M11 | 不存在自研通用 SSE chunk parser | PASS | 使用 Spring AI stream，代码扫描 |
| M12 | 向量维度与 Embedding API 一致 | PASS | 空库启动实时校验 + dimension tests |
| M12 | 更换维度时部署检查阻止错误启动 | PASS | `EmbeddingDimensionValidatorTest` |
| M12 | 无权限用户不能检索其他 Project | PASS | `ProjectAskSecurityTest` + PgVector scope tests |
| M12 | 文档更新后旧版本不参与检索 | PASS | RAG index/PgVector integration tests |
| M12 | 失败索引可重试且无重复有效记录 | PASS | RAG job/idempotency tests；实跑 jobs COMPLETED |
| M12 | 回答有来源或明确无依据 | PASS | demo RAG 返回来源 + no-context test |
| M13 | `mvn clean test` 通过 | PASS | final build success，244 tests |
| M13 | 用户事件重复消费幂等 | PASS | M4 + Testcontainers duplicate/version tests |
| M13 | 成员权限矩阵覆盖主要角色 | PASS | `MemberPermissionMatrixTest` |
| M13 | AI 429/5xx/timeout 可预测 | PASS | Direct/Embedding/SSE integration tests |
| M13 | RAG 无跨项目数据泄漏 | PASS | PgVector filter integration + security-first test |

## M14 最终验收

| 验收标准 | 状态 | 本次证据 |
|---|---|---|
| Workspace 添加用户功能可用 | PASS | demo |
| Project 添加用户功能可用 | PASS | demo |
| 用户搜索具备权限、排除和隐私控制 | PASS | demo exclusion + permission/privacy tests |
| dev/demo/prod 不依赖 Ollama | PASS | 配置/依赖扫描 |
| 外部 LLM/Embedding 配置清晰 | PASS | `.env.example` 与 AI/RAG 文档 |
| 5 服务和基础设施可启动 | PASS | 空环境演练 |
| 主业务、文件、通知、导出、AI、SSE、RAG 可演示 | PASS | `scripts/demo` 14/14；Bruno 30/30 |
| OpenAPI、测试、部署和演示文档齐全 | PASS | `docs/`、5 JSON、Bruno、Postman、脚本 |
| M15–M18 扩展点已记录且不影响启动 | PASS | `docs/post-mvp/extensions.md`；启动配置无 Post-MVP 依赖 |

## 交付判断与剩余风险

M14 交付资产和本地 MVP 演示已完成，但严格的 M0–M13 矩阵仍有 **1 个 `NOT DONE`：真实 AWS S3 切换契约未执行**。因此不能声称所有历史验收全绿。早先一次 `curl` 实跑观察到 Gateway 后的 SSE transport 在 `done` 后保持连接；本次 Bruno 3.5.1 实跑在收到 `done` 后正常结束，断连取消测试也通过，但仍建议在目标生产代理上复核长连接行为。
