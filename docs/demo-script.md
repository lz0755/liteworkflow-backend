# MVP 演示手册

## 准备

1. 按部署文档启动基础设施和五服务，`scripts/check-services` 全部通过。
2. 配置可计费的真实 Chat/Embedding API，并确认允许本次演示产生调用。
3. Shell 演示需安装 `curl` 和 `jq`；Bruno 验收需 Node.js/npm 或 Bruno CLI。选择符合身份服务密码策略的临时密码，不把它写入 shell history 或共享日志。

## 自动演示

```bash
read -rsp 'Demo password: ' DEMO_PASSWORD
export DEMO_PASSWORD
scripts/demo
unset DEMO_PASSWORD
```

脚本每次使用 UTC 时间生成唯一 `.invalid` 演示邮箱，因此可重复执行而不依赖数据重置。它逐项断言：

1. 注册 Adam、Alice、Bob，并让 Alice 再登录；
2. identity 事件进入 core 用户目录；
3. Adam 创建 Workspace，搜索并添加 Alice；添加后 exclusion 搜索不再返回 Alice；
4. Adam 创建 Project，只从 Workspace 成员中搜索并添加 Alice；
5. Alice 可读取 Project；
6. Adam 创建 Issue 并分配给 Alice；
7. Alice 评论并使用规范 `<@UUID>` mention Adam；
8. 上传 TXT 附件和 Markdown 项目文档；
9. Alice 收到分配通知，Adam 收到 mention 通知；
10. 调用真实外部 LLM 总结 Issue；
11. SSE 至少出现 `context`、`delta`、`done`；
12. RAG 回答返回至少一个来源；
13. 创建 CSV 导出任务，等待完成并验证下载内容。

失败时脚本只报告步骤和稳定错误码，不打印 Token。Token、SSE 输出和下载文件保存在权限受限的临时目录，退出后自动删除。

## Bruno CLI 验收

[原生 Bruno Collection](api/bruno) 可重复执行同一主演示链路：

```bash
read -rsp 'Demo password: ' DEMO_PASSWORD
export DEMO_PASSWORD
scripts/test-bruno
unset DEMO_PASSWORD
```

`scripts/test-bruno` 默认临时运行 Bruno CLI 3.5.1，生成唯一 `DEMO_RUN_ID`，并注入仓库内两份公开 fixture 的绝对路径。它不把密码写入命令参数或集合文件，Token/ID 只保存在 Bruno runtime。设置 `BRUNO_USE_INSTALLED=true` 可改用本机 `bru`。本次实跑为 30/30 requests、30/30 tests；第 28 个导出状态请求因轮询执行了两次，所以执行请求数比 29 个 `.bru` 文件多 1。

也可以导入 [Postman Collection](api/liteworkflow.postman_collection.json) 手动演示。Shell/Bruno 是可重复验收入口，Postman 更适合逐步讲解。
