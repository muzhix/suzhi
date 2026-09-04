# 溯知（OntoTrace）

文档到 EDU 的研究平台。当前阶段：B0（TXT/Markdown 纵向切片）。

## 本地启动

需要三个进程：PostgreSQL + 对象存储、后端、前端。

```bash
cp .env.example .env
# 编辑 .env：填入 AI_API_KEY、AI_BASE_URL（OpenAI 兼容，需含 /v1）、AI_CHAT_MODEL
docker compose up -d postgres silo silo-init
# Silo/MinIO 要求密码至少 8 位，默认见 .env.example 的 S3_SECRET_KEY；改密码后需重建 silo 容器并重启后端

# 终端 1：把 .env 导出后再启动，Spring Boot 不会自动读取 .env
set -a && source .env && set +a
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# 终端 2
cd frontend
corepack enable
pnpm install --frozen-lockfile
pnpm dev
```

浏览器打开 http://localhost:5173 ，应跳转到登录页「登录溯知」。

引导管理员账号以 `.env.example` 为准：`hanbd` / `change-me`。只在库里还没有管理员时创建。

本地 `application-local.yml` 默认 `ontotrace.ai.mode=live`，必须提供有效 `AI_API_KEY`。没有密钥时把 `AI_MODE=stub`（桩模型会把 EDU 截成前 40 字，仅用于走通流程）。

`AI_BASE_URL` 按兼容网关实际路径填写。若地址已带 `/v1`（例如 `.../compatible-mode/v1`），不要再追加一层 `/v1`。

live 抽取默认等待 5 分钟（`AI_CHAT_TIMEOUT`）。任务租约默认 10 分钟（`JOB_LEASE_DURATION`），需要大于一次模型调用，否则任务会在模型返回前被判定失败或被其它 worker 抢走。

任务在虚拟线程上并发执行，单进程默认最多 4 路（`WORKER_MAX_CONCURRENCY`）。该值应明显小于数据库连接池大小；EDU 抽取在模型调用期间会占用连接。提交任务后接口立即返回 202，前端轮询 `GET /api/jobs/{jobId}` 查看进度。

前端开发服务器把 `/api` 代理到 `http://localhost:8080`。如果登录报网络错误，先确认后端已经起来。

## 验证 B0 主链路

1. 打开 http://localhost:5173/login ，用户名 `hanbd`，密码 `change-me`，登录。
2. 进入「文档库」，输入题名，点「新建文档」。表格行内可上传、提取内容、全文抽取 EDU。
3. 在该行点「上传」，选择 `doc/玄武门素材/zizhi-tongjian-wude9-xuanwumen.txt`，等到提示上传完成。
4. 点「提取内容」，行内任务状态变为 `succeeded`。
5. 点「抽取 EDU」，等到任务成功。live 模式会调用大模型；再次抽取会覆盖该版本已有 EDU。
6. 点题名进入文档页。左栏选一段文本，右栏看对应 EDU；「重新抽取本段」只覆盖该段。

## 测试

```bash
cd backend && ./mvnw verify
cd ../frontend && pnpm typecheck && pnpm test && pnpm build
```
