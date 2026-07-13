# M14 Security 与日志脱敏检查

检查日期：2026-07-13。范围是当前工作树（已跟踪及未忽略的未跟踪文件）和本次本地实跑生成的日志；不包含 Git 历史、第三方供应商账户或生产 Secret Manager。

## 结果

| 检查 | 结果 | 说明 |
|---|---|---|
| Private key header | PASS | 未发现 PEM/OpenSSH private key |
| OpenAI-style key | PASS | 未发现 `sk-` 长 Key |
| AWS access key | PASS | 未发现 AKIA/ASIA key |
| GitHub token | PASS | 未发现 ghp/github_pat token |
| JWT literal | PASS | 未发现 `JWT_SECRET=` 长字面值 |
| `.env.example` Secret | PASS | 13 个敏感字段均为 `replace_me_*` |
| 运行日志 | PASS | 15 个 `.log` 无 Bearer、未脱敏 Secret assignment 或明文邮箱命中 |
| Ollama 依赖/配置 | PASS | 生产 POM、Java 和 YAML 无 Ollama |

执行入口：

```bash
scripts/check-secrets
scripts/check-log-redaction
```

## 本次修复

- 删除 identity/core/infra/ai RabbitMQ 密码的 `guest` 默认值。
- 删除 core/infra 内部服务 Token 的 `change-me` 运行默认值。
- 将测试中长得像真实供应商 Key 的夹具改为明确的 `test-*-placeholder`。
- Secret 扫描命中时只打印文件名，不打印命中内容；日志扫描同样抑制内容。
- 保留通用 Logback sanitizer、敏感 DTO `toString()` 脱敏和只记录 HTTP path 的行为。

## 非覆盖范围

Git 历史 Secret 扫描、容器镜像漏洞/SBOM、依赖 CVE、DAST、生产 IAM/TLS/WAF 和 Secret 自动轮换不在本次命令覆盖范围，不能从当前 PASS 推断这些能力已经完成。
