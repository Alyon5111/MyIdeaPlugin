# Agent Memory Implementation Plan (2026-08-27)

Status: COMPLETED

## Objective

Implement agent memory for the MyIdeaPlugin agent, providing three memory types
(project knowledge, conversation semantic, tool management), persisted to JSON,
with a combination of system-message injection and external tool access.

## Design Decisions

- Storage: JSON file at `.idea/myplugin-memory.json` (consistent with existing
  `ConversationStorageService`).
- Injection: Both System Message injection (small, important memories) and
  external memory tools (full access).
- Extraction: LLM auto-extraction from conversation with rule-based fallback.
- Testability: `AgentMemoryService` persistence is pluggable via `Persistence`
  interface, enabling in-memory unit tests without a real Project.

## Components

### Base
- `MemoryCategory.java` - enum (FACT, CONVENTION, DECISION, PREFERENCE, CONTEXT,
  TECH_STACK, TOOL_STATE)
- `MemoryEntry.java` - memory entry model (content, category, source, confidence,
  tags, timestamps, access count, importance/recency scoring)
- `MemoryStorageService.java` - JSON persistence to `.idea/myplugin-memory.json`
- `AgentMemoryService.java` - core CRUD + retrieval + pruning + system injection
  formatting; pluggable `Persistence`.

### Project Knowledge (systemmemory)
- `ProjectKnowledgeMemory.java` - typed access to tech stack / conventions /
  decisions / preferences / facts.
- `ProjectKnowledgeTool.java` - Agent tool: query, record_*, get_all.

### Conversation Semantic (semanticmemory)
- `ConversationMemoryExtractor.java` - LLM extraction with `TYPE|content` parsing
  and rule-based keyword fallback.
- `ConversationSemanticMemory.java` - extract-and-store + search + record.
- `ConversationMemoryTool.java` - Agent tool: search, remember_*.

### Tool Management (toolmemory)
- `ToolMemoryManager.java` - tool state memory + runtime map.
- `MemoryStatusTool.java` - Agent tool: summary, list, tool_states, prune.

## Agent Integrity

- Registered 3 new tools in `AgentExecutor` (project_knowledge, conversation_memory,
  memory_status) + TOOL_KEYWORDS triggers.
- System message injection: `formatForSystemInjection(...)` when memory enabled.
- ChatPanel: extraction hook runs after agent response completes.

## Settings (PluginStateService + Constant)

- memoryEnabled (default true)
- memoryExtractionEnabled (default true)
- memoryMaxInjection (default 5)
- memoryAutoPrune (default true)
- memoryMaxEntries (default 500)

## Tests

- AgentMemoryServiceTest
- ConversationSemanticMemoryTest
- ProjectKnowledgeToolTest
- ConversationMemoryToolTest
- MemoryStatusToolTest
- ToolMemoryManagerTest

## Result

All tests pass (271 total, no regression).
