# 溯知（OntoTrace）

文档到 EDU 的研究平台。B0（TXT/Markdown 纵向切片）已完成；后续从 B1（全书结构与阅读器）实施。PDF/MinerU/OCR 在写作之后的阶段 P。设计见 [blueprint.md](./blueprint.md) 1.15、[dev-landing.md](./dev-landing.md) 0.5。

## 本地启动

需要三个进程：PostgreSQL + 对象存储、后端、前端。

```bash
cp .env.example .env
# 编辑 .env：填入 AI_API_KEY、AI_BASE_URL（OpenAI 兼容，需含 /v1）、AI_CHAT_MODEL
docker compose up -d postgres silo silo-init
# Silo/MinIO 要求密码至少 8 位，默认见 .env.example 的 S3_SECRET_KEY；改密码后需重建 silo 容器并重启后端

# 终端 1：把 .env 导出后再启动，Spring Boot 不会自动读取 .env
set -a && source .env && set +a
./backend/mvnw -f backend/pom.xml spring-boot:run -Dspring-boot.run.profiles=local

# 终端 2
corepack enable
pnpm --dir frontend install --frozen-lockfile
pnpm --dir frontend dev
```

浏览器打开 http://localhost:5173 ，应跳转到登录页「登录溯知」。

引导管理员账号以 `.env.example` 为准：`hanbd` / `change-me`。只在库里还没有管理员时创建。

本地 `application-local.yml` 默认 `ontotrace.ai.mode=live`，必须提供有效 `AI_API_KEY`。没有密钥时把 `AI_MODE=stub`（桩模型会把 EDU 截成前 40 字，仅用于本地走通流程，产出的 EDU 不是真实抽取结果，不要用于验收或演示）。

`AI_BASE_URL` 按兼容网关实际路径填写。若地址已带 `/v1`（例如 `.../compatible-mode/v1`），不要再追加一层 `/v1`。

live 抽取默认等待 5 分钟（`AI_CHAT_TIMEOUT`）。任务租约默认 10 分钟（`JOB_LEASE_DURATION`），需要大于一次模型调用，否则任务会在模型返回前被判定失败或被其它 worker 抢走。可恢复错误（超时、网络瞬断）按退避自动重试，默认最多 3 次（`WORKER_MAX_ATTEMPTS`）。

任务在虚拟线程上并发执行，单进程默认最多 4 路（`WORKER_MAX_CONCURRENCY`）。模型调用发生在数据库事务外，抽取过程逐条短事务写入，轮询 `GET /api/jobs/{jobId}` 能看到进度增长。提交任务后接口立即返回 202 与任务地址；同参数任务在未完成时重复提交会复用同一任务。

前端开发服务器把 `/api` 代理到 `http://localhost:8080`。如果登录报网络错误，先确认后端已经起来。

## 验证 B1 主链路

1. 打开 http://localhost:5173/login ，用户名 `hanbd`，密码 `change-me`，登录。
2. 进入「文档库」，新建文档并上传 TXT。全书用 `doc/正史/` 中的文件（该目录已 gitignore）；短切片仍可用 `doc/玄武门素材/`。
3. 点「提取内容」：短 txt 可不选方案，空行切段，path 为 `p1`、`p2`。全书选体例（纪传体·目录异形 / 纪传体·目录同形 / 编年体·卷纪年），预览目录后再确认。确认前不会写入版本。
4. 点题名进入三栏阅读器。左栏目录树，中间当前节点各段，右栏 EDU。地址栏带 `path` 与 `unit`。
5. 在阅读器「前往抽取」按当前 path 抽取 EDU；文档库不再一键空 body 全文抽取，须勾选全文并看到段数。

B0 短链路（玄武门切片、无方案提取、局部重抽）仍然可用。

## 已知偏差

- 重跑 EDU 为覆盖式重写（物理删除旧 EDU），未实现 superseded 软替代；与 blueprint 18.3/20.2 的差异待 B2 与 EDU 编辑、替代能力一起处理。
- 模型结构化输出目前用提示词约束 JSON 解析，未启用提供方原生 JSON Schema；待生产模型提供方确定后切换，见 `dev-landing.md` 3.1。

## 测试

```bash
./backend/mvnw -f backend/pom.xml verify
# 集成测试（Testcontainers，需 Docker）
./backend/mvnw -f backend/pom.xml test -Dtest='*Test,*IT'
pnpm --dir frontend typecheck && pnpm --dir frontend test && pnpm --dir frontend build
```
