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
| `pipeline start requirement="用户登录" spec_domain=auth` | 返回 `=== SPEC PIPELINE STARTED ===`，含：`Pipeline ID: <uuid>`、`Requirement: 用户登录`、`Stage: SPEC`、`Spec doc: openspec/specs/auth/spec.md`，并提示保存 pipeline_id | **输出的 pipeline_id 是后续所有 add_step / advance_plan / start_tdd 的必传参数**，测试者必须复制保存 |

**落盘断言（P-01 的真正判据，气泡文本不作断言）：**
- ① `.idea/myplugin-pipeline.json` 以 `<uuid>` 为 key 的实例，`stage=SPEC`。
- ② `.idea/myplugin-conversations.json` 中 ASSISTANT 消息的 `thinking` 字段含原始工具输出 `[Tool Result] sdd_pipeline: === SPEC PIPELINE STARTED ===`（LLM 会把工具输出包装成叙述文，最终气泡不可作文本断言）。
- ③ `openspec/specs/auth/spec.md` 已生成（含 `## Purpose` 与 `### Requirement: 用户登录`），可被 `list_specs` 解析——这是 SPEC 阶段**落盘的 SDD 产物**。

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
| `generate plan source="<P-01需求文本>"` | 返回生成的 TDD 格式分步计划 + 计划文件路径（`docs/plans/...`）；thinking 字段必须出现 `[Tool Call] generate_plan`（硬调度：含 `generate plan` 等强绑定词时候选集收窄，模型无法漂移到 read_file） | **plan 文件路径可作为 advance_plan 的 plan_file 参数**，让流水线记录计划文件位置 |

> 兜底：即使本次模型仍不调用 `generate_plan`，`advance_plan`（P-07）在缺 `plan_file` 时会自动生成计划文档，PLAN 产物不缺失。

### P-07 推进到 PLAN

| 输入 | 具体预期 | 输出 → 下一流程 |
|------|----------|-----------------|
| `pipeline advance_plan pipeline_id=<id> plan_file=docs/plans/xx.md` | 返回 `=== STAGE: PLAN ===`，含 `Plan file: docs/plans/xx.md`、`Steps: 2`、`Next: call start_tdd with step_index=0` | **阶段从 SPEC 推进到 PLAN，并记录了计划文件**。验证 status 时 `Plan:` 字段已被填入 → 下一流程是 start_tdd(0) |
| `pipeline advance_plan pipeline_id=<id>`（不带 plan_file） | `Plan file:` 显示自动生成的文件名（兜底行为，文件写入 `docs/plans/<date>-<slug>-plan.md`） | 效果等价 |

---

## 阶段三：TDD（每步骤一个 TDD 循环）

**联动前提（P-08a）：** 要让 TDD 循环结束时**自动推进**流水线，必须先执行 `tdd link_pipeline pipeline_id=<id>`（中文可发：`启动 TDD 循环并关联到流水线 pipeline_id=<id>`，强绑定命中 `tdd_enforcer`）；`tdd start_cycle` 若带 `pipeline_id` 也等同联动。

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
4. **触发词面向"生成"动作**：`generate_plan` 需含 `generate plan` / `create plan` / `write plan` 等词。单纯的"列出实施计划"这类**列出**请求不会命中，模型只会给出自由文本（可借助核心工具 `list_files`），**无固定格式、不可作为回归断言**。若要走工具列出，需使用含 `generate plan` 的说法。
5. **`complete_tdd` 需要 `step_index` 等于当前 `currentStepIndex`**，否则返回 Error（`completeStepTdd` 校验）。
6. **非法阶段迁移会被拒绝**：例如在 SPEC 阶段直接 `complete_tdd` 会因 currentStepIndex 不匹配而返回 Error。

---

## 完整示例：新功能开发全流程测试（以新增 Agent 工具 `export_plan` 为例）

> 完整示例（REG-0 ~ REG-19，含需求说明、分步实现、逐条输入与预期输出、回归清单）已迁移至 **`docs/USAGE.md` 第 7 节**，此处不再重复。执行时请参考该节，并按上面的 P/E2E 用例编号填入"验证结果记录"。

---

## 验证结果记录（勾选）

> 复制此表到验证过程，逐条标注：PASS（通过）/ FAIL（失败）/ N/A（不适用）+ 备注。

| 用例 | 结果 | 备注 |
|------|------|------|
|      |      |      |

（请按需复制多行，对应上面各用例编号填写。完整示例的 REG-0~REG-19 见 `docs/USAGE.md` 第 7 节。）