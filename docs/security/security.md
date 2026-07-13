# Security 基线

## 身份与权限

- Gateway 白名单仅包括注册/登录/refresh/密码重置、健康检查和非 prod OpenAPI。
- Gateway 删除外部 `X-User-Id`、`X-Username`、角色等内部身份 Header，验证 Bearer JWT 后重建。
- 业务权限基于服务端 `CurrentUser` 和资源关系；Workspace/Project/Issue/File/Export/RAG 均不得信任 body 中的当前用户标识。
- Workspace OWNER/ADMIN 可管理成员；最后一个 OWNER 不能移除或降级。Project 成员必须先是 ACTIVE Workspace 成员。
- 内部 API 不经 Gateway 暴露，并使用独立 `INTERNAL_SERVICE_TOKEN`。

## Secret 与日志

Secret 只能来自环境或 Secret Manager。`.env.example` 中敏感变量必须为 `replace_me_*`；`.env` 已忽略。禁止在日志、异常、OpenAPI 示例和 MQ 中记录 password/hash、JWT、refresh/reset token、Authorization/Cookie、API key、完整 Prompt/模型响应、文件全文和 embedding。

通用 Logback converter 对消息和 throwable 做脱敏。HTTP 日志只记录 path，不记录带搜索词的 query string；AI 日志只记录 requestId、模型、耗时、Token 数和状态。执行：

```bash
scripts/check-secrets
scripts/check-log-redaction
```

Secret 扫描检查 Git 跟踪及未忽略的未跟踪内容，不读取忽略的本地 `.env`。日志检查不输出命中内容，避免二次泄露。

## 文件与外部调用

- 文件对象 key 由服务端生成；拒绝路径分隔符、控制字符、超限、扩展名/MIME/签名不一致。
- 下载、删除、附件、图标和项目文档都先调用 core 做资源权限校验。
- AI 设置连接/请求超时、有限重试、并发和每日配额；不对任意用户输入 URL 发起请求。
- RAG 权限检查先于检索，数据库查询同时应用 Workspace/Project filter。

MVP 尚未包含 SSO、Secret 自动轮换、WAF、细粒度策略引擎和 OpenTelemetry；相关扩展边界见 Post-MVP 文档。
