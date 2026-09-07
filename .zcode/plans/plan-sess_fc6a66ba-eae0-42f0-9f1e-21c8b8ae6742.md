# B0 文件库与 EDU 抽取对齐设计调整计划（2026-09-07 修订版）

## 基线确认

代码库已回退到 B0 原始状态（HEAD `5ea4673`，工作区干净），上一轮实施全部清除。本计划在干净基线上前向实施，无回退步骤。执行前先更新 `.zcode/plans/plan-sess_fc6a66ba-eae0-42f0-9f1e-21c8b8ae6742.md` 为本版本，并删除残留的空目录 `frontend/src/features/reader/`。

## 决策前提（用户已定）

1. **重跑语义 = 覆盖式重写**：重跑范围内旧 EDU 物理删除后重写，不引入 superseded/proposed 状态迁移逻辑。与 blueprint 18.3/20.2 的冲突在 README「已知偏差」记录，待 B2 与 EDU 编辑、替代、重复候选簇一起处理。
2. **MR5（前端阅读器对齐）暂缓不做**：阅读器维持现状（两栏、阅读页"重新抽取本段"按钮、版本不进 URL）。
3. **原生 JSON Schema 暂缓**：保留提示约束 JSON，README 记为已知偏差。

MR2/MR3/MR4 用户未提出异议，保留实施。无需新增数据库迁移。

## MR1：写入路径重构 + 覆盖式重写 + 确定性幂等

1. **新增 `semantic/persistence/EduBatchWriter.java`**：
   - `@Transactional write(Edu, List<EduSourceRef>, List<EduArgument>)`：单条 EDU 三表原子写入，短事务。
   - `@Transactional deleteExisting(versionId, textUnitId)`：重跑前删除范围内旧 EDU 及来源、参数（全文按版本三表删除；局部按 textUnitId 经 `edu_source_ref` 关联找 eduIds 后删三表，含把该单元作为补全上下文引用的 EDU）。沿用原 `replaceExisting` 逻辑，不写任何状态迁移。
2. **`ExtractEduJobHandler` 去掉类级 `@Transactional`**：开头 `deleteExisting`（含上次尝试的半成品）；循环内组装上下文、模型生成、校验定位均在事务外，`EduBatchWriter.write` 短事务写入；每单元后 `jobs.save` 即时提交进度（修复轮询全程 progress=0）。崩溃时已写入 EDU 保留，重试时 `deleteExisting` 清理半成品。
3. **`ExtractContentJobHandler` 拆事务**：S3 下载与解析移到事务外；版本 + 文本单元 + 任务进度合并为一个短事务（新增 `runcontrol/ExtractContentWriter`）；删除恒为 false 的 `job.getStatus()=="succeeded"` 守卫，改为 `job.getDocumentVersionId() != null` 即跳过重建。
4. **幂等键确定性**：`submitEdu/submitExtract` 去掉默认键随机 UUID；同参非终态任务存在时直接复用（挡双击）；终态后重跑按 `base:序号` 新建。`JobRepository` 补 `findFirstByIdempotencyKeyStartingWithAndStatusInOrderByCreatedAtDesc` 与 `countByIdempotencyKeyStartingWith`。前端 `jobs.ts` 删除随机 `Idempotency-Key` 头。客户端键命中他人任务时返回 409。
5. `EduRepository`/`EduSourceRefRepository`/`EduArgumentRepository` 的物理删除方法保持现状。

## MR2：processing_run 落地

1. 新增 `runcontrol/ProcessingRun` + `ProcessingRunRepository`（映射 V003 已有表；jsonb 列用 `@Transactional @Modifying` CAST 语句单独写）。
2. EDU 任务：开始建 run（job、版本、range、context_strategy、provider、模型、提示版本、schema 版本）；生成与复核的 token/延迟累计（`EduReviewResult` 扩展 tokenInput/tokenOutput/latencyMs 字段，同步两个网关与 `EduJsonMapper.parseReview`）；结束写终态、`finished_at`、复核分布 jsonb；`edu.processing_run_id` 回填；模型与提示版本以网关首次返回值为准。内容提取任务同样记 run（provider=builtin, modelId=text-v1）。实体二次 save 前 `setNew(false)`。
3. **丢弃输出附件**：校验/定位失败的模型输出按单元批次序列化，`S3AssetStore.putObject` 写 `runs/{runId}/dropped-{seq}.json`；`attachment_object_key` 存首键，`parameters` jsonb 记前缀与数量。

## MR3：来源引用与校验收紧

1. `persistOne` 保存全部 sources：逐条 locate，第一条 `primary`、其余 `context`；`autoActive` 要求 primary 精确命中（active/proposed 判定沿用原有逻辑，不新增状态）。
2. 删除"sources 为空伪造 target 全文"fallback：缺来源直接校验失败，进 dropped 附件。
3. `SourceLocator`：降级定位 `quote` 返回 null，未命中摘录不入库。
4. `EduValidator`：删除 `FORBIDDEN_MARKERS` 样例词表（禁止推断归还复核模型 + gold 样本）；补 type 五枚举、`time.precision` 枚举校验。
5. `EduContractTest`：定位用例改由 `evaluation/gold/historical/source-locator.jsonl` 驱动；`forbidInference` 改为样本驱动的职责边界断言；新增无 sources 失败、降级 quote 为空、枚举非法用例。

## MR4：任务重试退避与提交期校验

1. 错误分类：`UnprocessableException`/`ConflictException`/`NotFoundException`/`IllegalStateException` 为永久失败；其余（超时、IO、5xx、模型 JSON 解析）可恢复。
2. `JobService.execute` catch 分流：可恢复且未超 `max-attempts`（新配置，默认 3）置回 `pending` 并按 30s×2^(n-1) 退避重排（上限 10 分钟）；否则终态失败。
3. `ai.mode=disabled` 时 `submitEdu` 提交即抛明确配置错误。
4. `JobResponse` 补 `jobUrl`；`UploadService.complete` 校验会话 `pending` 且未过期。

## 测试与验收

- 新增 `ExtractEduPersistenceIT`（Testcontainers）：**删除语义**——全文重跑后旧行消失、总行数等于新一轮条数、无 superseded 行；局部重跑只删引用该单元的 EDU；同参未完成提交复用、终态后重跑新建；processing_run 字段与 `edu.processing_run_id` 断言。
- `JobServiceTest` 补错误分类；`EduContractTest`/`EduJsonMapperTest` 按 MR3/MR2 更新。
- README：保留原有第 5/6 步表述（覆盖、重新抽取本段），「已知偏差」节记录覆盖式重写暂缓和提示约束 JSON 两项；补重试/幂等行为说明。
- 回归：`./mvnw test -Dtest='*Test,*IT'` 全绿；前端 `pnpm typecheck && pnpm test && pnpm build`。

## 明确不做（保持边界）

重跑软替代与 superseded 状态迁移（用户决策暂缓，B2 处理）；阅读器对齐 blueprint 16.4（用户决策暂缓）；逐条 `supersedes_edu_id`、重复候选簇、EDU PATCH/替代接口（B2）；分层上下文预算（B2）；重复上传提示、资产复用、检索版本去重（后续阶段）；原生 JSON Schema（暂缓）。