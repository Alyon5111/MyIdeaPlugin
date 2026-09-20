# SDD → Plan → TDD → Change 流程测试用例（MyIdeaPlugin）

> 用途：验证规格驱动开发（Spec-Driven Development）全流程，阶段依序推进并正确流转数据。
> 方式：在 IntelliJ 中运行插件（`runIde`），用**两个操作入口**验证：
> 1. **文本命令通道（Chat 面板）**——用自然语言触发关键词调用对应工具。
> 2. **Pipeline 操作面板（Pipeline tab）**——实例列表 + 按钮，选中实例后点按钮执行（零输入）。
> 依据：以真实代码行为为准（`SDDPipelineTool` / `PipelineService` / `TddEnforcerTool` / `SpecPipelineInstance` / `PipelinePanel`）。

## 两个操作入口

| 入口 | 位置 | 特性 |
|------|------|------|
| 文本命令通道 | 工具窗口 `Chat` tab | 所有工具都可用，参数由 LLM 推断；需关键词触发（见下） |
| Pipeline 操作面板 | 工具窗口 `Pipeline` tab | **零输入**：顶部实例列表 + 摘要，中间按钮，底部输出区；`Start / Add Steps / Link Change 需手填参数 → 走文本通道` |

> 面板按钮是**硬调度**：点击直接调用对应工具动作，不经 LLM 选择，输出显示在面板底部输出区。**改动后点 `Refresh`** 让列表刷新。

### 工具调用机制（重要前提，仅影响文本通道）

- 插件**没有** `/toolname action=...` 命令路由。工具选择由 `AgentExecutor.selectToolsForMessage()` 依据**用户消息中的关键词**决定，工具的 action/参数由 LLM 从自然语言推断并填成 JSON。
- 每个用例的"输入"必须包含对应触发关键词，否则工具不会被选中、无输出：
  - `sdd_pipeline` → 关键词含 `pipeline` / `spec` / `advance tdd` / `next stage`
  - `tdd_enforcer` → 关键词含 `tdd`
  - `generate_plan` → 关键词含 `generate plan` / `implementation plan`
  - `create_change` / `link_change` → 关键词含 `change`
- `pipeline_id` 是贯穿全流程的主键，`start` 的输出必须保存供后续每个步骤复用；面板列表中选中任意实例即获得其 id（摘要行显示）。

## 阶段总览

```
SPEC → PLAN → TDD → CHANGE → DONE
```

- 一次 PLAN 步骤 = 一个 TDD 循环（RED→RED_VERIFY→GREEN→GREEN_VERIFY→REFACTOR→DONE）。
- 两个协同工具：
  - **`sdd_pipeline`**（`SDDPipelineTool`）：管理流水线实例与阶段流转。
  - **`tdd_enforcer`**（`TddEnforcerTool`）：管理每步骤的 TDD 状态机；`link_pipeline` 后可联动自动推进流水线。

---

## 阶段〇：Pipeline 操作面板（UI 通道，零输入）

> 前置：已运行插件，工具窗口出现 `Chat / Settings / Pipeline` 三个 tab。面板结构：
> 顶部 = 实例列表（`[stage] 需求前30字 (domain)`）+ `Refresh` + 摘要行；中部 = 按钮行；底部 = 输出区。
> 面板为**状态驱动**：按钮可用性由"当前选中实例的阶段 + 步骤"决定（见 P-UI5 表）。

### P-UI0 面板加载（打开即验证）

| 输入 | 具体预期 |
|------|----------|
| 打开工具窗口，点击 `Pipeline` tab | 出现列表（可空）、`Refresh` 按钮、摘要行、按钮行、输出区；无任何输入框 |

### P-UI1 列表显示实例内容

| 前置 | 输入 | 具体预期 |
|------|------|----------|
| 用文本通道 `pipeline start requirement=... spec_domain=agent-tools` 建实例 | 点击 `Refresh` | 列表出现一行，格式含 `[SPEC] 需求前30字 (agent-tools)`；悬停有 tooltip 显示完整 ID/stage/spec/requirement/步骤数 |

> 列表与文本通道共享同一存储（`.idea/myplugin-pipeline.json`），先建实例后面板才能列出。

### P-UI2 选中实例 → 摘要刷新

| 输入 | 具体预期 |
|------|----------|
| 点击列表里某条实例 | 摘要行更新为该实例信息：`[SPEC] step 1/0 · 需求前40字`；按钮按当前阶段启用/禁用（见 P-UI5） |

### P-UI3 选中后点按钮执行 → 底部输出工具结果

| 前置 | 输入 | 具体预期 |
|------|------|----------|
| 处于 SPEC 且已 `add_step`（文本通道） | 选中实例 → 点 `Advance Plan` | 底部输出 `=== STAGE: PLAN ===`，其中 `Plan file:` 为**自动生成的计划文件名**（面板不传 plan_file，工具自动写 `docs/plans/<date>-<slug>-plan.md`）；**无需手动 Refresh**，列表该行立即变 `[PLAN] ...`（面板每次点按钮后自动刷新，见 P-UI15） |

### P-UI4 多实例：列表区分选中

| 前置 | 输入 | 具体预期 |
|------|------|----------|
| 存在 2+ 实例（不同阶段） | 点不同条目 | 摘要与按钮状态分别随选中实例变化；`Status` 只作用于选中实例（输出含其 ID） |

### P-UI5 按钮可用性（状态驱动，对照表）

| 实例阶段 | Advance Plan | Start TDD | Complete TDD | Complete | Export Plan | Status | List | Create Change | Link Change |
|----------|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| SPEC | ✅ | ⛔ | ⛔ | ⛔ | ✅ | ✅ | ✅ | ✅ | ✅ |
| PLAN | ⛔ | ✅(auto step 0) | ⛔ | ⛔ | ✅ | ✅ | ✅ | ✅ | ✅ |
| TDD | ⛔ | ✅(start next step) | ✅ | ⛔ | ✅ | ✅ | ✅ | ✅ | ✅ |
| CHANGE | ⛔ | ⛔ | ⛔ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| 未选中 | ⛔ | ⛔ | ⛔ | ⛔ | ✅ | ⛔ | ✅ | ✅ | ⛔ |

> `Export Plan` 与 `List` 不依赖选中项（始终可用）；`Create Change` 需要手填参数 → 点击仅提示走文本通道；`Link Change` 需要手填 → 点击提示走文本通道（不真正执行）。

### P-UI6 Start TDD 自动取 step_index

| 前置 | 输入 | 具体预期 |
|------|------|----------|
| PLAN 阶段、已 add 2 步 | 选中 → 点 `Start TDD` | 输出 `=== STAGE: TDD (step 1/2) ===`（自动 step_index=0）；列表变 `[TDD] ...` |
| TDD 阶段、当前 step 0 已做完（文本通道 mark_refactor） | 点 `Start TDD` | 自动取 **currentStepIndex+1**（step 2/2） |
| TDD 阶段、所有 step 已开始 | 点 `Start TDD` | 输出提示 `All plan steps already started.`，不越界 |

### P-UI7 Complete TDD 需要证据

| 前置 | 输入 | 具体预期 |
|------|------|----------|
| TDD 阶段、未跑任何 tdd 循环 | 点 `Complete TDD` | 输出含 `no TDD evidence`（防伪证据门），阶段不变 |

### P-UI8 Complete（CHANGE → DONE）

| 前置 | 输入 | 具体预期 |
|------|------|----------|
| CHANGE 阶段 | 选中 → 点 `Complete` | 输出 `Pipeline <id> marked DONE`；`Refresh` 后该实例从列表消失（active 排除 DONE） |

### P-UI9 手填按钮走文本通道

| 输入 | 具体预期 |
|------|----------|
| 点 `Create Change` | 输出提示 `requires manual parameters ... use the text command channel（如 change create ...）`，**不**执行创建 |
| 点 `Link Change` | 输出提示类似 `Start / Add Steps / Link Change ... use the text command channel`，不执行 |

### P-UI10 双通道 trace 记录（需求5 回归）

| 前置 | 输入 | 具体预期 |
|------|------|----------|
| 用**文本通道**跑一步 `pipeline` 工具 | 查看 `.idea/myplugin-conversations.json` | 存在 channel="text" 的 `TraceEntry`，含 toolName/arguments/result/timestamp |
| 用**面板**点 `Status` | 查看同一 JSON | 新增一条 channel="ui" 的 `TraceEntry`，含 toolName=sdd_pipeline |

> Trace 写入 `Conversation.addTrace(...)`（`TraceRecorder`），文本通道在 Chat 的 onToolCall 记录，面板在按钮执行后记录。

### P-UI11 Refresh 同步面板与文本通道状态

| 输入 | 具体预期 |
|------|----------|
| 文本通道完成 `link_change`（→DONE） | 面板列表若不刷新仍是旧阶段；点 `Refresh` 后该实例消失 | 面板是**快照驱动**：文本通道的变更需手动点 `Refresh` 才可见；而**面板自身点按钮后会立即自动刷新**（见 P-UI15），两者不对称，核验时注意区分 |

### P-UI12 点按钮后自动刷新 + 选中项重置

| 前置 | 输入 | 具体预期 |
|------|------|----------|
| 列表有 2 条实例，当前选中**第 2 条** | 对第 2 条点 `Advance Plan`（使其 SPEC→PLAN） | ① 输出区出现工具结果；② **不等 Refresh**，列表第 2 行标签立即变为 `[PLAN] ...`；③ 选中项**重置为第 1 条**（面板每次刷新后 `setSelectedIndex(0)`），摘要行随之变成第 1 条实例的信息 |

> 这是已知交互特性（非缺陷）：面板刷新后固定选中第一条。若第 1、2 条阶段不同，刷新后按钮可用性会随第 1 条阶段变化——核验时按"选中项已重置"理解，不要当成状态丢失。

### P-UI13 长需求截断与 (no-spec) 显示

| 前置 | 输入 | 具体预期 |
|------|------|----------|
| 文本通道 `pipeline start` 一条**超过 30 字符**的 requirement（如 50 个字符） | Refresh → 查看列表该行 | 列表标签的需求部分只显示**前 30 字符 + `...`**，格式 `[SPEC] <30字>... (domain)` |
| 选中该实例 | 查看摘要行 | 摘要显示**前 40 字符 + `...`**（比列表多 10 字） |
| 悬停该行 | tooltip | 显示**完整** requirement、ID、Stage、Spec、Steps（tooltip 不截断） |
| 文本通道 `pipeline start` 一条**不带 spec_domain** 的实例 | Refresh → 查看列表 | 该行 domain 显示 `no-spec`（而非空白），格式 `[SPEC] <需求> (no-spec)` |

### P-UI14 空状态与输出区格式

| 前置 | 输入 | 具体预期 |
|------|------|----------|
| 无任何活跃实例（首次或全部 DONE） | 打开 Pipeline tab | 列表为空；摘要行显示 `No active pipeline instance selected.`（面板打开时列表为空，选中为空，故为"selected"文案） |
| 任一状态 | 点 `Status` 或 `List` 等任意会输出结果的按钮 | 每次工具结果追加在输出区末尾，结果块之间以 `-------------------------------`（27 个短横线）分隔；输出区**不可编辑** |
| 工具执行抛异常（难以人工构造，可选） | — | 输出区出现 `Error: <异常消息>` 且面板不崩溃（`run()` 统一捕获） |

### P-UI15 List 按钮（面板通道）

| 前置 | 输入 | 具体预期 |
|------|------|----------|
| 存在 2 条不同阶段实例（如 1 条 SPEC、1 条 PLAN） | 选中任意实例 → 点 `List` | 输出区显示 `=== SPEC-DRIVEN PIPELINE ===` + 每活跃实例一行 `- [STAGE] 需求 (spec: domain)`；**DONE 实例不出现**；不依赖选中项（未选中也可点） |
| 无任何活跃实例 | 点 `List` | 输出 `No active pipeline instances. Use pipeline.start to begin.` |

### P-UI16 Status 按钮（面板通道，明细）

| 前置 | 输入 | 具体预期 |
|------|------|----------|
| 选中一条已 add_step 并推进到 PLAN 的实例 | 点 `Status` | 输出区显示 `=== PIPELINE INSTANCE ===` 明细：`ID:`、`Stage: PLAN`、`Requirement:`、`Spec:`、`Plan:`、`Plan steps (N):`（每步带 `[X]`/`[>]`/`[ ]` 标记）、`Change:`（若已 link）、`--- Stage Log ---` 含 `CREATED at SPEC stage` 与 `ADVANCED to PLAN` 记录 |
| 未选中实例 | — | `Status` 按钮**置灰不可点**（面板不发送无 id 的 status；要看全量状态请用文本通道 `pipeline status`，见 P-T02） |

### P-UI17 Export Plan 按钮（面板通道）

| 前置 | 输入 | 具体预期 |
|------|------|----------|
| `docs/plans/` 下**没有任何**计划文件 | 点 `Export Plan` | 输出 `Error: no plans in docs/plans. Use generate_plan first.` |
| `docs/plans/` 下**已有**计划（P-06 或 P-07 自动生成过） | 点 `Export Plan` | ① 输出 `=== Plan Exported ===`、`Source: docs/plans/<name>.md`、`Exported file: docs/plans/export/<name>-export.md` + 渲染内容前 1200 字符；② 面板**不传 file 参数** → 导出的是 `docs/plans/` 下**最近一份**计划；③ 落盘断言：`docs/plans/export/<name>-export.md` 存在，内含 `## Task/Step Summary`、`Tasks: N, Steps: M` 统计与 `## Full Plan Content` 原文 |

> 面板 `Export Plan` 是零输入快捷方式；要指定导出**某一份**计划，请用文本通道 `export plan file=<name>.md`（见 P-T06）。

### P-UI18 面板按钮与文本通道输出一致性

| 前置 | 输入 | 具体预期 |
|------|------|----------|
| 同一 SPEC 实例、已 add_step | 面板点 `Advance Plan` 得到输出 A；再对**另一条**同条件实例用文本通道 `pipeline advance_plan pipeline_id=<id>` 得到输出 B | A 与 B 的工具原始输出一致（都是 `=== STAGE: PLAN ===` 且 Plan file 为各自自动生成的文件名）；面板输出区直接显示工具原始字符串，文本通道由 LLM 转述（可能包装叙述文），**断言以面板输出与落盘为准** |

### P-UI19 面板操作写入 ui 通道 trace

| 前置 | 输入 | 具体预期 |
|------|------|----------|
| 当前会话存在（Chat tab 有过对话） | 面板点 `Status`、`List` 各一次 | `.idea/myplugin-conversations.json` 当前会话的 trace 新增 2 条 `channel="ui"` 记录：toolName=`sdd_pipeline`、arguments 含 `pipeline_id`（Status）/`action=list`（List）、result 与面板输出一致、有 timestamp |
| 当前会话**不存在**（刚重启 IDE 未开新对话） | 面板点 `Status` | 工具仍正常执行、输出正常显示；trace 写入静默跳过（不崩溃、无 Error 输出）——trace 依赖当前会话存在 |

---

## 阶段一：SPEC（需求 → 流水线实例）

### P-01 启动流水线

| 输入 | 具体预期 | 输出 → 下一流程 |
|------|----------|-----------------|
| `pipeline start requirement="新增 Agent 工具 export_plan：读取 docs/plans 下计划并导出为 Markdown 到 docs/plans/export" spec_domain=agent-tools` | 返回 `=== SPEC PIPELINE STARTED ===`，含：`Pipeline ID: <uuid>`、`Requirement: 新增 Agent 工具 export_plan...`、`Stage: SPEC`、`Spec doc: openspec/specs/agent-tools/spec.md`，并提示保存 pipeline_id | **输出的 pipeline_id 是后续所有 add_step / advance_plan / start_tdd 的必传参数**，测试者必须复制保存 |

**落盘断言（P-01 的真正判据，气泡文本不作断言）：**
- ① `.idea/myplugin-pipeline.json` 以 `<uuid>` 为 key 的实例，`stage=SPEC`。
- ② `.idea/myplugin-conversations.json` 中 ASSISTANT 消息的 `thinking` 字段含原始工具输出 `[Tool Result] sdd_pipeline: === SPEC PIPELINE STARTED ===`（LLM 会把工具输出包装成叙述文，最终气泡不可作文本断言）。
- ③ `openspec/specs/agent-tools/spec.md` 已生成（含 `## Purpose` 与 `### Requirement: 新增 Agent 工具 export_plan...`），可被 `list_specs` 解析——这是 SPEC 阶段**落盘的 SDD 产物**。

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
| `pipeline add_step pipeline_id=<id> plan_steps="扫描 docs/plans 目录\n导出 Markdown 报告"` | 返回 `Added 2 plan step(s) to pipeline <id>.`，并提示 `Next: call advance_plan` | **plan_steps 被存入实例，共 2 步**。这是后续 start_tdd 的 step_index 依据（0=扫描 docs/plans 目录，1=导出 Markdown 报告） |

备注：`plan_steps` 按换行拆分，空行会被忽略（`addStep` 过滤空白行）。

### P-05 缺步骤推进（防御）

| 输入 | 具体预期 | 备注 |
|------|----------|------|
| `pipeline advance_plan pipeline_id=<id>`（此时**未** add_step） | 返回 `Add plan steps first (action=add_step) or you can pass plan_steps with advance_plan.`，**阶段仍停留 SPEC** | 验证"无步骤不可推进"的护栏。**注意**：提示文案虽提到可随 advance_plan 传 plan_steps，但代码 `advancePlan()` 并不读取该参数——实际仍需先 `add_step`（文案与实现不一致，属已知小瑕疵，核验时按"必须 add_step"为准） |

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
| P-08b | `tdd start_cycle task="扫描 docs/plans 目录"` | `=== TDD Cycle Started ===`，Task、Cycle #、`State: RED`，提示先写失败测试 | **state=RED** → 下一步 write_test |
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
| `pipeline start_tdd pipeline_id=<id> step_index=1` | 返回 `=== STAGE: TDD (step 2/2) ===`、`Task: 导出 Markdown 报告`、提示 `Use 'tdd_enforcer start_cycle' with this task...complete_tdd with step_index=1` | **currentStepIndex 更新为 1** → 再跑一轮 P-08b~g，mark_refactor 后 allStepsDone → CHANGE |

（也可用 `pipeline complete_tdd step_index=<当前>` 手动完成步骤，但推荐 tdd mark_refactor 联动。）

---

## 阶段四：CHANGE（变更 → 结束）

### P-12 关联变更并完成

| 前置 | 输入 | 具体预期 | 输出 → 下一流程 |
|------|------|----------|-----------------|
| 流水线已到 CHANGE（所有 TDD 步完成） | `pipeline link_change pipeline_id=<id> change_name=export-plan-tool` | 返回 `Linked change 'export-plan-tool' to pipeline <id>. Stage: CHANGE`（因当前是 CHANGE → 自动 advance 到 DONE） | **流水线进入 DONE**。change 应已配合 openspec 的 create_change 在 `openspec/changes` 创建 |

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

## 面板补测：工具层行为（文本通道执行，覆盖面板按钮够不到的参数/分支）

> 以下用例走**文本通道**（关键词：`pipeline` / `spec` / `change`），用于覆盖 `sdd_pipeline` 工具中面板按钮不会触发的分支。断言均针对工具原始输出（thinking 字段中的 `[Tool Result] sdd_pipeline: ...` 或落盘文件），LLM 转述气泡不作断言。

### P-T01 start：无 spec_domain / spec 已存在 / note_zh

| 用例 | 输入 | 具体预期 |
|------|------|----------|
| P-T01a | `pipeline start requirement="No domain requirement"`（不带 spec_domain） | 输出含 `Note: no spec_domain given, no spec doc generated.`；**不创建** `openspec/specs/` 目录；面板列表中该实例显示 `no-spec`（见 P-UI13） |
| P-T01b | 先执行 P-01（agent-tools 域 spec 已生成），再 `pipeline start requirement="Second agent-tools req" spec_domain=agent-tools` | 输出含 `openspec/specs/agent-tools/spec.md (already exists)`；磁盘上 `spec.md` **保持第一次的内容不被覆盖**（读文件比对） |
| P-T01c | `pipeline start requirement="User can reset password" spec_domain=reset note_zh="密码重置备注"`（用新 domain 避免 P-T01b 干扰） | 生成的 `openspec/specs/reset/spec.md` 正文保持英文，**末尾**含 `> Note (Chinese): 密码重置备注` |

### P-T02 status：不带 pipeline_id（全量明细）

| 用例 | 输入 | 具体预期 |
|------|------|----------|
| P-T02a | 存在 2 条活跃实例 | `pipeline status`（不给 id） | 输出为两条 `=== PIPELINE INSTANCE ===` 明细拼接（各自含 ID/Stage/Stage Log），顺序为创建顺序 |
| P-T02b | 无任何活跃实例 | `pipeline status` | 输出 `No active pipeline instances. Use pipeline.start to begin.` |

> 面板 `Status` 按钮必须选中实例（无 id 分支只存在于文本通道）——见 P-UI16。

### P-T03 next_step（面板无此按钮）

| 用例 | 前置 | 输入 | 具体预期 |
|------|------|------|----------|
| P-T03 | 实例依次处于各阶段 | `pipeline next_step pipeline_id=<id>` | SPEC → `Next: add plan steps (action=add_step), then call advance_plan.`；PLAN → `Next: call start_tdd with step_index=0...`；TDD → 含当前步任务文本与 `complete_tdd` 提示；CHANGE → `create_change + implement + archive, then call link_change`；DONE → `This requirement is complete.` |

### P-T04 非法阶段推进 / 未知实例

| 用例 | 输入 | 具体预期 |
|------|------|----------|
| P-T04a | `pipeline advance_plan pipeline_id=<id>`（实例**已在 PLAN**） | `Can only advance to PLAN from SPEC. Current: PLAN`，阶段不变 |
| P-T04b | `pipeline start_tdd pipeline_id=<id> step_index=0`（实例仍在 **SPEC**） | `Can only start TDD from PLAN/TDD. Current: SPEC` |
| P-T04c | `pipeline status pipeline_id=not-exist-id` | `Pipeline instance not found: not-exist-id`（status 对未知 id 返回 not found，而非崩溃） |

### P-T05 start_tdd step_index 越界

| 用例 | 前置 | 输入 | 具体预期 |
|------|------|------|----------|
| P-T05 | PLAN 阶段、2 步 | `pipeline start_tdd pipeline_id=<id> step_index=5` | `Invalid step_index 5 (plan has 2 steps).`，currentStepIndex 不变 |

> 面板 `Start TDD` 自动取 next 且先检查 `next >= size`（P-UI6 的 `All plan steps already started.`），面板侧不会发出越界 step_index；此分支只能文本通道触达。

### P-T06 complete_tdd 步号不匹配 / export 指定文件

| 用例 | 前置 | 输入 | 具体预期 |
|------|------|------|----------|
| P-T06a | TDD 阶段，currentStepIndex=0（step 0 有证据） | `pipeline complete_tdd pipeline_id=<id> step_index=1` | `Cannot complete step 1 (not the current step, or invalid index).`（证据校验通过但 `currentStepIndex != stepIndex` 被拒） |
| P-T06b | `docs/plans/` 有 ≥2 份计划 | `export plan file=<较旧那份的文件名>.md` | 输出 `=== Plan Exported ===`，`Source:` 为指定文件（而非最新那份）；`docs/plans/export/<该文件>-export.md` 落盘 |

### P-T07 未知 action

| 用例 | 输入 | 具体预期 |
|------|------|----------|
| P-T07 | `pipeline action=foo pipeline_id=<id>`（诱导模型传 action=foo） | 工具返回 `Unknown action: foo. Use: start, add_step, advance_plan, start_tdd, complete_tdd, link_change, complete, status, list, next_step` |

> LLM 可能不照传 `foo`（自行纠正为合法 action），此用例**以 thinking 中实际 tool call 参数与工具原始输出为准**；若模型改传合法 action，记 N/A 并备注。

---

## 端到端冒烟测试（回归重点）

### E2E-01 完整 SDD 流水线（单步骤，文本通道）

| 执行序列 | 阶段流转 |
|----------|----------|
| `pipeline start ...` → `pipeline add_step(1步)` → `pipeline advance_plan` → `tdd link_pipeline` → `tdd start_cycle` → `tdd write_test` → `tdd verify_test(FAIL)` → `tdd write_code` → `tdd verify_code(PASS)` → `tdd mark_refactor` → `pipeline link_change` → `pipeline complete` | `SPEC → PLAN → TDD → CHANGE → DONE`，全程无 Error，每步输出可被下一步使用 |

### E2E-02 完整 SDD 流水线（多步骤）

| 执行序列 | 阶段流转 |
|----------|----------|
| 与 E2E-01 相同，但 `add_step` 含 2 步；第 1 步的 `mark_refactor` 后停留 TDD，`pipeline start_tdd step_index=1` 完成第 2 步后再进 CHANGE | 验证中间步骤不提前推进：`SPEC → PLAN → TDD → (step0 → TDD) → (step1 → TDD allDone → CHANGE) → DONE` |

### E2E-03 混合通道全流程（面板 + 文本协同，重点回归）

> 验证面板不依赖手填参数也能完整驱动流水线（除 add_steps / TDD 循环外全面板操作）。

| 步骤 | 入口 | 操作 | 预期 |
|------|------|------|------|
| 1 | 文本 | `pipeline start requirement="新增 Agent 工具 export_plan：读取 docs/plans 下计划并导出为 Markdown 到 docs/plans/export" spec_domain=agent-tools` | SPEC 实例已建 |
| 2 | 面板 | `Refresh` → 选中该实例 → 摘要 `[SPEC]` | 列表/摘要正确 |
| 3 | 文本 | `pipeline add_step pipeline_id=<id> plan_steps="步1\n步2"` | 加入 2 步 |
| 4 | 面板 | 点 `Advance Plan` | `[PLAN]` |
| 5 | 面板 | 选中 → 点 `Start TDD` | 自动 step_index=0，`[TDD] (step 1/2)` |
| 6 | 文本 | `tdd link_pipeline` + 完整 RED→...→REFACTOR 循环 | step1 完成，仍 `[TDD]` |
| 7 | 面板 | 点 `Start TDD` | 自动 next step，`step 2/2` |
| 8 | 文本 | 第 2 步 TDD 循环 `mark_refactor` | allDone → `[CHANGE]` |
| 9 | 面板 | 点 `Complete` | `DONE`，Refresh 后列表消失 |
| 10 | 磁盘 | 查 `.idea/myplugin-conversations.json` | 同时存在 channel="text"（步骤1/3/6/8）与 channel="ui"（步骤4/5/7/9）trace |

### E2E-04 面板防御（非法操作不越界）

| 执行序列 | 预期 |
|----------|------|
| SPEC 阶段点 `Complete TDD`/`Start TDD` | 按钮不可用（灰）或输出 Error 提示，阶段不变 |
| TDD 阶段未跑循环点 `Complete TDD` | 提示 `no TDD evidence` |
| 未选中实例时查看各按钮 | `Start TDD`/`Complete`/`Complete TDD`/`Status`/`Link Change`/`Advance Plan` 全部置灰不可点；仅 `Export Plan`/`List`/`Create Change` 可点（`Create Change` 点出走文本通道提示）；输出区**不会**出现工具调用（置灰按钮无法触发；`Select a pipeline instance first.` 提示仅在选中态失效的极端时序下出现） |

---

## 阶段五：Chat Stop 按钮（流式中断）

> 目标：`Stop` 按钮在**任何模式下中断当前生成**，并正确恢复 UI 状态。
> 前置：插件已运行，Chat tab 可见，底部有 `Send` / `Stop` 两个按钮，模型可用（本地 llama.cpp 已起）。
> 说明：Stop 涉及两个执行路径——**非 Agent 流式**（`LlamaChatStreamClient`，勾选「流式输出」）与 **Agent 模式**（勾选 Agent 开关）。下面用例区分标注。
> **当前已知状态**：Stream 模式与 Agent 模式的 `currentStreamThread`/`agentThread` 接线不完整，`stopStreaming()` 主要依赖 `currentStreamThread.interrupt()`（仅非流式路径赋值），流式与 Agent 路径中断不生效——用例即验收实现。

### S-01 初始状态（未请求时）

| 输入 | 预期（通过标准） | 备注 |
|------|------------------|------|
| 打开 Chat，未发送任何消息 | `Stop` **灰置不可点**；`Send` 可用；输入框可输入 | 初始化 `stopButton.setEnabled(false)` |

### S-02 发送后进入生成态

| 输入 | 预期（通过标准） |
|------|-------------------|
| 在输入框输入一条普通消息（如 `解释这个项目的架构`），点 `Send` | 请求发起瞬间：`Stop` 变为**可用**、`Send` **变灰**（同一时刻互斥）；状态栏显示 `Agent working...` 或正在输出 |

### S-03 非 Agent 流式输出中途点 Stop（核心缺陷场景）

| 前置 | 输入 | 预期（通过标准） | 当前实际 |
|------|------|------------------|----------|
| 勾选「流式输出」（Stream Mode）+ **未**勾选 Agent；模型输出足够长（可要求"写一份 3 段长文"） | 输出进行中（文本仍在跳动）点 `Stop` | ① 输出**立即停止追加**，末尾文本冻结不动；② `Stop` 变灰、`Send` 与输入框立即恢复可用；③ 不再产生新的后端请求；④ 无异常弹窗 | Stream 路径 `currentStreamThread` 未被赋值，`stopStreaming()` 的 interrupt 落空 → 文本继续输出、UI 保持生成态 → **FAIL** |

### S-04 Agent 模式应答中途点 Stop

| 前置 | 输入 | 预期（通过标准） | 当前实际 |
|------|------|------------------|----------|
| 勾选 Agent；发一条会让 Agent 多次思考/多工具调用的请求（如 `pipeline status 然后解释`） | Agent 仍在迭代（thinking/工具卡片还在追加）时点 `Stop` | ① 不再发起**新的** tool call；② 答案区追加 `[Agent stopped by user]`；③ `Stop` 变灰、`Send` 恢复；④ 会话被保存（对话仍含该次消息），可继续发下一条 | `stopStreaming()` 同时路径下 `currentStreamThread` 为空、未调用 `setStreaming(false)` → UI 保持生成态，可能直到 Agent 自然结束才恢复 → **FAIL 或依赖 Agent 自然结束** |

### S-05 停止后立即再发消息（状态复位）

| 前置 | 输入 | 预期（通过标准） | 备注 |
|------|------|------------------|------|
| 完成 S-03 或 S-04 的 Stop | 不重启插件，直接输入新消息点 `Send` | `Stop` 重新可用、`Send` 变灰，新一轮生成正常开展；上一次不会"复活"追加 | 若上一层 Stop 未复位 UI，此处会卡在 Send 灰置 → FAIL |

### S-06 Stop 幂等（重复点击不崩）

| 前置 | 输入 | 预期（通过标准） |
|------|------|------------------|
| 处于生成态（S-02） | 连续快速点 `Stop` 2~3 次 | 无异常、无重复输出；最终 UI 收敛到停止态（Stop 灰、Send 可用）；第一次点击后 Stop 应立即置灰（第二次点击实际点不到） |

### S-07 非流式模式下 Stop

| 前置 | 输入 | 预期（通过标准） |
|------|------|------------------|
| **取消**「流式输出」；发一条长请求 | 输出等待期间点 `Stop` | 请求被中断（无完成回调继续写 UI），UI 立即复位、无残留生成态 |

### S-08 停止后会话持久化

| 前置 | 输入 | 预期（通过标准） |
|------|------|------------------|
| 完成 S-03 / S-04 的 Stop | 查看 `.idea/myplugin-conversations.json` | 该会话存在；含被停止的那次 user/assistant 消息（流水线/工具 trace 正常写入）；若 Stop 发生于 Agent 工具调用中，trace 中该工具调用仍有记录 |

> **验收判定**：S-01/S-02 为前置基础；S-03、S-04 是缺陷的核心场景（当前 FAIL）；S-05~S-08 为停止后状态收敛回归。实现 Stop 功能时以 S-03/S-04 的「通过标准」为验收目标。

---

## 关键注意事项

1. **pipeline_id 是贯穿全流程的主键**，`start` 的输出必须保存供后续每个步骤复用。
2. **TDD 联动依赖 `tdd link_pipeline`**，否则 `mark_refactor` 只结束 TDD 循环、不会自动推进流水线。
3. **工具是关键词驱动的**（仅文本通道）：所有用例输入需含触发关键词（pipeline / spec / tdd / change / generate plan），否则工具不被选中、无输出。**面板按钮不受此限制**（硬调度）。
4. **触发词面向"生成"动作**：`generate_plan` 需含 `generate plan` / `create plan` / `write plan` 等词。单纯的"列出实施计划"这类**列出**请求不会命中，模型只会给出自由文本（可借助核心工具 `list_files`），**无固定格式、不可作为回归断言**。若要走工具列出，需使用含 `generate plan` 的说法。
5. **`complete_tdd` 需要 `step_index` 等于当前 `currentStepIndex`**，否则返回 Error（`completeStepTdd` 校验）。
6. **非法阶段迁移会被拒绝**：例如在 SPEC 阶段直接 `complete_tdd` 会因 currentStepIndex 不匹配而返回 Error。
7. **面板是快照驱动**：文本通道对流水线的任何变更，需在面板点 `Refresh` 才可见；面板操作会自动刷新列表并默认选中第一条。
8. **面板手填类操作（Start 需步骤已存在、Add Steps、Link Change、Create Change）**：面板一律不提供输入框，命中需手填参数的按钮时给出"走文本通道"提示——这是设计预期，不是缺陷。

---

## 完整示例：新功能开发全流程测试（以新增 Agent 工具 `export_plan` 为例）

> 完整示例（REG-0 ~ REG-19，含需求说明、分步实现、逐条输入与预期输出、回归清单）已迁移至 **`docs/USAGE.md` 第 7 节**，此处不再重复。执行时请参考该节，并按上面的 P/E2E 用例编号填入"验证结果记录"。

---

## 验证结果记录（勾选）

> 复制此表到验证过程，逐条标注：PASS（通过）/ FAIL（失败）/ N/A（不适用）+ 备注。

| 用例 | 结果 | 备注 |
|------|------|------|
| P-UI0 面板加载 | | |
| P-UI1 列表显示实例内容 | | |
| P-UI2 选中实例→摘要刷新 | | |
| P-UI3 选中后点按钮执行 | | |
| P-UI4 多实例区分选中 | | |
| P-UI5 按钮可用性对照表 | | |
| P-UI6 Start TDD 自动取 step | | |
| P-UI7 Complete TDD 需证据 | | |
| P-UI8 Complete→DONE | | |
| P-UI9 手填按钮走文本通道 | | |
| P-UI10 双通道 trace | | |
| P-UI11 Refresh 同步 | | |
| P-UI12 点按钮后自动刷新+选中重置 | | |
| P-UI13 长需求截断与 no-spec | | |
| P-UI14 空状态与输出区格式 | | |
| P-UI15 List 按钮 | | |
| P-UI16 Status 按钮明细 | | |
| P-UI17 Export Plan 按钮 | | |
| P-UI18 面板/文本输出一致性 | | |
| P-UI19 面板 ui 通道 trace | | |
| P-01 启动流水线 | | |
| P-02 缺 requirement | | |
| P-03 状态查询(SPEC) | | |
| P-04 添加计划步骤 | | |
| P-05 缺步骤推进 | | |
| P-06 生成计划 | | |
| P-07 推进到 PLAN | | |
| P-08b~g TDD 内部循环 | | |
| P-09 单步完成→CHANGE | | |
| P-10 多步中间→仍 TDD | | |
| P-11 显式推进第 2 步 | | |
| P-12 link_change→DONE | | |
| P-13 complete 收尾 | | |
| P-14 列表排除 DONE | | |
| P-15 全程状态明细 | | |
| P-T01 start 分支(无domain/已存在/note_zh) | | |
| P-T02 status 不带 id | | |
| P-T03 next_step | | |
| P-T04 非法阶段推进/未知实例 | | |
| P-T05 step_index 越界 | | |
| P-T06 complete_tdd 步号不匹配/export 指定文件 | | |
| P-T07 未知 action | | |
| E2E-01 单步骤冒烟 | | |
| E2E-02 多步骤冒烟 | | |
| E2E-03 混合通道全流程 | | |
| E2E-04 面板防御 | | |
| S-01 初始状态 | | |
| S-02 发送后进入生成态 | | |
| S-03 流式输出中途 Stop | | |
| S-04 Agent 应答中途 Stop | | |
| S-05 停止后立即再发 | | |
| S-06 Stop 幂等 | | |
| S-07 非流式 Stop | | |
| S-08 停止后会话持久化 | | |

（请按需复制多行，对应上面各用例编号填写。完整示例的 REG-0~REG-19 见 `docs/USAGE.md` 第 7 节。）
