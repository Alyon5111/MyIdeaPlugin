# SDD → Plan → TDD → Change 流程测试用例（MyIdeaPlugin）

> 用途：验证规格驱动开发（Spec-Driven Development）全流程，阶段依序推进并正确流转数据。
> 方式：在 IntelliJ 中运行插件（`runIde`），在 Agent 会话中用**自然语言触发关键词**调用对应工具，检查返回内容。
> 依据：以真实代码行为为准（`SDDPipelineTool` / `PipelineService` / `TddEnforcerTool` / `SpecPipelineInstance`）。

## 工具调用机制（重要前提）

- 插件**没有** `/toolname action=...` 命令路由。工具选择由 `AgentExecutor.selectToolsForMessage()` 依据**用户消息中的关键词**决定，工具的 action/参数由 LLM 从自然语言推断并填成 JSON。
- 每个用例的"输入"必须包含对应触发关键词，否则工具不会被选中、无输出：
  - `sdd_pipeline` → 关键词含 `pipeline` / `spec` / `advance tdd` / `next stage`
  - `tdd_enforcer` → 关键词含 `tdd`
  - `generate_plan` → 关键词含 `generate plan` / `implementation plan`
  - `create_change` / `link_change` → 关键词含 `change`
- `pipeline_id` 是贯穿全流程的主键，`start` 的输出必须保存供后续每个步骤复用。

## 阶段总览

```
SPEC → PLAN → TDD → CHANGE → DONE
```

- 一次 PLAN 步骤 = 一个 TDD 循环（RED→RED_VERIFY→GREEN→GREEN_VERIFY→REFACTOR→DONE）。
- 两个协同工具：
  - **`sdd_pipeline`**（`SDDPipelineTool`）：管理流水线实例与阶段流转。
  - **`tdd_enforcer`**（`TddEnforcerTool`）：管理每步骤的 TDD 状态机；`link_pipeline` 后可联动自动推进流水线。

---

## 阶段一：SPEC（需求 → 流水线实例）

### P-01 启动流水线

| 输入 | 具体预期 | 输出 → 下一流程 |
|------|----------|-----------------|
| `pipeline start requirement="用户登录" spec_domain=auth` | 返回 `=== SPEC PIPELINE STARTED ===`，含：`Pipeline ID: <uuid>`、`Requirement: 用户登录`、`Stage: SPEC`，并提示保存 pipeline_id | **输出的 pipeline_id 是后续所有 add_step / advance_plan / start_tdd 的必传参数**，测试者必须复制保存 |

### P-02 缺 requirement 启动（防御）

| 输入 | 具体预期 | 备注 |
|------|----------|------|
| `pipeline start`（无 requirement） | 返回 `Error: 'requirement' is required to start a pipeline`，**不创建实例** | 防御性校验，避免污染实例列表 |

### P-03 状态查询（SPEC 期）

| 输入 | 具体预期 | 输出 → 下一流程 |
|------|----------|-----------------|
| `pipeline status pipeline_id=<id>` | 返回 `Stage: SPEC`、`Plan steps (0):`（空）、Stage Log 首行 `CREATED at SPEC stage...`、**不显示** `Current step`（因 currentStepIndex=-1） | 确认实例已建立且阶段正确、**还没有计划步骤** → 下一步必须 add_step |

---

## 阶段二：PLAN（计划步骤 → 进入 PLAN）

### P-04 添加计划步骤

| 输入 | 具体预期 | 输出 → 下一流程 |
|------|----------|-----------------|
| `pipeline add_step pipeline_id=<id> plan_steps="写登录测试\n实现登录逻辑"` | 返回 `Added 2 plan step(s) to pipeline <id>.`，并提示 `Next: call advance_plan` | **plan_steps 被存入实例，共 2 步**。这是后续 start_tdd 的 step_index 依据（0=写登录测试，1=实现登录逻辑） |

备注：`plan_steps` 按换行拆分，空行会被忽略（`addStep` 过滤空白行）。

### P-05 缺步骤推进（防御）

| 输入 | 具体预期 | 备注 |
|------|----------|------|
| `pipeline advance_plan pipeline_id=<id>`（此时**未** add_step） | 返回 `Add plan steps first (action=add_step) or you can pass plan_steps with advance_plan.`，**阶段仍停留 SPEC** | 验证"无步骤不可推进"的护栏 |

### P-06 生成并落地计划（与 generate_plan 衔接）

| 输入 | 具体预期 | 输出 → 下一流程 |
|------|----------|-----------------|
| `generate plan source="<P-01需求文本>"` | 返回生成的 TDD 格式分步计划 + 计划文件路径（`docs/plans/...`） | **plan 文件路径可作为 advance_plan 的 plan_file 参数**，让流水线记录计划文件位置 |

### P-07 推进到 PLAN

| 输入 | 具体预期 | 输出 → 下一流程 |
|------|----------|-----------------|
| `pipeline advance_plan pipeline_id=<id> plan_file=docs/plans/xx.md` | 返回 `=== STAGE: PLAN ===`，含 `Plan file: docs/plans/xx.md`、`Steps: 2`、`Next: call start_tdd with step_index=0` | **阶段从 SPEC 推进到 PLAN，并记录了计划文件**。验证 status 时 `Plan:` 字段已被填入 → 下一流程是 start_tdd(0) |

---

## 阶段三：TDD（每步骤一个 TDD 循环）

**联动前提（P-08a）：** 要让 TDD 循环结束时**自动推进**流水线，必须先执行 `tdd link_pipeline pipeline_id=<id>`。

### TDD 内部循环（P-08b ~ P-08g）

| 用例 | 输入 | 具体预期 | 输出 → 下一流程 |
|------|------|----------|-----------------|
| P-08b | `tdd start_cycle task="写登录测试"` | `=== TDD Cycle Started ===`，Task、Cycle #、`State: RED`，提示先写失败测试 | **state=RED** → 下一步 write_test |
| P-08c | `tdd write_test` | (RED→RED_VERIFY) 提示运行测试确认失败 | **state=RED_VERIFY** → 下一步 verify_test |
| P-08d | `tdd verify_test test_output="BUILD FAILED\n1 test failed: expected 2 but was 1"` | (RED_VERIFY→GREEN)（测试按预期失败） | **state=GREEN** → 下一步 write_code |
| P-08e | `tdd write_code` | (GREEN→GREEN_VERIFY) | **state=GREEN_VERIFY** → 下一步 verify_code |
| P-08f | `tdd verify_code test_output="BUILD SUCCESSFUL\n3 tests passed"` | (GREEN_VERIFY→REFACTOR) + 重构提示 | **state=REFACTOR** → 下一步 mark_refactor |
| P-08g | `tdd mark_refactor` | `=== TDD Cycle Complete ===`，**因已 link_pipeline，自动把流水线该步骤标记完成** | **驱动流水线推进**（见 P-09/P-10） |

备注：若**未** link_pipeline，`mark_refactor` 只结束 TDD 循环，不触碰流水线。

### P-09 单步骤循环完成 → 流水线进 CHANGE

| 前置 | 输入 | 具体预期 | 输出 → 下一流程 |
|------|------|----------|-----------------|
| 流水线只有 1 步，已 link_pipeline，跑完 P-08b~g | `tdd mark_refactor` | 返回含 `Spec pipeline <id>: ALL plan steps done -> CHANGE.` 和 `Next: create_change + implement + archive, then pipeline.link_change.` | **流水线阶段自动从 TDD 推进到 CHANGE**（`allStepsDone()`=true 触发）→ 下一步做 change |

### P-10 多步骤：中间步骤完成 → 仍停留 TDD

| 前置 | 输入 | 具体预期 | 输出 → 下一流程 |
|------|------|----------|-----------------|
| 流水线有 2 步，P-08 完成 step0 的 TDD | `tdd mark_refactor` | 返回 `Spec pipeline <id>: step 1 TDD done.` + `Next: start the next plan step's TDD cycle, or call pipeline.start_tdd` | **阶段仍是 TDD**（未 allStepsDone）→ 下一步 `pipeline start_tdd step_index=1` 开始第 2 步 |

### P-11 显式推进到第 2 步

| 输入 | 具体预期 | 输出 → 下一流程 |
|------|----------|-----------------|
| `pipeline start_tdd pipeline_id=<id> step_index=1` | 返回 `=== STAGE: TDD (step 2/2) ===`、`Task: 实现登录逻辑`、提示 `Use 'tdd_enforcer start_cycle' with this task...complete_tdd with step_index=1` | **currentStepIndex 更新为 1** → 再跑一轮 P-08b~g，mark_refactor 后 allStepsDone → CHANGE |

（也可用 `pipeline complete_tdd step_index=<当前>` 手动完成步骤，但推荐 tdd mark_refactor 联动。）

---

## 阶段四：CHANGE（变更 → 结束）

### P-12 关联变更并完成

| 前置 | 输入 | 具体预期 | 输出 → 下一流程 |
|------|------|----------|-----------------|
| 流水线已到 CHANGE（所有 TDD 步完成） | `pipeline link_change pipeline_id=<id> change_name=login-change` | 返回 `Linked change 'login-change' to pipeline <id>. Stage: CHANGE`（因当前是 CHANGE → 自动 advance 到 DONE） | **流水线进入 DONE**。change 应已配合 openspec 的 create_change 在 `openspec/changes` 创建 |

### P-13 完成（归档收尾）

| 输入 | 具体预期 | 输出 → 下一流程 |
|------|----------|-----------------|
| `pipeline complete pipeline_id=<id>` | 返回 `Pipeline <id> marked DONE (requirement delivered through spec->plan->tdd->change).` | **阶段 DONE** → 从 `pipeline list`（active 列表）中消失 |

### P-14 列表验证（排除 DONE）

| 输入 | 具体预期 | 备注 |
|------|----------|------|
| `pipeline list` | 返回 `[DONE]` 的实例**不再出现**，只列 SPEC/PLAN/TDD/CHANGE 状态的实例（`getActive()` 排除 DONE） | 验证归档效果 |

### P-15 全程状态明细（每阶段可用）

| 输入 | 具体预期 |
|------|----------|
| `pipeline status pipeline_id=<id>` | `Stage:`、`Requirement:`、`Plan:`、`Plan steps(N):`（带 `[X]/[>]/[ ]` 标记）、`Current step:`、`Change:`、`Stage Log:`（含各 ADVANCED/STARTED/DONE 记录） |

---

## 端到端冒烟测试（回归重点）

### E2E-01 完整 SDD 流水线（单步骤）

| 执行序列 | 阶段流转 |
|----------|----------|
| `pipeline start ...` → `pipeline add_step(1步)` → `pipeline advance_plan` → `tdd link_pipeline` → `tdd start_cycle` → `tdd write_test` → `tdd verify_test(FAIL)` → `tdd write_code` → `tdd verify_code(PASS)` → `tdd mark_refactor` → `pipeline link_change` → `pipeline complete` | `SPEC → PLAN → TDD → CHANGE → DONE`，全程无 Error，每步输出可被下一步使用 |

### E2E-02 完整 SDD 流水线（多步骤）

| 执行序列 | 阶段流转 |
|----------|----------|
| 与 E2E-01 相同，但 `add_step` 含 2 步；第 1 步的 `mark_refactor` 后停留 TDD，`pipeline start_tdd step_index=1` 完成第 2 步后再进 CHANGE | 验证中间步骤不提前推进：`SPEC → PLAN → TDD → (step0 → TDD) → (step1 → TDD allDone → CHANGE) → DONE` |

---

## 关键注意事项

1. **pipeline_id 是贯穿全流程的主键**，`start` 的输出必须保存供后续每个步骤复用。
2. **TDD 联动依赖 `tdd link_pipeline`**，否则 `mark_refactor` 只结束 TDD 循环、不会自动推进流水线。
3. **工具是关键词驱动的**：所有用例输入需含触发关键词（pipeline / spec / tdd / change / generate plan），否则工具不被选中、无输出。
4. **`complete_tdd` 需要 `step_index` 等于当前 `currentStepIndex`**，否则返回 Error（`completeStepTdd` 校验）。
5. **非法阶段迁移会被拒绝**：例如在 SPEC 阶段直接 `complete_tdd` 会因 currentStepIndex 不匹配而返回 Error。

---

## 完整示例：新功能开发全流程测试（以新增 Agent 工具 `export_plan` 为例）

> 本节用本项目一个**真实的新功能**走完整个 SDD → Plan → TDD → Change 流程，作为可逐条执行、可回归的端到端测试基准。
> 测试项目 = 本项目（MyIdeaPlugin，IntelliJ 本地 LLM 插件）。
>
> ### 新功能需求说明：`export_plan`（导出计划为 Markdown）
> 给本插件新增一个 Agent 工具 `export_plan`：
> - **作用**：把 `docs/plans/` 下的某个实施计划（`PlanStore` 管理的 `.md` 计划）读取后生成一份排版更佳的 Markdown 导出文件。
> - **贴合点**：完全沿用项目现有模式 —— 实现 `AgentTool` 接口（`name()/description()/specification()/execute()`），复用 `PlanStore`（本项目 `agent/plan/PlanStore.java`）的 `readPlan()`/`getPlansDir()` 读取计划，参考 `GeneratePlanTool` 的 action 分发风格。
> - **建议的实现分解**（供流水线规划引用）：
>   - step 0：新增 `ExportPlanTool implements AgentTool`，实现`读取计划 + 校验文件存在 + 返回计划内容`（action=export）
>   - step 1：实现`生成 Markdown 导出文件`（写入 `docs/plans/export/`）并返回文件路径
> - **完成后需在** `AgentExecutor` 的工具列表注册（构造器 `tools = List.of(...)` 中 `new ExportPlanTool(ctx)`），并在 `TOOL_KEYWORDS` 加 `export_plan` → 关键词 `"export plan"`、`"导出计划"`。
>
> ### 前置准备
> 1. 在 IntelliJ 中 `runIde` 启动插件。
> 2. 确认 LLM 服务已配置并连通（`Settings → PluginStateService` 的 LlamaCpp URL，默认 `http://localhost:8080`）。
> 3. 测试开始前确保项目干净，无残留的 `docs/plans`、`openspec/changes`、`docs/plans/export` 产物。

### 阶段零：前置检查

#### REG-0 环境就绪（确认现有 generate_plan 可用，作为 export_plan 的数据源）

| 步骤 | 具体预期 |
|------|----------|
| 在 Agent 会话发送：`列出当前项目里有哪些已生成的实施计划` | 触发 `generate_plan` → 返回 `=== Implementation Plans ===`；若为空则 `No plans found. Use 'generate' to create one.` |

---

### 阶段一：SPEC（需求 → 流水线实例）

#### REG-1 启动流水线（新增 export_plan 工具）

| 测试步骤 / 输入 | 具体预期 | 输出 → 下一流程 |
|------------------|----------|-----------------|
| 发送：`pipeline start requirement="新增 Agent 工具 export_plan：读取 docs/plans 下计划并导出为 Markdown 到 docs/plans/export"` | 返回 `=== SPEC PIPELINE STARTED ===`，含：`Pipeline ID: <uuid>`、`Requirement: 新增 Agent 工具 export_plan...`、`Stage: SPEC`，末尾提示保存 pipeline_id | **记录 `Pipeline ID`，记为 `<PID>`**。后续所有步骤都以 `<PID>` 为必需参数 |

#### REG-2 缺 requirement 防御

| 测试步骤 / 输入 | 具体预期 |
|------------------|----------|
| 发送：`pipeline start 开始一个流水线`（消息里不携带 requirement 内容） | 返回 `Error: 'requirement' is required to start a pipeline`，且 **`pipeline list` 中不会新增该实例** |

#### REG-3 状态确认（SPEC 期）

| 测试步骤 / 输入 | 具体预期 | 输出 → 下一流程 |
|------------------|----------|-----------------|
| 发送：`pipeline status pipeline_id=<PID>` | 返回 `Stage: SPEC`、`Plan steps (0):`（空）、Stage Log 首行 `CREATED at SPEC stage for requirement: 新增 Agent 工具 export_plan...`，且**无** `Current step` 行 | 确认已建实例、尚无步骤 → 下一步 add_step |

---

### 阶段二：PLAN（计划步骤 → 进入 PLAN）

#### REG-4 分两步规划 export_plan

| 测试步骤 / 输入 | 具体预期 | 输出 → 下一流程 |
|------------------|----------|-----------------|
| 发送：`pipeline add_step pipeline_id=<PID> plan_steps="1. 新增 ExportPlanTool：读取计划并校验文件存在\n2. 实现生成 Markdown 导出文件并返回路径"` | 返回 `Added 2 plan step(s) to pipeline <PID>.`，提示 `Next: call advance_plan` | **实例中已有 2 步**；step_index：0=读取+校验，1=导出 Markdown |

#### REG-5 未加步骤前不能推进（防御）

| 测试步骤 / 输入 | 具体预期 |
|------------------|----------|
| 发送：`pipeline advance_plan pipeline_id=<PID>`（在 REG-4 之前执行） | 返回 `Add plan steps first (action=add_step) or you can pass plan_steps with advance_plan.`，阶段停留 SPEC |

#### REG-6 生成实施计划文档

| 测试步骤 / 输入 | 具体预期 | 输出 → 下一流程 |
|------------------|----------|-----------------|
| 发送：`generate plan source="新增 Agent 工具 export_plan，读取 docs/plans 计划并导出 Markdown 到 docs/plans/export"` | 返回 `=== Plan Generated ===`、`File: docs/plans/<slug>-plan.md`，含 Validation 信息与 Plan Preview（TDD 分步） | **记录计划文件名 `<PLAN_FILE>`**，作为 REG-7 的 plan_file 参数，且是 export_plan 之后要读取/导出的数据源 |

#### REG-7 推进到 PLAN

| 测试步骤 / 输入 | 具体预期 | 输出 → 下一流程 |
|------------------|----------|-----------------|
| 发送：`pipeline advance_plan pipeline_id=<PID> plan_file=<PLAN_FILE>` | 返回 `=== STAGE: PLAN ===`、`Plan file: <PLAN_FILE>`、`Steps: 2`、`Next: call start_tdd with step_index=0` | **阶段推进到 PLAN**。sequent status 中 `Plan:` 字段已填 → 下一步 start_tdd(0) |

---

### 阶段三：TDD（每步骤一个 TDD 循环）

#### REG-8 关联 TDD 与流水线（联动前提）

| 测试步骤 / 输入 | 具体预期 | 输出 → 下一流程 |
|------------------|----------|-----------------|
| 发送：`启动 TDD 循环并关联到流水线 pipeline_id=<PID>` | 返回 `Linked TDD cycle to spec pipeline <PID>.` | mark_refactor 时才能自动推进流水线 |

#### REG-9 开始第 1 个 TDD 循环（步骤0：读取+校验）

| 测试步骤 / 输入 | 具体预期 | 输出 → 下一流程 |
|------------------|----------|-----------------|
| 发送：`pipeline start_tdd pipeline_id=<PID> step_index=0` | 返回 `=== STAGE: TDD (step 1/2) ===`、`Task: 新增 ExportPlanTool：读取计划并校验文件存在`、提示运行 `tdd_enforcer start_cycle` | **currentStepIndex=0**。通知：先用 tdd 工具走 RED→GREEN→REFACTOR |

#### REG-10 TDD 循环 body：RED → REFACTOR

| 用例 | 测试步骤 / 输入 | 具体预期 | 输出 → 下一流程 |
|------|------------------|----------|-----------------|
| REG-10a | 发送：`tdd start_cycle task="ExportPlanTool 读取计划并校验"` | `=== TDD Cycle Started ===`、`State: RED`，提示先写失败测试 | state=RED → write_test |
| REG-10b | 发送：`tdd write_test`（实际执行：编写会失败的测试，先测"文件不存在时返回错误"） | state 推进 → RED_VERIFY，提示运行测试确认失败 | state=RED_VERIFY → verify_test |
| REG-10c | 发送：`tdd verify_test test_output="BUILD FAILED\n1 test failed: expected not-found error but got NPE"` | 测试按预期失败 → state=GREEN | state=GREEN → write_code |
| REG-10d | 发送：`tdd write_code`（执行：实现 `ExportPlanTool` 骨架 + `readPlan` 空值校验，复用 `PlanStore`） | state 推进 → GREEN_VERIFY | state=GREEN_VERIFY → verify_code |
| REG-10e | 发送：`tdd verify_code test_output="BUILD SUCCESSFUL\n3 tests passed"` | state=REFACTOR，附重构提示 | state=REFACTOR → mark_refactor |
| REG-10f | 发送：`tdd mark_refactor` | `=== TDD Cycle Complete ===` + **因已 REG-8 联动**，返回 `Spec pipeline <PID>: step 1 TDD done.` 及 `Next: start the next plan step's TDD cycle` | **流水线仍停留 TDD**（有 2 步，未全部完成）→ 下一步 REG-11 |

#### REG-11 开始第 2 个 TDD 循环（步骤1：导出 Markdown）

| 测试步骤 / 输入 | 具体预期 | 输出 → 下一流程 |
|------------------|----------|-----------------|
| 发送：`pipeline start_tdd pipeline_id=<PID> step_index=1` | 返回 `=== STAGE: TDD (step 2/2) ===`、`Task: 实现生成 Markdown 导出文件并返回路径` | **currentStepIndex=1** |

#### REG-12 TDD 循环 body：步骤1（同 REG-10）

| 用例 | 测试步骤 / 输入 | 具体预期 | 输出 → 下一流程 |
|------|------------------|----------|-----------------|
| REG-12a~e | 依次执行 `tdd start_cycle task="导出 Markdown"` → `write_test` → `verify_test(FAIL)` → `write_code`（实现写入 `docs/plans/export/`） → `verify_code(PASS)` | 状态依序 RED→RED_VERIFY→GREEN→GREEN_VERIFY→REFACTOR，全程无 Error | state=REFACTOR → mark_refactor |
| REG-12f | 发送：`tdd mark_refactor` | `=== TDD Cycle Complete ===` + **全部步骤完成** → 返回 `Spec pipeline <PID>: ALL plan steps done -> CHANGE.`，提示 `Next: create_change + implement + archive, then pipeline.link_change.` | **流水线自动推进到 CHANGE** → 下一步做 change |

---

### 阶段四：CHANGE（变更 → 结束）

#### REG-13 创建 OpenSpec 变更

| 测试步骤 / 输入 | 具体预期 | 输出 → 下一流程 |
|------------------|----------|-----------------|
| 发送：`create_change name=add-export-plan-tool` | 返回 `Created change: add-export-plan-tool`，含 `proposal.md`、`tasks.md`、`specs/` 三项说明 | 在 `openspec/changes/add-export-plan-tool/` 生成 scaffold → 预设填写 proposal/tasks/specs 内容（记录新增的 ExportPlanTool + 注册到 AgentExecutor） → 下一步 link_change |

#### REG-14 关联变更并完成流水线

| 测试步骤 / 输入 | 具体预期 | 输出 → 下一流程 |
|------------------|----------|-----------------|
| 发送：`pipeline link_change pipeline_id=<PID> change_name=add-export-plan-tool` | 返回 `Linked change 'add-export-plan-tool' to pipeline <PID>. Stage: DONE`（因当前为 CHANGE → 自动 advance 到 DONE） | **流水线进入 DONE** |

#### REG-15 归档收尾（可选，显式 DONE）

| 测试步骤 / 输入 | 具体预期 | 输出 → 下一流程 |
|------------------|----------|-----------------|
| 发送：`pipeline complete pipeline_id=<PID>` | 返回 `Pipeline <PID> marked DONE (requirement delivered through spec->plan->tdd->change).` | 阶段 DONE，从 active 列表消失 |

---

### 阶段五：收尾验证（回归）

#### REG-16 列表排除 DONE

| 测试步骤 / 输入 | 具体预期 |
|------------------|----------|
| 发送：`pipeline list` | `<PID>` 实例**不再出现**（`[DONE]` 被 `getActive()` 排除） |

#### REG-17 全程状态明细

| 测试步骤 / 输入 | 具体预期 |
|------------------|----------|
| 发送：`pipeline status pipeline_id=<PID>` | `Stage: DONE`、`Requirement:` 正确、`Plan: <PLAN_FILE>`、`Plan steps (2):` 两行均 `[X]`、`Current step: 2`、`Change: add-export-plan-tool`、Stage Log 含 SPEC→PLAN→TDD→CHANGE/多个 TDD DONE 记录 |

#### REG-18 新功能成果验证（export_plan 真实可用）

| 测试步骤 / 输入 | 具体预期 |
|------------------|----------|
| 发送：`export plan file=<PLAN_FILE> 导出这个计划` | 触发 `export_plan` → 返回含生成文件路径（如 `docs/plans/export/<name>-export.md`） |
| 用 IDE 打开生成的导出文件（或 `read_file path=docs/plans/export/<name>-export.md`） | 文件存在且内容与 `<PLAN_FILE>` 计划一致、Markdown 排版正确 |
| 发送：`export plan file=不存在的计划.md` | 返回错误提示计划不存在（`Plan '<file>' not found`），不产生文件 |

#### REG-19 完整无误回归清单

> 走完 REG-1~REG-18，最终断言：
> - 阶段依序 `SPEC → PLAN → TDD → CHANGE → DONE`，**无任何 Error**。
> - `pipeline_id`、`plan_file`、`step_index`、`change_name` 在各步骤间正确传递。
> - TDD 两步各自 `verify_test(FAIL)` + `verify_code(PASS)` 状态机完整。
> - 新工具 `export_plan` 已在 `AgentExecutor` 注册（`TOOL_KEYWORDS` 命中 `export plan`/`导出计划`），能将 `<PLAN_FILE>` 正确导出为 `docs/plans/export/` 下的 Markdown，且对不存在的计划返回错误。
> - 产物齐全：`docs/plans/<PLAN_FILE>`、`docs/plans/export/`、`openspec/changes/add-export-plan-tool/`。

---

## 验证结果记录（勾选）

> 复制此表到验证过程，逐条标注：PASS（通过）/ FAIL（失败）/ N/A（不适用）+ 备注。

| 用例 | 结果 | 备注 |
|------|------|------|
|      |      |      |

（请按需复制多行，对应上面各用例编号填写。）
