# Agent System

## Purpose

Provide an AI agent with tool-calling capabilities that can autonomously execute tasks using a set of registered tools, with dynamic tool selection and role-based behavior.

## Requirements

### Requirement: Tool Registration

The system SHALL register all available tools and expose their specifications to the LLM.

#### Scenario: Tool initialization

- WHEN agent executor is created
- THEN all tools are registered with name, description, and JSON schema

#### Scenario: Tool specification

- WHEN agent sends request to LLM
- THEN tool specifications are included in the request

### Requirement: Dynamic Tool Selection

The system SHALL select relevant tools based on user message keywords to reduce context size.

#### Scenario: Keyword matching

- WHEN user message contains keywords like "change" or "spec"
- THEN corresponding SDD tools are included in active tool set

#### Scenario: Core tools always included

- WHEN any user message is processed
- THEN core tools (read_file, list_files, search_code, execute_command) are always selected

#### Scenario: Insufficient tools

- WHEN selected tools count is too low
- THEN write_file and edit_file are automatically added

### Requirement: Tool Execution Loop

The system SHALL execute a tool-calling loop where the LLM requests tool calls and receives results.

#### Scenario: Single tool call

- WHEN LLM requests one tool call
- THEN system executes the tool and returns result to LLM

#### Scenario: Multiple tool calls

- WHEN LLM requests multiple tool calls in one response
- THEN system executes all tools and returns combined results

#### Scenario: Tool not found

- WHEN LLM requests a tool that doesn't exist
- THEN system returns error message indicating tool not found

### Requirement: Iteration Limit

The system SHALL limit the number of agent iterations to prevent infinite loops.

#### Scenario: Maximum iterations reached

- WHEN agent exceeds 15 iterations
- THEN system forces final answer from LLM

#### Scenario: Total tool call limit

- WHEN agent exceeds 8 total tool calls
- THEN system requests final answer immediately

### Requirement: Repeated Call Detection

The system SHALL detect and prevent repeated identical tool calls.

#### Scenario: Duplicate detection

- WHEN LLM requests the same tool with same arguments 2+ times
- THEN system forces STOP and requests final answer

### Requirement: Role Detection

The system SHALL detect user intent and apply role-specific system prompts.

#### Scenario: Review role

- WHEN user message contains "review", "check", "analyze"
- THEN system uses CODE_REVIEWER role prompt

#### Scenario: Architect role

- WHEN user message contains "design", "architecture", "plan"
- THEN system uses ARCHITECT role prompt

#### Scenario: Default developer role

- WHEN no specific role keywords detected
- THEN system uses DEVELOPER role prompt

### Requirement: Cancellation

The system SHALL support cancellation of running agent tasks.

#### Scenario: User cancels

- WHEN user clicks stop button during agent execution
- THEN agent loop terminates and partial results are preserved

## Implementation Notes

- Max iterations: 15
- Max total tool calls: 8
- Max history messages: 20 (to prevent context overflow)
- Model: OpenAiChatModel with configurable base URL
- Temperature: 0.3, TopP: 0.9
