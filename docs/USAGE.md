# MyIdeaPlugin 使用说明（Usage Guide）

> MyIdeaPlugin 是一个 IntelliJ 插件：在 IntelliJ 中内置一个**本地 LLM 编程助手**（默认对接 Llama.cpp 的 OpenAI 兼容接口），提供聊天、工具调用、情境记忆、以及一条完整的 **SDD（Spec-Driven Development）→ Plan → TDD → Change** 开发流水线。
>
> 本文档是完整使用说明，末尾附一个**新功能开发全流程完整示例**（新增 Agent 工具 `export_plan`），逐条列明输入、预期输出与下一步衔接，可直接照着操作。

---

## 目录

1. [项目结构速览](#1-项目结构速览)
2. [快速开始](#2-快速开始)
3. [设置项详解](#3-设置项详解)
4. [工作原理与工具调用机制](#4-工作原理与工具调用机制)
5. [工具清单与触发关键词](#5-工具清单与触发关键词)
6. [核心流程：SDD → Plan → TDD → Change](#6-核心流程sdd--plan--tdd--change)
7. [完整示例：新增 Agent 工具 export_plan](#7-完整示例新增-agent-工具-export_plan)
8. [关键注意事项](#8-关键注意事项)

---

## 1. 项目结构速览

| 路径 | 说明 |
|------|------|
| `src/main/java/com/example/myplugin/ui/` | 聊天界面（ToolWindow、标签页、流式输出） |
| `src/main/java/com/example/myplugin/agent/AgentExecutor.java` | Agent 核心：模型调度、工具选择、上下文管理 |
| `src/main/java/com/example/myplugin/agent/` | 各 Agent 工具实现（按子包归类） |
| `src/main/java/com/example/myplugin/settings/` | 设置持久化（`MyPluginSettings.xml`）与设置界面 |
| `src/main/java/com/example/myplugin/openspec/` | OpenSpec 规范管理（specs / changes） |
| `src/main/java/com/example/myplugin/model/Constant.java` | 全部默认参数 |
| `docs/plans/` | 实施计划存放目录（`generate_plan` 写入） |
| `openspec/specs/` | OpenSpec 规格定义 |
| `openspec/changes/` | OpenSpec 变更（change）目录 |

---

## 2. 快速开始

1. **启动插件**：在 IntelliJ 中运行 Gradle 任务 `runIde`（或直接运行插件调试配置），IDE 会带插件启动。
2. **启动本地 LLM 服务**：确保 Llama.cpp server 已运行，默认地址 `http://localhost:8080`（需 OpenAI 兼容的 `/v1` 端点）。
3. **打开助手**：IDE 右侧 `My Plugin` ToolWindow（由 `plugin.xml` 注册）。
4. **新建/选择会话标签**：在会话中直接以自然语言提问或发指令即可。
5. **配置连接**（如非默认地址）：`Settings → My Plugin Settings`，见下一节。

> 首次运行连接失败会给出友好错误（如 `AgentErrorMessages.connectionError()`）。LLM 不可用时，Agent 无法运转，`runIde` 启动日志中的协程 dump 属于 IntelliJ 正常启动噪音，与插件无关。

---

## 3. 设置项详解

设置由 `PluginStateService` 持久化到项目级 `MyPluginSettings.xml`，界面入口为 `SettingsComponent` / `SettingsConfigurable`（名称 "My Plugin Settings"）。默认值定义在 `model/Constant.java`。

### 3.1 模型连接参数

| 设置项 | 默认值 | 说明 |
|--------|--------|------|
| **LlamaCpp URL** | `http://localhost:8080` | 本地 LLM 服务地址；`AgentExecutor.createModel()` 会自动补全 `/v1` 后缀 |
| **API Key** | 空串（实际传 `"no-key"`） | 本地服务通常随意填 |
| **Temperature** | `0.7` | 采样温度 |
| **Top P** | `0.9` | 核采样 |
| **Max Tokens** | `4000` | 最大输出 token |
| **Max Retries** | `1` | 失败重试次数 |
| **Timeout (秒)** | `300` | 请求超时 |

### 3.2 Agent 行为参数

| 设置项 | 默认值 | 说明 |
|--------|--------|------|
| **Max Iterations**（`agentMaxIterations`） | `15` | Agent 最大思考-工具循环轮数 |
| **Max Tool Calls**（`agentMaxToolCalls`） | `8` | 单轮会话累计工具调用上限，超过强制要求模型直接给出最终答案 |
| **Max History**（`agentMaxHistory`） | `20` | 送入模型的历史消息条数上限 |
| **Max Context Messages**（`agentMaxContextMessages`） | `6` | **滑动窗口大小（上下文溢出防护）**：`trimContextWindow()` 只保留系统消息 + 最近 N-1 条消息 |

### 3.3 流式与记忆

| 设置项 | 默认值 | 说明 |
|--------|--------|------|
| **Stream Mode**（`streamMode`） | `true` | 流式输出 |
| **Memory Enabled**（`memoryEnabled`） | `true` | 是否启用情境记忆注入 |
| **Memory Extraction**（`memoryExtractionEnabled`） | `true` | 会话结束后自动抽取记忆 |
| **Max Injection**（`memoryMaxInjection`） | `5` | 系统提示中最多注入多少条记忆 |
| **Auto Prune**（`memoryAutoPrune`） | `true` | 超限自动修剪 |
| **Max Entries**（`memoryMaxEntries`） | `500` | 记忆最大条数 |

> **上下文溢出防护原理**（`AgentExecutor`）：
> - 每个工具结果超过 `4000` 字符会被截断（`truncateToolResult`）。
> - 每轮迭代前调用 `trimContextWindow()` 收缩滑动窗口。
> - 若模型返回含 `exceed_context_size_error` / `context window` / `context length` / `too many tokens` / `tokens exceeds` 等错误，统一输出 `AgentErrorMessages.contextLimitError()` 的友好提示 + 原始错误。

---

## 4. 工作原理与工具调用机制

### 4.1 工具选择是关键词驱动的

插件**没有** `/toolname action=...` 命令路由。真实机制：

1. `AgentExecutor.selectToolsForMessage(lastUserMsg)` 把用户消息转小写，遍历 `TOOL_KEYWORDS`，消息**包含某关键词**就把对应工具加入候选集（`AgentExecutor.java:239`）。
2. **核心工具始终可用**：`read_file`、`list_files`、`search_code`、`execute_command`（`CORE_TOOLS`）。
3. **兜底**：若候选集 ≤ 核心+null（≤ 核心 2 个），额外补 `write_file`、`edit_file`。
4. **复合命中**：
   - 消息含 `change` 且不含 `diff`/`git` → 追加 `read_change`、`create_change`、`list_changes`、`archive_change`。
   - 消息含 `spec` 或 `openspec` → 追加 `sdd_status`、`list_specs`、`read_spec`、`list_changes`。
5. 工具的具体 `action` 与参数由 **LLM 从自然语言推断**填成 JSON。

> 结论：消息**必须包含触发关键词**，对应工具才会被选中；否则只返回普通文本回答，无工具输出。

### 4.2 角色识别

`detectRole()` 依据 `ROLE_KEYWORDS` 把用户消息归类，注入不同系统提示：

| 角色 | 关键词（部分） |
|------|----------------|
| `review` | review / audit / code review / cr / pr review |
| `architect` | architecture / design / structure / coupling / refactor |
| `test` | test coverage / unit test / integration test / mock / coverage |
| `dev`（默认） | 其余 |

### 4.3 上下文管理与防护

- 历史记录超 `agentMaxHistory` 时只取最近 N 条。
- 工具结果超 4000 字符截断。
- 滑动窗口按 `agentMaxContextMessages` 收缩。
- 相同工具+参数调用 3 次 → 强制最终答案；累计工具调用超 `agentMaxToolCalls` → 强制最终答案。
- 场景：`ChatPanel` 通过 `AgentEvent` 回调推送 onThinking / onToolCall / onToolResult / onAnswer / onError。

---

## 5. 工具清单与触发关键词

> 以下完整转录自 `AgentExecutor.java` 的 `tools` 列表与 `TOOL_KEYWORDS`。随新工具注册需同步扩展。

### 5.1 基础代码工具（部分含在 CORE_TOOLS，始终可选）

| 工具 | 触发关键词 |
|------|-----------|
| `read_file`（核心） | - |
| `list_files`（核心） | - |
| `search_code`（核心） | - |
| `execute_command`（核心） | - |
| `write_file` | write / create file / new file / create |
| `edit_file` | edit / modify / change file / update file / replace |
| `find_classes` | class / interface / enum / record / find class |
| `find_references` | reference / usage / who uses / who calls |
| `get_file_structure` | structure / outline / methods / fields |
| `goto_definition` | definition / where is defined / jump to |
| `get_diagnostics` | diagnostic / error / warning / compile error |
| `check_inspections` | inspection / inspect / lint / code review |
| `run_tests` | test / junit / run test |
| `reformat_code` | format / reformat / pretty print |
| `run_gradle_task` | gradle / maven / build / compile project |
| `git_diff` | diff / change / what changed / modified |
| `git_log` | log / commit / history / recent commits |
| `get_project_structure` | project structure / module / source root |

### 5.2 OpenSpec 工具

| 工具 | 触发关键词 |
|------|-----------|
| `read_spec` | spec / specification / read spec |
| `read_change` | read change / proposal / tasks |
| `list_specs` | list spec / all spec / show spec |
| `list_changes` | list change / active change / openspec |
| `create_change` | new change / create change / start change / propose |
| `archive_change` | archive / finish change / complete change / merge spec |
| `sdd_status` | sdd / sdd status / openspec status |

### 5.3 开发流程工具

| 工具 | 触发关键词 |
|------|-----------|
| `brainstorm` | brainstorm / design / plan feature / architect / design decision / think about |
| `generate_plan` | generate plan / create plan / implementation plan / write plan / task list / step by step |
| `tdd_enforcer` | tdd / test driven / red green / write test first / failing test |
| `sdd_pipeline` | spec pipeline / sdd pipeline / pipeline / advance tdd / next stage |
| `systematic_debugging` | debug / debugging / bug / root cause / fix bug |
| `verification_gate` | verify / verification / validate / evidence / confirm |
| `code_review` | review code / code review / cr / audit / yagni |
| `executing_plans` | execute plan / run plan / implement plan |
| `subagent_driven_development` | subagent / dispatch agent / parallel task |
| `git_workflow` | worktree / git worktree / branch management / git branch |
| `finishing_branch` | finish branch / complete branch / merge branch / create pr / pull request |

### 5.4 记忆工具

| 工具 | 触发关键词 |
|------|-----------|
| `project_knowledge` | project knowledge / tech stack / convention / remember / recall / previously |
| `conversation_memory` | remember / recall / previous session / semantic |
| `memory_status` | memory status / memory summary / memory list / memories |

---

## 6. 核心流程：SDD → Plan → TDD → Change

由两个协同工具驱动：

- **`sdd_pipeline`**（`SDDPipelineTool` + `PipelineService`）：管理流水线实例与阶段流转，状态持久化到磁盘。
- **`tdd_enforcer`**（`TddEnforcerTool` + `TddStateMachine`）：管理每个计划步骤的 TDD 状态机；**`link_pipeline` 后可自动推进流水线**。

### 6.1 阶段总览

```
SPEC → PLAN → TDD → CHANGE → DONE
```

- 一次 PLAN 步骤 = 一个 TDD 循环（RED→RED_VERIFY→GREEN→GREEN_VERIFY→REFACTOR→DONE）。
- `pipeline_id` 是贯穿全流程的主键，`start` 的输出必须保存。

### 6.2 `sdd_pipeline` 的 action 一览

| action | 必填参数 | 行为 / 预期返回 |
|--------|----------|-----------------|
| `start` | `requirement`（空则报错，不创建实例） | `=== SPEC PIPELINE STARTED ===` + `Pipeline ID: <uuid>` + `Stage: SPEC` |
| `add_step` | `pipeline_id`、`plan_steps`（按换行拆分，空行忽略） | `Added N plan step(s)... Next: call advance_plan` |
| `advance_plan` | `pipeline_id`，可选 `plan_file` | 仅 SPEC 可进；无 steps 时返回 `Add plan steps first...`；成功返回 `=== STAGE: PLAN ===` + `Steps: N` |
| `start_tdd` | `pipeline_id`、`step_index`（0 起） | 仅 PLAN/TDD 可进；返回 `=== STAGE: TDD (step k/N) ===` + `Task: ...` |
| `complete_tdd` | `pipeline_id`、`step_index` | `step_index` 必须等于当前 `currentStepIndex`，否则 Error |
| `link_change` | `pipeline_id`、`change_name` | 若当前在 CHANGE → 自动 advance 到 DONE |
| `complete` | `pipeline_id` | 显式标记 DONE |
| `status` | `pipeline_id` | `formatDetail()`：Stage/Requirement/Plan/Plan steps(marker)/Current step/Change/Stage Log |
| `list` | - | `formatSummary()`：列出非 DONE 实例 |
| `next_step` | `pipeline_id` | 按当前阶段提示下一步动作 |

### 6.3 `tdd_enforcer` 的 action 一览

| action | 说明 | 状态迁移 |
|--------|------|----------|
| `start_cycle`（参数 `task`） | 开启新循环；循环进行中会报错 | → RED |
| `write_test` | 仅 RED 可调 | RED → RED_VERIFY |
| `verify_test`（参数 `test_output`，必填） | 测试按预期失败才进 GREEN | RED_VERIFY → GREEN |
| `write_code` | 仅 GREEN 可调 | GREEN → GREEN_VERIFY |
| `verify_code`（参数 `test_output`，必填） | 测试通过进 REFACTOR 并附重构建议 | GREEN_VERIFY → REFACTOR |
| `mark_refactor` | 仅 REFACTOR 可调；**若已 `link_pipeline` 则自动 `completeStepTdd` 并推进流水线** | REFACTOR → DONE |
| `link_pipeline`（参数 `pipeline_id`） | 将 TDD 循环绑定到流水线，`mark_refactor` 时联动 | - |
| `get_status` | 查看状态机、历史、红旗 | - |
| `cancel` | 重置为 IDLE | 任意 → IDLE |
| `check_rationalization`（参数 `message`） | 检测为跳过 TDD 准备的合理化借口 | - |

> **务必学会的行为**：`mark_refactor` 时若已 link 且全部步骤完成 → 返回 `ALL plan steps done -> CHANGE.`（流水线自动进 CHANGE）；若还有下一步 → 返回 `step N TDD done.` 并提示 `start_tdd` 下一步。

### 6.4 生成计划（`generate_plan`）

| action | 必填 | 说明 |
|--------|------|------|
| `generate` | `source`（文本或设计文档路径） | 生成 TDD 分步计划并保存到 `docs/plans/<slug>-plan.md`，返回 `=== Plan Generated ===` + `File:` + 校验信息 + Preview |
| `list`（默认） | - | 列出 `docs/plans` 下全部计划及进度；空则 `No plans found...` |
| `validate` | `plan_file` | 校验计划结构，返回 VALID/INVALID + Errors/Warnings |
| `get_status` | `plan_file` | 任务数 / 完成步骤 / 进度 |
| `update_step` | `plan_file`、`task_index`、`step_index`、`completed` | 勾选/取消计划中的步骤 |

---

## 7. 完整示例：新增 Agent 工具 export_plan

> 本节用项目**真实的新功能**走完 **SDD → Plan → TDD → Change** 全流程，作为可逐条执行、可回归的端到端使用/测试基准。
> 示例项目 = 本项目（MyIdeaPlugin，IntelliJ 本地 LLM 插件）。

### 7.1 新功能需求说明：`export_plan`（导出计划为 Markdown）

- **作用**：把 `docs/plans/` 下的某个实施计划（`PlanStore` 管理的 `.md` 计划）读取后，生成一份排版更佳的 Markdown 导出文件。
- **贴合点**：完全沿用项目现有模式 —— 实现 `AgentTool` 接口（`name()/description()/specification()/execute()`），复用 `PlanStore`（`agent/plan/PlanStore.java`）的 `readPlan()`/`getPlansDir()`，参考 `GeneratePlanTool` 的 action 分发风格。
- **建议实现分解**（供流水线规划引用）：
  - **step 0**：新增 `ExportPlanTool implements AgentTool`，实现「读取计划 + 校验文件存在 + 返回计划内容」（action=export）。
  - **step 1**：实现「生成 Markdown 导出文件」（写入 `docs/plans/export/`）并返回文件路径。
- **完成后注册**：在 `AgentExecutor` 构造器 `tools = List.of(...)` 中加 `new ExportPlanTool(ctx)`，并在 `TOOL_KEYWORDS` 增加关键词：`"export plan"`、`"导出计划"`。

### 7.2 前置准备

1. `runIde` 启动插件。
2. 确认 LLM 服务已配置并连通（设置项 LlamaCpp URL，默认 `http://localhost:8080`）。
3. 确保项目干净：无残留的 `docs/plans/`、`openspec/changes/`、`docs/plans/export/` 产物（至少已知其现状）。

---

### 阶段零：前置检查

> **修正说明**：`generate_plan` 的触发关键词面向"生成"动作（`generate plan` / `create plan` / `write plan` / `step by step`）。"列出实施计划"这类**列出**请求不命中任何关键词，工具不会**被选中**，模型只能给出自由文本（可借助核心工具 `list_files` 或凭模型知识），**无固定格式、不可作为回归断言**。因此本用例只确认环境，不断言工具输出。

#### REG-0 环境就绪（确认 docs/plans 下存在既有计划，作为 export_plan 的数据源）

| 步骤 | 具体预期 |
|------|----------|
| 在 Agent 会话发送：`列出当前项目里有哪些已实施的计划`（自然语言提问） | 输出应为描述 docs/plans 下计划的自由文本；**不得断言出现 `=== Implementation Plans ===`**（该请求不保证命中 generate_plan） |
| 人工/旁路确认 `docs/plans/` 目录 | 至少存在一份 `.md` 计划文件，可作为 export_plan 的读取目标（后续 REG-6 也会生成一份） |

---

### 阶段一：SPEC（需求 → 流水线实例）

#### REG-1 启动流水线（新增 export_plan 工具）

| 测试步骤 / 输入 | 具体预期 | 输出 → 下一流程 |
|------------------|----------|-----------------|
| 发送：`pipeline start requirement="新增 Agent 工具 export_plan：读取 docs/plans 下计划并导出为 Markdown 到 docs/plans/export" spec_domain=agent-tools` | 返回 `=== SPEC PIPELINE STARTED ===`，含 `Pipeline ID: <uuid>`、`Requirement: 新增 Agent 工具 export_plan...`、`Stage: SPEC`、`Spec doc: openspec/specs/agent-tools/spec.md`，末尾提示保存 pipeline_id | **记录 `Pipeline ID`，记为 `<PID>`**。后续所有步骤都以 `<PID>` 为必需参数 |
| 旁路确认 ①：`.idea/myplugin-pipeline.json` | 存在以 `<PID>` 为 key 的实例记录，`stage=SPEC`（此刻原始工具输出的 `=== SPEC PIPELINE STARTED ===` 必须出现在 `.idea/myplugin-conversations.json` 的 ASSISTANT thinking 字段中——LLM 会包装工具输出，**最终气泡文本不作文本断言**） | |
| 旁路确认 ②：`openspec/specs/agent-tools/spec.md` | 文件已生成，能被 `list_specs` 解析（含 `## Purpose` 与 `### Requirement: 新增 Agent 工具 export_plan...`），这是本阶段**真正落盘的 SDD 产物** | 之后 REG-13 的 `create_change` 即在此 spec 之上产出 Change |

> 判定原则：SPEC 阶段的"预期"以**磁盘上的持久化产物**为准——① pipeline 实例落盘、② spec 文档落盘。聊天气泡里模型怎么复述/包装工具输出不作严谨断言（详见第 7 节"已知缺陷"）。

#### REG-2 缺 requirement 防御

| 测试步骤 / 输入 | 具体预期 |
|------------------|----------|
| 发送：`pipeline start 开始一个流水线`（消息里不携带 requirement 内容） | 返回 `Error: 'requirement' is required to start a pipeline`，且 `pipeline list` 中不会新增该实例 |

#### REG-3 状态确认（SPEC 期）

| 测试步骤 / 输入 | 具体预期 | 输出 → 下一流程 |
|------------------|----------|-----------------|
| 发送：`pipeline status pipeline_id=<PID>` | 返回 `Stage: SPEC`、`Plan steps (0):`（空）、Stage Log 首行 `CREATED at SPEC stage...`，且**无** `Current step` 行（`currentStepIndex=-1`） | 确认已建实例、尚无步骤 → 下一步 `add_step` |

---

### 阶段二：PLAN（计划步骤 → 进入 PLAN）

#### REG-4 分两步规划 export_plan

| 测试步骤 / 输入 | 具体预期 | 输出 → 下一流程 |
|------------------|----------|-----------------|
| 发送：`pipeline add_step pipeline_id=<PID> plan_steps="1. 新增 ExportPlanTool：读取计划并校验文件存在\n2. 实现生成 Markdown 导出文件并返回路径"` | 返回 `Added 2 plan step(s) to pipeline <PID>.` + `Next: call advance_plan` | 实例中已有 2 步；step_index：0=读取+校验，1=导出 Markdown |

#### REG-5 未加步骤前不能推进（防御）

| 测试步骤 / 输入 | 具体预期 |
|------------------|----------|
| 发送：`pipeline advance_plan pipeline_id=<PID>`（在 REG-4 之前执行） | 返回 `Add plan steps first (action=add_step) or you can pass plan_steps with advance_plan.`，阶段停留 SPEC |

#### REG-6 生成实施计划文档

> 触发引擎已加"硬调度"：消息含 `generate plan` 等强绑定词时，候选工具集被收窄为核心工具 + `generate_plan`，模型不会漂移到 read_file 等探索工具（修复 qwen-3-27b 在 REG-6 上"选中了 generate_plan 却去读文件"的问题）。

| 测试步骤 / 输入 | 具体预期 | 输出 → 下一流程 |
|------------------|----------|-----------------|
| 发送：`generate plan source="新增 Agent 工具 export_plan，读取 docs/plans 计划并导出 Markdown 到 docs/plans/export"` | `[Tool Call] generate_plan` 必须出现（thinking 字段可查）；返回 `=== Plan Generated ===`、`File: docs/plans/<slug>-plan.md`，含 Validation 信息与 Plan Preview（TDD 分步） | **记录计划文件名 `<PLAN_FILE>`**，作为 REG-7 的 `plan_file` 参数，且是 export_plan 之后要读取/导出的数据源 |

> 即使模型本次（另一次尝试中）不调用 `generate_plan`，REG-7 的 `advance_plan` 也会在缺 `plan_file` 时**自动生成**计划文档，PLAN 产物不会缺失。

#### REG-7 推进到 PLAN

| 测试步骤 / 输入 | 具体预期 | 输出 → 下一流程 |
|------------------|----------|-----------------|
| 发送：`pipeline advance_plan pipeline_id=<PID> plan_file=<PLAN_FILE>` | 返回 `=== STAGE: PLAN ===`、`Plan file: <PLAN_FILE>`、`Steps: 2`、`Next: call start_tdd with step_index=0` | 阶段推进到 PLAN；`status` 中 `Plan:` 字段已填 → 下一步 `start_tdd(0)` |
| 变体：`pipeline advance_plan pipeline_id=<PID>`（不带 plan_file） | `Plan file:` 显示自动生成的文件名（`docs/plans/<date>-<slug>-plan.md` 已写入磁盘——`advance_plan` 的兜底行为） | 同样进入 PLAN，效果等价 |

---

### 阶段三：TDD（每步骤一个 TDD 循环）

#### REG-8 关联 TDD 与流水线（联动前提）

| 测试步骤 / 输入 | 具体预期 | 输出 → 下一流程 |
|------------------|----------|-----------------|
| 发送：`启动 TDD 循环并关联到流水线 pipeline_id=<PID>`（中文“关联到流水线/启动 tdd/tdd 循环”命中 `tdd_enforcer` 强绑定，候选仅该工具；或英文 `tdd_enforcer link_pipeline pipeline_id=<PID>`） | `[Tool Call] tdd_enforcer` 必须出现（thinking 中 `Selected tools (1): tdd_enforcer`）；`start_cycle` 带 `pipeline_id` 或 `link_pipeline` action 二者任一即建立联动；返回 `=== TDD Cycle Started ===` 或 `Linked TDD cycle to spec pipeline <PID>.` | `mark_refactor` 时才能自动推进流水线 |

#### REG-9 开始第 1 个 TDD 循环（步骤0：读取+校验）

| 测试步骤 / 输入 | 具体预期 | 输出 → 下一流程 |
|------------------|----------|-----------------|
| 发送：`pipeline start_tdd pipeline_id=<PID> step_index=0` | 返回 `=== STAGE: TDD (step 1/2) ===`、`Task: 新增 ExportPlanTool：读取计划并校验文件存在`、提示运行 `tdd_enforcer start_cycle` | `currentStepIndex=0`；用 tdd 工具走 RED→GREEN→REFACTOR |

#### REG-10 TDD 循环 body：RED → REFACTOR（步骤0）

| 用例 | 测试步骤 / 输入 | 具体预期 | 输出 → 下一流程 |
|------|------------------|----------|-----------------|
| REG-10a | `tdd start_cycle task="ExportPlanTool 读取计划并校验"` | `=== TDD Cycle Started ===`、`State: RED`；提示先写失败测试 | state=RED → write_test |
| REG-10b | `tdd write_test`（编写会失败的测试：先测「文件不存在时返回错误」） | state=RED_VERIFY；提示运行测试确认失败 | state=RED_VERIFY → verify_test |
| REG-10c | `tdd verify_test test_output="BUILD FAILED\n1 test failed: expected not-found error but got NPE"` | 测试按预期失败 → state=GREEN | state=GREEN → write_code |
| REG-10d | `tdd write_code`（实现 `ExportPlanTool` 骨架 + `readPlan` 空值校验，复用 `PlanStore`） | state=GREEN_VERIFY | state=GREEN_VERIFY → verify_code |
| REG-10e | `tdd verify_code test_output="BUILD SUCCESSFUL\n3 tests passed"` | state=REFACTOR，附重构提示 | state=REFACTOR → mark_refactor |
| REG-10f | `tdd mark_refactor` | `=== TDD Cycle Complete ===` + 因 REG-8 已联动，返回 `Spec pipeline <PID>: step 1 TDD done.` + `Next: start the next plan step's TDD cycle` | 流水线**仍停留 TDD**（共 2 步未完成）→ 下一步 REG-11 |

#### REG-11 开始第 2 个 TDD 循环（步骤1：导出 Markdown）

| 测试步骤 / 输入 | 具体预期 | 输出 → 下一流程 |
|------------------|----------|-----------------|
| 发送：`pipeline start_tdd pipeline_id=<PID> step_index=1` | 返回 `=== STAGE: TDD (step 2/2) ===`、`Task: 实现生成 Markdown 导出文件并返回路径` | `currentStepIndex=1` |

#### REG-12 TDD 循环 body：步骤1（同 REG-10）

| 用例 | 测试步骤 / 输入 | 具体预期 | 输出 → 下一流程 |
|------|------------------|----------|-----------------|
| REG-12a~e | 依次执行 `tdd start_cycle task="导出 Markdown"` → `write_test` → `verify_test(FAIL)` → `write_code`（实现写入 `docs/plans/export/`） → `verify_code(PASS)` | 状态依序 RED→RED_VERIFY→GREEN→GREEN_VERIFY→REFACTOR，全程无 Error | state=REFACTOR → mark_refactor |
| REG-12f | `tdd mark_refactor` | `=== TDD Cycle Complete ===` + 全部步骤完成 → `Spec pipeline <PID>: ALL plan steps done -> CHANGE.` + `Next: create_change + implement + archive, then pipeline.link_change.` | 流水线**自动推进到 CHANGE** → 下一步做 change |

---

### 阶段四：CHANGE（变更 → 结束）

#### REG-13 创建 OpenSpec 变更

| 测试步骤 / 输入 | 具体预期 | 输出 → 下一流程 |
|------------------|----------|-----------------|
| 发送：`create_change name=add-export-plan-tool` | 返回 `Created change: add-export-plan-tool`，含 `proposal.md`、`tasks.md`、`specs/` 三项说明 | 在 `openspec/changes/add-export-plan-tool/` 生成 scaffold；预设填写 proposal/tasks/specs 内容（记录新增的 ExportPlanTool + 注册到 AgentExecutor）→ 下一步 `link_change` |

#### REG-14 关联变更并完成流水线

| 测试步骤 / 输入 | 具体预期 | 输出 → 下一流程 |
|------------------|----------|-----------------|
| 发送：`pipeline link_change pipeline_id=<PID> change_name=add-export-plan-tool` | 返回 `Linked change 'add-export-plan-tool' to pipeline <PID>. Stage: DONE`（当前为 CHANGE → 自动 advance 到 DONE） | 流水线进入 DONE |

#### REG-15 归档收尾（可选，显式 DONE）

| 测试步骤 / 输入 | 具体预期 | 输出 → 下一流程 |
|------------------|----------|-----------------|
| 发送：`pipeline complete pipeline_id=<PID>` | 返回 `Pipeline <PID> marked DONE (requirement delivered through spec->plan->tdd->change).` | 阶段 DONE，从 active 列表消失 |

---

### 阶段五：收尾验证（回归）

#### REG-16 列表排除 DONE

| 测试步骤 / 输入 | 具体预期 |
|------------------|----------|
| 发送：`pipeline list` | `<PID>` 实例不再出现（`[DONE]` 被 `getActive()` 排除） |

#### REG-17 全程状态明细

| 测试步骤 / 输入 | 具体预期 |
|------------------|----------|
| 发送：`pipeline status pipeline_id=<PID>` | `Stage: DONE`、`Requirement:` 正确、`Plan: <PLAN_FILE>`、`Plan steps (2):` 两行均 `[X]`、`Current step: 2`、`Change: add-export-plan-tool`、Stage Log 含 SPEC→PLAN→TDD→CHANGE / 多个 TDD DONE 记录 |

#### REG-18 新功能成果验证（export_plan 真实可用）

| 测试步骤 / 输入 | 具体预期 |
|------------------|----------|
| 发送：`export plan file=<PLAN_FILE> 导出这个计划` | 触发 `export_plan` → 返回含生成文件路径（如 `docs/plans/export/<name>-export.md`） |
| 用 IDE 打开生成的导出文件（或 `read_file path=docs/plans/export/<name>-export.md`） | 文件存在且内容与 `<PLAN_FILE>` 计划一致、Markdown 排版正确 |
| 发送：`export plan file=不存在的计划.md` | 返回错误提示计划不存在（`Plan '<file>' not found`），不产生文件 |

#### REG-19 完整无误回归清单

> 走完 REG-1 ~ REG-18 后断言：
> - 阶段依序 `SPEC → PLAN → TDD → CHANGE → DONE`，**无任何 Error**。
> - `pipeline_id`、`plan_file`、`step_index`、`change_name` 在各步骤间正确传递。
> - TDD 两步各自 `verify_test(FAIL)` + `verify_code(PASS)` 状态机完整。
> - 新工具 `export_plan` 已在 `AgentExecutor` 注册（`TOOL_KEYWORDS` 命中 `export plan`/`导出计划`），能将 `<PLAN_FILE>` 正确导出为 `docs/plans/export/` 下的 Markdown，且对不存在的计划返回错误。
> - 产物齐全：`docs/plans/<PLAN_FILE>`、`docs/plans/export/`、`openspec/changes/add-export-plan-tool/`。

---

## 8. 关键注意事项

1. **`pipeline_id` 是贯穿全流程的主键**，`start` 的输出必须保存供后续每步复用。
2. **TDD 联动依赖 `tdd link_pipeline`**，否则 `mark_refactor` 只结束 TDD 循环、不会自动推进流水线。
3. **工具是关键词驱动的**：所有用例输入需含触发关键词（`pipeline` / `spec` / `tdd` / `change` / `generate plan` / `export plan` 等），否则工具不被选中、无输出。
4. **`complete_tdd` 需要 `step_index` 等于当前 `currentStepIndex`**，否则返回 Error（`completeStepTdd` 校验）。
5. **非法阶段迁移会被拒绝**：例如 SPEC 阶段直接 `complete_tdd` 会因 currentStepIndex 不匹配返回 Error；`advance_plan` 仅 SPEC 可进，`start_tdd` 仅 PLAN/TDD 可进。
6. **上下文防护是硬约束**：工具结果截断 4000 字符、滑动窗口 `agentMaxContextMessages`（默认 6）——长流程注意上下文被裁剪后的信息丢失。
7. **Brainstorm 模块存在已知缺陷**（当前版本）：无 action 能添加 question/approach（`ASK_QUESTIONS`/`PROPOSE_APPROACHES` 阶段走不通）；会话用单一 `"active"` key，无法并发隔离。使用 `brainstorm` 时留意。
8. **记忆是系统提示注入**：命中记忆阈值才注入，超 `memoryMaxInjection` 条会截断——依赖旧对话结论时建议在消息里带关键词（`remember`/`recall`）。